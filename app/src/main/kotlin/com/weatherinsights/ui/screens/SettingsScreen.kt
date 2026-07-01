package com.weatherinsights.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherinsights.data.model.NotificationPreferences
import com.weatherinsights.ui.components.GlassyPanel
import com.weatherinsights.ui.theme.TextPrimary
import com.weatherinsights.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: NotificationPreferences,
    onPreferencesChanged: (NotificationPreferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePickerDialogFor by remember { mutableStateOf<TimePickerTarget?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Notification Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Critical & Instant Alerts
        Text(
            text = "Critical Notifications",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NotificationToggleRow(
                    title = "Severe Weather Alerts",
                    subtitle = "Instant alerts before storms, hurricanes, heavy snow, or hail.",
                    checked = preferences.criticalAlertsEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(criticalAlertsEnabled = it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Routine & Daily Summaries
        Text(
            text = "Routine Reports",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NotificationToggleRow(
                    title = "Morning Report",
                    subtitle = "Daily weather summary and clothing suggestions to start your day.",
                    checked = preferences.morningReportEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(morningReportEnabled = it))
                    }
                )
                if (preferences.morningReportEnabled) {
                    TimeConfigurationRow(
                        label = "Report Time",
                        time = preferences.morningReportTime,
                        onClick = { showTimePickerDialogFor = TimePickerTarget.MorningReport }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NotificationToggleRow(
                    title = "Evening Report",
                    subtitle = "Summary enabling planning based on tomorrow's weather.",
                    checked = preferences.eveningReportEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(eveningReportEnabled = it))
                    }
                )
                if (preferences.eveningReportEnabled) {
                    TimeConfigurationRow(
                        label = "Report Time",
                        time = preferences.eveningReportTime,
                        onClick = { showTimePickerDialogFor = TimePickerTarget.EveningReport }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Smart & Situational Alerts
        Text(
            text = "Smart Notifications",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NotificationToggleRow(
                    title = "Weekend Summary",
                    subtitle = "Weekend weather forecast report sent on Friday afternoon.",
                    checked = preferences.weekendSummaryEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(weekendSummaryEnabled = it))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                NotificationToggleRow(
                    title = "Temperature Shock Alert",
                    subtitle = "Warns when there is a 10-degree temperature difference compared to the previous day.",
                    checked = preferences.tempShockEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(tempShockEnabled = it))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                NotificationToggleRow(
                    title = "Health and Allergy Alerts",
                    subtitle = "Alerts when pollen levels, air quality (AQI), or UV index are high.",
                    checked = preferences.healthAlertsEnabled,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(healthAlertsEnabled = it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 4: Quiet Hours
        Text(
            text = "Quiet Hours",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        GlassyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Routine notifications other than critical alerts are silenced during these hours.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showTimePickerDialogFor = TimePickerTarget.SleepStart }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Start", fontSize = 14.sp, color = TextSecondary)
                        Text(
                            text = preferences.sleepStartTime,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showTimePickerDialogFor = TimePickerTarget.SleepEnd }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("End", fontSize = 14.sp, color = TextSecondary)
                        Text(
                            text = preferences.sleepEndTime,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    // Time Picker Dialog
    if (showTimePickerDialogFor != null) {
        val currentTarget = showTimePickerDialogFor!!
        val initialTimeString = when (currentTarget) {
            TimePickerTarget.MorningReport -> preferences.morningReportTime
            TimePickerTarget.EveningReport -> preferences.eveningReportTime
            TimePickerTarget.SleepStart -> preferences.sleepStartTime
            TimePickerTarget.SleepEnd -> preferences.sleepEndTime
        }
        val parts = initialTimeString.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialogFor = null },
            confirmButton = {
                TextButton(onClick = {
                    val formattedTime = String.format(
                        "%02d:%02d",
                        timePickerState.hour,
                        timePickerState.minute
                    )
                    val updatedPrefs = when (currentTarget) {
                        TimePickerTarget.MorningReport -> preferences.copy(morningReportTime = formattedTime)
                        TimePickerTarget.EveningReport -> preferences.copy(eveningReportTime = formattedTime)
                        TimePickerTarget.SleepStart -> preferences.copy(sleepStartTime = formattedTime)
                        TimePickerTarget.SleepEnd -> preferences.copy(sleepEndTime = formattedTime)
                    }
                    onPreferencesChanged(updatedPrefs)
                    showTimePickerDialogFor = null
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                }
            },
            title = {
                Text(
                    text = when (currentTarget) {
                        TimePickerTarget.MorningReport -> "Select Morning Report Time"
                        TimePickerTarget.EveningReport -> "Select Evening Report Time"
                        TimePickerTarget.SleepStart -> "Select Quiet Hours Start"
                        TimePickerTarget.SleepEnd -> "Select Quiet Hours End"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0x99009AFF),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Composable
fun TimeConfigurationRow(
    label: String,
    time: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AccessTime,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
        Text(
            text = time,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

enum class TimePickerTarget {
    MorningReport,
    EveningReport,
    SleepStart,
    SleepEnd
}
