package com.netlocker.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netlocker.domain.model.ALL_DAYS_MASK
import com.netlocker.domain.model.dayBit
import com.netlocker.ui.theme.netLocker
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "9:30 PM" from minutes-since-midnight — what the schedule fields store. */
fun formatMinuteOfDay(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).format(TIME_FORMAT)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * Per-app "block during this time window" section, shown inside the Edit Rule dialog and
 * the Add Rule sheet — only when the caller has already checked the Settings master
 * switch is on (see [com.netlocker.util.PreferencesManager.scheduleMasterEnabled]); this
 * composable itself doesn't know about that switch, it just renders whatever it's given.
 */
@Composable
fun ScheduleForm(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    days: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.netLocker
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.wifi.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.wifi, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Schedule", fontWeight = FontWeight.SemiBold)
                Text(
                    "Block this app's internet during a time window",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
            )
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("From", startMinute, Modifier.weight(1f)) { pickingStart = true }
                TimeField("To", endMinute, Modifier.weight(1f)) { pickingEnd = true }
            }
            if (startMinute == endMinute) {
                Text(
                    "Start and end are the same time — this would block all day.",
                    fontSize = 12.sp,
                    color = colors.blocked,
                )
            }
            DaySelector(days = days, onDaysChange = onDaysChange)
        }
    }

    if (pickingStart) {
        TimePickerDialog(initialMinute = startMinute, onDismiss = { pickingStart = false }) {
            onStartChange(it)
            pickingStart = false
        }
    }
    if (pickingEnd) {
        TimePickerDialog(initialMinute = endMinute, onDismiss = { pickingEnd = false }) {
            onEndChange(it)
            pickingEnd = false
        }
    }
}

/** "Su" "Mo" "Tu" "We" "Th" "Fr" "Sa", index 0=Sunday matching [com.netlocker.domain.model.dayBit]. */
private val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/** "Everyday" / "Weekdays" / "Weekends" / "Mon, Wed, Fri" — a short summary of [days] for
 *  the Rules-tab card, where full day chips don't fit. */
fun formatScheduleDays(days: Int): String = when (days) {
    ALL_DAYS_MASK -> "Everyday"
    WEEKDAYS_MASK -> "Weekdays"
    WEEKEND_MASK -> "Weekends"
    else -> (0..6).filter { (days shr it) and 1 == 1 }.joinToString(", ") { DAY_LABELS[it] }
}

private const val WEEKDAYS_MASK = 0b011_1110 // Mon-Fri
private const val WEEKEND_MASK = 0b100_0001 // Sat+Sun

/** Everyday / individual Su-Sa toggle chips for which days [ScheduleForm]'s window applies to. */
@Composable
private fun DaySelector(days: Int, onDaysChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Repeats", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // "Everyday" gets its own full-width row: at 7 chips wide it has no room to spell
        // itself out without truncating (it did, silently, sharing a row with the day
        // letters — Compose's default Text overflow clips rather than showing "…").
        DayChip(
            label = "Everyday",
            selected = days == ALL_DAYS_MASK,
            onClick = { onDaysChange(ALL_DAYS_MASK) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LABELS.forEachIndexed { index, label ->
                val bit = dayBit(index)
                DayChip(
                    label = label,
                    selected = (days and bit) != 0,
                    onClick = {
                        // Everyday is derived (all bits set), not a separate stored state —
                        // toggling one day out of "Everyday" leaves the other six selected,
                        // which is the expected "I meant every day except this one" result.
                        val next = days xor bit
                        onDaysChange(if (next == 0) bit else next) // never allow zero days selected
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.netLocker
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.wifi.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.wifi else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(formatMinuteOfDay(minute), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (minute: Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.netLocker.card,
        text = { TimePicker(state = state) },
        confirmButton = { Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
