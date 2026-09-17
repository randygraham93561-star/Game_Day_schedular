package com.sports.gamedayschedular.ui.referee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sports.gamedayschedular.data.model.*
import com.sports.gamedayschedular.data.repository.FirestoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class RefereeSearchViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    private val _searchQuery = MutableStateFlow("")
    private val _organizationId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val referees: StateFlow<List<RefereeProfile>> = combine(_organizationId.filterNotNull(), _searchQuery) { orgId, query ->
        repository.searchReferees(orgId, query)
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setOrganization(orgId: String) {
        _organizationId.value = orgId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun validateAndAssign(
        referee: RefereeProfile,
        gameId: String,
        position: AssignmentPosition,
        orgId: String
    ): ValidationResult {
        // 1. Active Status
        if (!referee.isActive) return ValidationResult.Error("Referee is inactive.")

        // 2. Fetch game details for further checks
        val game = repository.getGamesForDay(orgId, Date()).first().find { it.id == gameId }
            ?: return ValidationResult.Error("Game not found.")

        // 3. Comfort Level Checks
        val comfortLevel = if (position == AssignmentPosition.HeadReferee) {
            referee.headRefereeComfortLevel
        } else {
            referee.assistantRefereeComfortLevel
        }

        if (game.difficultyLevel > comfortLevel + 1) {
            return ValidationResult.Error("This game is too far above your comfort level.")
        }
        
        val needsWarning = game.difficultyLevel == comfortLevel + 1

        // 4. Overlap/Conflict Check
        val todayGames = repository.getGamesForDay(orgId, Date()).first()
        val allAssignments = repository.getAssignmentsForOrganization(orgId).first().filter { it.refereeId == referee.id }
        
        val newGameStartTime = game.date
        // Standard 90-minute window for conflicts
        val gameDurationMs = 90L * 60 * 1000 
        val newGameEndTime = Date(newGameStartTime.time + gameDurationMs)

        val conflict = allAssignments.any { assign ->
            val existingGame = todayGames.find { it.id == assign.gameId } ?: return@any false
            val existingStart = existingGame.date
            val existingEnd = Date(existingStart.time + gameDurationMs)
            
            // Overlap formula: (StartA < EndB) and (EndA > StartB)
            (newGameStartTime.before(existingEnd) && newGameEndTime.after(existingStart))
        }

        if (conflict) {
            return ValidationResult.Error("Time Conflict: You are already assigned to a game that overlaps with this time.")
        }

        // 5. Youth Referee Restriction
        val org = repository.getOrganization(orgId)
        if (referee.isMinor && org != null) {
            val refereeStep = DivisionDifficulty.getDivisionStepForAge(referee.age)
            val gameStep = DivisionDifficulty.getDivisionStep(game.ageGroup)
            
            val gapRequired = if (position == AssignmentPosition.HeadReferee) 
                org.youthRefereeRequirements.minDivisionGapHead 
            else 
                org.youthRefereeRequirements.minDivisionGapAR
            
            if (refereeStep - gameStep < gapRequired) {
                return ValidationResult.Error("Youth Restriction: You must be at least $gapRequired divisions above the players to officiate this role. (Actual Gap: ${refereeStep - gameStep})")
            }
        }

        if (needsWarning) {
            return ValidationResult.Warning("This game is one division above your comfort level. Do you want to continue?")
        }

        val success = repository.assignRefereeToGame(referee.id, gameId, position, orgId)
        return if (success) ValidationResult.Success else ValidationResult.Error("Assignment failed (maybe already filled).")
    }

    suspend fun checkIn(referee: RefereeProfile, orgId: String): Boolean {
        return try {
            repository.checkInRefereeForToday(referee.id, orgId)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkLunchEligibility(referee: RefereeProfile, orgId: String): Pair<Boolean, String?> {
        val games = repository.getGamesForDay(orgId, Date()).first()
        val assignments = repository.getAssignmentsForOrganization(orgId).first().filter { it.refereeId == referee.id }
        val season = repository.getActiveSeason(orgId).first() ?: return false to "No active season found."

        val assignedRequired = season.lunchVoucherAssignedGamesRequired
        val completedRequired = season.lunchVoucherCompletedGamesRequired

        val assignedToday = assignments.size
        val completedToday = assignments.count { a ->
            val g = games.find { it.id == a.gameId }
            g != null && (g.status == GameStatus.ReportApproved || g.status == GameStatus.Completed)
        }

        val isEligible = (assignedToday >= assignedRequired) && (completedToday >= completedRequired)
        
        val msg = if (!isEligible) {
            "Not Eligible: Requires $assignedRequired assigned and $completedRequired completed games. Current: $assignedToday/$completedToday."
        } else {
            "Eligible: You meet the requirement of $assignedRequired games."
        }
        
        return isEligible to msg
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
