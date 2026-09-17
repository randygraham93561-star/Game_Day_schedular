package com.sports.gamedayschedular.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.sports.gamedayschedular.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.*

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val gamesCollection = db.collection("games")
    private val assignmentsCollection = db.collection("assignments")
    private val profilesCollection = db.collection("profiles")
    private val organizationsCollection = db.collection("organizations")
    private val seasonsCollection = db.collection("seasons")
    private val printJobsCollection = db.collection("print-jobs")

    fun getOrganizations(): Flow<List<Organization>> =
        organizationsCollection.snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toObject(Organization::class.java)?.copy(id = it.id) }
        }

    fun getPrintJobs(organizationId: String): Flow<List<PrintJob>> {
        Log.d("GDS_PRINTER", "Querying print-jobs for org: $organizationId")
        return printJobsCollection.whereEqualTo("organizationId", organizationId)
            .whereEqualTo("status", "Pending")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(PrintJob::class.java)?.copy(id = it.id) }
            }
    }

    suspend fun updatePrintJobStatus(jobId: String, status: String) {
        printJobsCollection.document(jobId).update("status", status).await()
    }

    suspend fun resetOrganizationStatus(organizationId: String) {
        organizationsCollection.document(organizationId).update(
            mapOf(
                "currentGameDayCode" to "",
                "printerStatus" to "IDLE"
            )
        ).await()
    }

    suspend fun getGameDetails(gameId: String): Game? =
        gamesCollection.document(gameId).get().await().toObject(Game::class.java)?.copy(id = gameId)


    fun getActiveSeason(organizationId: String): Flow<Season?> =
        seasonsCollection.whereEqualTo("organizationId", organizationId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Season::class.java)?.copy(id = it.id) }
                    .firstOrNull { it.active } ?: snapshot.documents.firstOrNull()?.toObject(Season::class.java)?.copy(id = snapshot.documents.first().id)
            }

    fun getGamesForDay(organizationId: String, date: Date, timeZone: TimeZone = TimeZone.getDefault()): Flow<List<Game>> {
        val calendar = Calendar.getInstance(timeZone).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.time

        return gamesCollection
            .whereEqualTo("organizationId", organizationId)
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThan("date", endOfDay)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Game::class.java)?.copy(id = it.id) }
                    .sortedBy { it.date }
            }
    }

    fun getAssignmentsForGame(gameId: String): Flow<List<Assignment>> =
        assignmentsCollection.whereEqualTo("gameId", gameId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Assignment::class.java)?.copy(id = it.id) }
            }

    fun getAssignmentsForOrganization(organizationId: String): Flow<List<Assignment>> =
        assignmentsCollection.whereEqualTo("organizationId", organizationId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Assignment::class.java)?.copy(id = it.id) }
            }

    fun getProfilesForOrganization(organizationId: String): Flow<List<RefereeProfile>> =
        profilesCollection.whereEqualTo("organizationId", organizationId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(RefereeProfile::class.java)?.copy(id = it.id) }
            }

    fun searchReferees(organizationId: String, query: String): Flow<List<RefereeProfile>> =
        profilesCollection
            .whereEqualTo("organizationId", organizationId)
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val profile = doc.toObject(RefereeProfile::class.java)?.copy(id = doc.id)
                    if (profile != null && profile.name.contains(query, ignoreCase = true)) {
                        profile
                    } else null
                }
            }

    suspend fun checkInReferee(assignmentId: String) {
        assignmentsCollection.document(assignmentId).update("checkedIn", true).await()
    }

    suspend fun checkInRefereeForToday(refereeId: String, organizationId: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.time

        // This is a bit complex as we can't easily query by Date ranges and update in one go without a loop or batch
        val assignments = assignmentsCollection
            .whereEqualTo("refereeId", refereeId)
            .whereEqualTo("organizationId", organizationId)
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .whereLessThan("timestamp", endOfDay)
            .get()
            .await()

        val batch = db.batch()
        assignments.documents.forEach { doc ->
            batch.update(doc.reference, "checkedIn", true)
        }
        batch.commit().await()
    }

    suspend fun assignRefereeToGame(
        refereeId: String,
        gameId: String,
        position: AssignmentPosition,
        organizationId: String
    ): Boolean = db.runTransaction { transaction ->
        val gameRef = gamesCollection.document(gameId)
        val refereeRef = profilesCollection.document(refereeId)
        
        val game = transaction.get(gameRef).toObject(Game::class.java) ?: return@runTransaction false
        val referee = transaction.get(refereeRef).toObject(RefereeProfile::class.java) ?: return@runTransaction false

        // 1. Create the new assignment
        val docRef = assignmentsCollection.document()
        val assignment = Assignment(
            id = docRef.id,
            gameId = gameId,
            refereeId = refereeId,
            position = position,
            status = AssignmentStatus.Confirmed,
            checkedIn = true,
            timestamp = Date(),
            organizationId = organizationId
        )
        transaction.set(docRef, assignment)

        // 2. Update Referee Profile counts (Reflects in Referee App)
        if (position == AssignmentPosition.HeadReferee) {
            transaction.update(refereeRef, "headRefereeGamesCount", referee.headRefereeGamesCount + 1)
        } else {
            transaction.update(refereeRef, "assistantRefereeGamesCount", referee.assistantRefereeGamesCount + 1)
        }

        // 3. Update Game Status
        if (game.status == GameStatus.Open) {
            transaction.update(gameRef, "status", GameStatus.PartiallyFilled.name)
        }
        
        true
    }.await()

    suspend fun removeRefereeFromAssignment(assignmentId: String, refereeId: String, gameId: String): Boolean = db.runTransaction { transaction ->
        val assignmentRef = assignmentsCollection.document(assignmentId)
        val refereeRef = profilesCollection.document(refereeId)
        val gameRef = gamesCollection.document(gameId)

        val referee = transaction.get(refereeRef).toObject(RefereeProfile::class.java) ?: return@runTransaction false
        val assignment = transaction.get(assignmentRef).toObject(Assignment::class.java) ?: return@runTransaction false
        val game = transaction.get(gameRef).toObject(Game::class.java) ?: return@runTransaction false

        // 1. Delete assignment
        transaction.delete(assignmentRef)

        // 2. Decrement counts in profile
        if (assignment.position == AssignmentPosition.HeadReferee) {
            transaction.update(refereeRef, "headRefereeGamesCount", (referee.headRefereeGamesCount - 1).coerceAtLeast(0))
        } else {
            transaction.update(refereeRef, "assistantRefereeGamesCount", (referee.assistantRefereeGamesCount - 1).coerceAtLeast(0))
        }

        // 3. Update Game Status (If it was Full, it's now PartiallyFilled)
        if (game.status == GameStatus.Full) {
            transaction.update(gameRef, "status", GameStatus.PartiallyFilled.name)
        }
        // Note: If last ref is removed, ideally it goes to Open, but that requires a query 
        // which isn't easy in a transaction. The VM will handle visual state.

        true
    }.await()

    suspend fun getOrganization(organizationId: String): Organization? =
        organizationsCollection.document(organizationId).get().await().toObject(Organization::class.java)?.copy(id = organizationId)

    fun getOrganizationFlow(id: String): Flow<Organization?> =
        organizationsCollection.document(id).snapshots().map { snapshot ->
            snapshot.toObject(Organization::class.java)?.copy(id = snapshot.id)
        }

    suspend fun updateOrganizationCode(organizationId: String, newCode: String) {
        organizationsCollection.document(organizationId).update("currentGameDayCode", newCode).await()
    }

    suspend fun updateOrganizationCodeAndStatus(organizationId: String, newCode: String, status: String) {
        organizationsCollection.document(organizationId).update(
            mapOf(
                "currentGameDayCode" to newCode,
                "printerStatus" to status
            )
        ).await()
    }

    suspend fun getRefereeProfile(id: String): RefereeProfile? =
        profilesCollection.document(id).get().await().toObject(RefereeProfile::class.java)?.copy(id = id)

    suspend fun submitPrintJob(job: PrintJob) {
        val docRef = printJobsCollection.document()
        val finalJob = job.copy(id = docRef.id)
        docRef.set(finalJob).await()
    }
}
