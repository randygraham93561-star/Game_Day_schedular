package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.*

enum class PointDistributionMode {
    Even, Manual, NeedsBased
}

@IgnoreExtraProperties
data class RefereeProfile(
    var id: String = "",
    var name: String = "",
    var phoneNumber: String = "",
    var dateOfBirth: Date? = null,
    var badgeLevel: String = "",
    var headRefereeComfortLevel: Int = 0,
    var assistantRefereeComfortLevel: Int = 0,
    var totalPoints: Int = 0,
    var headRefereeGamesCount: Int = 0,
    var assistantRefereeGamesCount: Int = 0,
    var qualifications: List<String> = emptyList(),
    var currentSeasonId: String = "",
    var teamIdsForPoints: List<String> = emptyList(),
    var distributionMode: PointDistributionMode = PointDistributionMode.Even,
    
    @get:PropertyName("active") @set:PropertyName("active")
    var active: Boolean = true,
    
    var organizationId: String = "",
    var badgeCorrectionRequested: Boolean = false,
    
    @get:PropertyName("newReferee") @set:PropertyName("newReferee")
    var newReferee: Boolean = false,
    
    var hasTakenCourse: Boolean = false,
    
    @get:PropertyName("onboardingCompleted") @set:PropertyName("onboardingCompleted")
    var onboardingCompleted: Boolean = false,
    
    var completedSeasonQuizzes: List<String> = emptyList(),
    var fcmToken: String = "",
    
    @get:PropertyName("accountLocked") @set:PropertyName("accountLocked")
    var accountLocked: Boolean = false
) {
    @get:Exclude
    val isActive: Boolean get() = active
    
    @get:Exclude
    val isNewReferee: Boolean get() = newReferee
    
    @get:Exclude
    val isOnboardingCompleted: Boolean get() = onboardingCompleted
    
    @get:Exclude
    val isAccountLocked: Boolean get() = accountLocked

    @get:Exclude
    val age: Int
        get() {
            val dob = dateOfBirth ?: return 0
            val cal = Calendar.getInstance()
            val now = Calendar.getInstance()
            cal.time = dob
            var age = now.get(Calendar.YEAR) - cal.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < cal.get(Calendar.DAY_OF_YEAR)) age--
            return age
        }

    @get:Exclude
    val isMinor: Boolean
        get() = age > 0 && age < 18
}
