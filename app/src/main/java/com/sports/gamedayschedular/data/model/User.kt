package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.PropertyName

enum class UserRole {
    Referee, Admin, Mentor, SystemAdmin, CoachAdmin
}

data class User(
    val id: String = "",
    val email: String = "",
    @get:PropertyName("role") @set:PropertyName("role")
    var role: String = "Referee",
    val organizationId: String? = null,
    val adminOrgIds: List<String> = emptyList(),
    val coachOrgIds: List<String> = emptyList(),
    val isAccountLocked: Boolean = false
) {
    val userRole: UserRole
        get() = when (role.lowercase()) {
            "admin", "referee_admin", "refereeadmin" -> UserRole.Admin
            "systemadmin", "system_admin" -> UserRole.SystemAdmin
            "mentor" -> UserRole.Mentor
            "coachadmin", "coach_admin" -> UserRole.CoachAdmin
            else -> UserRole.Referee
        }
}
