package com.sports.gamedayschedular.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Date

@IgnoreExtraProperties
data class PrintJob(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",
    
    @get:PropertyName("organizationId") @set:PropertyName("organizationId")
    var organizationId: String = "",
    
    @get:PropertyName("refereeId") @set:PropertyName("refereeId")
    var refereeId: String = "",
    
    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = "LunchVoucher",
    
    @get:PropertyName("templateId") @set:PropertyName("templateId")
    var templateId: String = "",
    
    @get:PropertyName("renderedContent") @set:PropertyName("renderedContent")
    var renderedContent: String = "", 
    
    @get:PropertyName("status") @set:PropertyName("status")
    var status: String = "Pending",
    
    @get:PropertyName("data") @set:PropertyName("data")
    var data: Map<String, String> = emptyMap(),
    
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Date = Date(),
    
    @get:PropertyName("errorMessage") @set:PropertyName("errorMessage")
    var errorMessage: String? = null
)
