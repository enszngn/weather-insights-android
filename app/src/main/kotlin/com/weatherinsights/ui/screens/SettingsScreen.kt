package com.weatherinsights.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.weatherinsights.data.model.NotificationPreferences
import com.weatherinsights.ui.components.GlassyPanel
import com.weatherinsights.ui.theme.TextPrimary
import com.weatherinsights.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    preferences: NotificationPreferences,
    onPreferencesChanged: (NotificationPreferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTimePickerFor by remember { mutableStateOf<TimePickerTarget?>(null) }

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
                        onClick = { activeTimePickerFor = TimePickerTarget.MorningReport }
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
                        onClick = { activeTimePickerFor = TimePickerTarget.EveningReport }
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
                            .clickable { activeTimePickerFor = TimePickerTarget.SleepStart }
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
                            .clickable { activeTimePickerFor = TimePickerTarget.SleepEnd }
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

    // iOS-Style Openable Wheel Time Picker Window
    if (activeTimePickerFor != null) {
        val currentTarget = activeTimePickerFor!!
        val initialTime = when (currentTarget) {
            TimePickerTarget.MorningReport -> preferences.morningReportTime
            TimePickerTarget.EveningReport -> preferences.eveningReportTime
            TimePickerTarget.SleepStart -> preferences.sleepStartTime
            TimePickerTarget.SleepEnd -> preferences.sleepEndTime
        }
        
        WheelTimePickerDialog(
            title = when (currentTarget) {
                TimePickerTarget.MorningReport -> "Morning Report Time"
                TimePickerTarget.EveningReport -> "Evening Report Time"
                TimePickerTarget.SleepStart -> "Quiet Hours Start"
                TimePickerTarget.SleepEnd -> "Quiet Hours End"
            },
            initialTime = initialTime,
            onDismiss = { activeTimePickerFor = null },
            onConfirm = { selectedTime ->
                val updatedPrefs = when (currentTarget) {
                    TimePickerTarget.MorningReport -> preferences.copy(morningReportTime = selectedTime)
                    TimePickerTarget.EveningReport -> preferences.copy(eveningReportTime = selectedTime)
                    TimePickerTarget.SleepStart -> preferences.copy(sleepStartTime = selectedTime)
                    TimePickerTarget.SleepEnd -> preferences.copy(sleepEndTime = selectedTime)
                }
                onPreferencesChanged(updatedPrefs)
                activeTimePickerFor = null
            }
        )
    }
}

@Composable
private fun NotificationToggleRow(
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
                checkedTrackColor = Color(0xFF34C759),     // Green when on
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.White,
                uncheckedBorderColor = Color.LightGray
            )
        )
    }
}

@Composable
private fun TimeConfigurationRow(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalWheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp
) {
    val items = range.toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(value))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            val selectedVal = items.getOrNull(centerIndex) ?: value
            if (selectedVal != value) {
                onValueChange(selectedVal)
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        // Highlighting center area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color(0x26FFFFFF))
                .border(1.dp, Color(0x33FFFFFF))
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(itemHeight)) }
            items(items.size) { index ->
                val itemVal = items[index]
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", itemVal),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(itemHeight)) }
        }
    }
}

@Composable
private fun WheelTimePickerDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val timePair = com.weatherinsights.data.util.TimeUtils.parseTimeToHourMinute(initialTime)
    var selectedHour by remember { mutableStateOf(timePair?.first ?: 12) }
    var selectedMinute by remember { mutableStateOf(timePair?.second ?: 0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(0.dp)) // Maintain sharp corners styling
                .background(Color(0xEE1E1E1E))   // Dark background for picker modal
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(0.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerticalWheelPicker(
                        value = selectedHour,
                        range = 0..23,
                        onValueChange = { selectedHour = it },
                        modifier = Modifier.width(60.dp)
                    )
                    
                    Text(
                        text = ":",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    VerticalWheelPicker(
                        value = selectedMinute,
                        range = 0..59,
                        onValueChange = { selectedMinute = it },
                        modifier = Modifier.width(60.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        onClick = {
                            onConfirm(String.format("%02d:%02d", selectedHour, selectedMinute))
                        }
                    ) {
                        Text("Save", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private enum class TimePickerTarget {
    MorningReport,
    EveningReport,
    SleepStart,
    SleepEnd
}
