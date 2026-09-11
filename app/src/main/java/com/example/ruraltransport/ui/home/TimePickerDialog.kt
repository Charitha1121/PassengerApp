package com.example.ruraltransport.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun FutureTimePickerDialog(
    initialTargetEpochMillis: Long,
    onDismiss: () -> Unit,
    onTimeSelected: (Long) -> Unit
) {
    val initialCal = remember {
        Calendar.getInstance().apply { timeInMillis = initialTargetEpochMillis }
    }

    var isTomorrow by remember {
        val nowCal = Calendar.getInstance()
        mutableStateOf(
            initialCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR) ||
            initialCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)
        )
    }

    val rawHour = initialCal.get(Calendar.HOUR)
    var selectedHour by remember { mutableIntStateOf(if (rawHour == 0) 12 else rawHour) }
    var selectedMinute by remember { mutableIntStateOf((initialCal.get(Calendar.MINUTE) / 5) * 5) }
    var isPm by remember { mutableStateOf(initialCal.get(Calendar.AM_PM) == Calendar.PM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Future Transport Time", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "When will you need an auto?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date Selection: Today vs Tomorrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentButton(
                        text = "Today",
                        selected = !isTomorrow,
                        modifier = Modifier.weight(1f),
                        onClick = { isTomorrow = false }
                    )
                    SegmentButton(
                        text = "Tomorrow",
                        selected = isTomorrow,
                        modifier = Modifier.weight(1f),
                        onClick = { isTomorrow = true }
                    )
                }

                // Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Picker
                    NumberPicker(
                        value = selectedHour,
                        range = 1..12,
                        label = "Hour",
                        onValueChange = { selectedHour = it }
                    )

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minutes Picker (multiples of 5 for transport convenience)
                    NumberPicker(
                        value = selectedMinute,
                        range = 0..55 step 5,
                        label = "Minute",
                        formatter = { "%02d".format(it) },
                        onValueChange = { selectedMinute = it }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // AM / PM Toggle
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SegmentButton(
                            text = "AM",
                            selected = !isPm,
                            onClick = { isPm = false }
                        )
                        SegmentButton(
                            text = "PM",
                            selected = isPm,
                            onClick = { isPm = true }
                        )
                    }
                }

                // Summary
                val amPmStr = if (isPm) "PM" else "AM"
                val dayStr = if (isTomorrow) "Tomorrow" else "Today"
                val minStr = "%02d".format(selectedMinute)
                Text(
                    text = "Target Time: $dayStr at $selectedHour:$minStr $amPmStr",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resultCal = Calendar.getInstance().apply {
                        if (isTomorrow) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                        var hour24 = selectedHour % 12
                        if (isPm) hour24 += 12
                        set(Calendar.HOUR_OF_DAY, hour24)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onTimeSelected(resultCal.timeInMillis)
                }
            ) {
                Text("Confirm Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: Iterable<Int>,
    label: String,
    formatter: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit
) {
    val list = remember(range) { range.toList() }
    val currentIndex = list.indexOf(value).coerceAtLeast(0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            TextButton(
                onClick = {
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.lastIndex
                    onValueChange(list[prevIndex])
                }
            ) {
                Text("▼", fontWeight = FontWeight.Bold)
            }

            Text(
                text = formatter(value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            TextButton(
                onClick = {
                    val nextIndex = if (currentIndex < list.lastIndex) currentIndex + 1 else 0
                    onValueChange(list[nextIndex])
                }
            ) {
                Text("▲", fontWeight = FontWeight.Bold)
            }
        }
    }
}
