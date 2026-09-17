package com.sports.gamedayschedular.ui.transform

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sports.gamedayschedular.data.model.*
import com.sports.gamedayschedular.data.repository.FirestoreRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

data class DashboardSummary(
    val clock: String = "",
    val checkInCount: Int = 0,
    val printerCode: String = "",
    val backgroundColor: Int = Color.parseColor("#0D47A1"), // Default Dark Blue
    val textColor: Int = Color.WHITE,
    val logoUrl: String? = null
)

data class AssignmentWithName(
    val assignment: Assignment,
    val refereeName: String
)

data class GameWithAssignments(
    val game: Game,
    val assignments: List<AssignmentWithName>,
    val durationMinutes: Int
)

data class DivisionWithGames(
    val division: Division,
    val games: List<GameWithAssignments>
)

class TransformViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val isGeneratingCode = AtomicBoolean(false)

    private val _organizationId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardSummary: StateFlow<DashboardSummary> = _organizationId
        .filterNotNull()
        .flatMapLatest { orgId ->
            combine(
                repository.getOrganizationFlow(orgId),
                repository.getAssignmentsForOrganization(orgId)
            ) { org, assignments ->
                val code = org?.currentGameDayCode ?: ""
                val status = org?.printerStatus ?: "IDLE"
                
                // CRITICAL: Robust rotation trigger with race condition protection
                if ((code.isEmpty() || code.startsWith("CONSUMED")) && status == "IDLE") {
                    if (isGeneratingCode.compareAndSet(false, true)) {
                        viewModelScope.launch {
                            try {
                                val newCode = (100000..999999).random().toString()
                                Log.d("GDS_CODE", "Generating new code: $newCode")
                                // Update both code and status to be 100% sure
                                repository.updateOrganizationCodeAndStatus(orgId, newCode, "IDLE")
                            } finally {
                                isGeneratingCode.set(false)
                            }
                        }
                    }
                }
                
                val checkedInCount = assignments.filter { it.checkedIn }.map { it.refereeId }.distinct().size

                val bgHex = when (org?.themeColor) {
                    "Red" -> "#B71C1C"
                    "Blue" -> "#0D47A1"
                    "Yellow" -> "#FBC02D"
                    else -> "#0D47A1" // Default Blue
                }
                
                val bgColor = Color.parseColor(bgHex)
                // Auto-contrast logic: if background is yellow (light), use black text
                val txtColor = if (org?.themeColor == "Yellow") Color.BLACK else Color.WHITE
                
                DashboardSummary(
                    printerCode = if (status == "PRINTING") "PRINTING" else code,
                    checkInCount = checkedInCount,
                    backgroundColor = bgColor,
                    textColor = txtColor,
                    logoUrl = org?.logoUrl
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    @OptIn(ExperimentalCoroutinesApi::class)
    val divisionsWithGames: StateFlow<List<DivisionWithGames>> = _organizationId
        .filterNotNull()
        .flatMapLatest { orgId ->
            repository.getOrganizationFlow(orgId).filterNotNull().flatMapLatest { org ->
                val tz = TimeZone.getTimeZone(org.timeZone)
                val gamesFlow = repository.getGamesForDay(orgId, Date(), tz)
                val seasonFlow = repository.getActiveSeason(orgId)
                val assignmentsFlow = repository.getAssignmentsForOrganization(orgId)
                val profilesFlow = repository.getProfilesForOrganization(orgId)

                combine(gamesFlow, seasonFlow, assignmentsFlow, profilesFlow) { games, season, assignments, profiles ->
                    val divisions = season?.divisions ?: emptyList()
                    val now = Date()

                    // Filter games based on duration expiration
                    val activeGames = games.filter { game ->
                        val div = divisions.find { (it.name == game.divisionName || it.name == game.ageGroup) && it.gender == game.gender }
                        val durationMinutes = (div?.halfDurationMinutes ?: 45) * 2 + 15 // 2 halves + 15m buffer
                        val endTime = Date(game.date.time + (durationMinutes * 60 * 1000))
                        
                        // Keep game on screen if it hasn't ended yet
                        now.before(endTime)
                    }.distinctBy { it.id }

                    val allocatedGameIds = mutableSetOf<String>()

                    divisions.map { division ->
                        val divisionGames = activeGames.filter { 
                            it.id !in allocatedGameIds && 
                            (it.divisionName == division.name || it.ageGroup == division.name) &&
                            it.gender == division.gender
                        }
                        
                        allocatedGameIds.addAll(divisionGames.map { it.id })

                        val gamesWithAssignments = divisionGames.map { game ->
                            val gameAssignments = assignments.filter { it.gameId == game.id }
                            val assignmentsWithNames = gameAssignments.map { assignment ->
                                val referee = profiles.find { it.id == assignment.refereeId }
                                AssignmentWithName(assignment, referee?.name ?: "Unknown")
                            }
                            
                            // Use both name and gender for duration lookup
                            val div = divisions.find { 
                                (it.name == game.divisionName || it.name == game.ageGroup) && 
                                it.gender == game.gender 
                            }
                            val duration = (div?.halfDurationMinutes ?: 45) * 2 + 15
                            
                            GameWithAssignments(game, assignmentsWithNames, duration)
                        }
                        DivisionWithGames(division, gamesWithAssignments)
                    }.filter { it.games.isNotEmpty() }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setOrganization(orgId: String) {
        _organizationId.value = orgId
    }

    suspend fun removeReferee(assignment: Assignment, gameId: String): Boolean {
        return try {
            repository.removeRefereeFromAssignment(assignment.id, assignment.refereeId, gameId)
            true
        } catch (e: Exception) {
            Log.e("GDS_ASSIGN", "Failed to remove referee: ${e.message}")
            false
        }
    }

    private fun generateAndSetNewCode(orgId: String) {
        viewModelScope.launch {
            val newCode = (100000..999999).random().toString()
            repository.updateOrganizationCode(orgId, newCode)
        }
    }
}
