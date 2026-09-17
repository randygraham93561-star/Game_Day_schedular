package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.PropertyName
import java.util.Date

enum class GameStatus {
    Open, PartiallyFilled, Full, PendingReview, ReportApproved, Completed
}

data class RefereeVerification(
    val refereeId: String = "",
    val isPresent: Boolean = false,
    val correctRole: Boolean = false,
    val actualRefereeId: String? = null,
    val actualRole: AssignmentPosition? = null
)

data class Game(
    val id: String = "",
    val gameNumber: Int = 0,
    val date: Date = Date(),
    val time: String = "",
    val location: String = "",
    val fieldNumber: String = "",
    val ageGroup: String = "",
    val divisionName: String = "",
    val gender: String = "Boys", // Boys, Girls, Coed
    val homeTeamName: String = "",
    val homeTeamId: String = "",
    val awayTeamName: String = "",
    val awayTeamId: String = "",
    val requiredCrewSize: Int = 0,
    val difficultyLevel: Int = 1,
    val seasonId: String = "",
    val status: GameStatus = GameStatus.Open,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val cards: String? = null,
    val notes: String? = null,
    val cardsShown: Boolean = false,
    val cardTypes: String = "",
    val disciplinaryDescription: String = "",
    val reporterSignature: String = "",
    val selectedTargetTeamId: String = "",
    val isDualCenter: Boolean = false,
    val refereeVerifications: List<RefereeVerification> = emptyList(),
    val reportSubmittedAt: Date? = null,
    val reportSubmittedBy: String? = null,
    val organizationId: String = "",
    @get:PropertyName("mentorsAllowed") @set:PropertyName("mentorsAllowed")
    var mentorsAllowed: Boolean = true,
    @get:PropertyName("friendly") @set:PropertyName("friendly")
    var isFriendly: Boolean = false
)
