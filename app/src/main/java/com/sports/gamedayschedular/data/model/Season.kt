package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Date

@IgnoreExtraProperties
data class Division(
    var name: String = "",
    var gender: String = "Coed", // Boys, Girls, Coed
    var teamsAccumulatePoints: Boolean = true,
    var mentorsAllowed: Boolean = true,
    
    @get:PropertyName("friendlyByDefault") @set:PropertyName("friendlyByDefault")
    var friendlyByDefault: Boolean = false,
    
    var playersPerTeam: Int = 11,
    var halfDurationMinutes: Int = 45,
    var ballSize: Int = 5,
    var difficultyLevel: Int = 0
) {
    @get:Exclude
    val isFriendlyByDefault: Boolean get() = friendlyByDefault
}

@IgnoreExtraProperties
data class Season(
    var id: String = "",
    var name: String = "",
    var startDate: Date = Date(),
    var endDate: Date = Date(),
    var active: Boolean = true,
    var live: Boolean = false,
    var archivedAt: Date? = null,
    var organizationId: String = "",
    var collectPoints: Boolean = false,
    var maxPointsPerWeekend: Int = 0,
    var centerRefereePoints: Int = 0,
    var assistantRefereePoints: Int = 0,
    var lunchVoucherAssignedGamesRequired: Int = 0,
    var lunchVoucherCompletedGamesRequired: Int = 0,
    var divisions: List<Division> = emptyList()
)
