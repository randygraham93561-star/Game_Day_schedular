package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Date

enum class SubscriptionTier {
    Free, Base, Standard, Pro
}

@IgnoreExtraProperties
data class Organization(
    var id: String = "",
    var name: String = "",
    var contactEmail: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    
    @get:PropertyName("teamAppSyncEnabled") @set:PropertyName("teamAppSyncEnabled")
    var teamAppSyncEnabled: Boolean = false,
    
    var tier: String = "Free",
    var subscriptionExpiresAt: Date? = null,
    var printerIp: String = "",
    var printerMacAddress: String = "",
    var printerModel: String = "Generic Thermal",
    var currentGameDayCode: String = "",
    var printerStatus: String = "IDLE",
    var timeZone: String = "UTC",
    var themeColor: String = "Default",
    var logoUrl: String? = null,
    
    var youthRefereeRequirements: YouthRefereeRequirements = YouthRefereeRequirements(),
    
    var printerSettings: Map<String, String> = mapOf(
        "paper_width" to "80mm",
        "print_density" to "Normal",
        "auto_cut" to "true",
        "voucher_header" to "League Lunch Voucher",
        "character_set" to "UTF-8"
    )
) {
    @get:Exclude
    val isTeamAppSyncEnabled: Boolean get() = teamAppSyncEnabled

    @get:Exclude
    val subscriptionTier: SubscriptionTier
        get() = when (tier.lowercase()) {
            "base" -> SubscriptionTier.Base
            "standard" -> SubscriptionTier.Standard
            "pro" -> SubscriptionTier.Pro
            else -> SubscriptionTier.Free
        }
}

@IgnoreExtraProperties
data class YouthRefereeRequirements(
    var minDivisionGapHead: Int = 2,
    var minDivisionGapAR: Int = 1
)
