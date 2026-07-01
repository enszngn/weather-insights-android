package com.weatherinsights.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationPreferences(
    val criticalAlertsEnabled: Boolean = true,
    val morningReportEnabled: Boolean = true,
    val eveningReportEnabled: Boolean = true,
    val weekendSummaryEnabled: Boolean = true,
    val tempShockEnabled: Boolean = true,
    val morningReportTime: String = "08:00", // HH:mm format
    val eveningReportTime: String = "20:00", // HH:mm format
    val sleepStartTime: String = "22:00",    // HH:mm format
    val sleepEndTime: String = "07:00"       // HH:mm format
)
