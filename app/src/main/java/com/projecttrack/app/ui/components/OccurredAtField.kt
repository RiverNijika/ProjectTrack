package com.projecttrack.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projecttrack.app.ui.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 发生时间选择控件：默认当前时间，点击后先选日期再选时间，便于补录历史进展。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccurredAtField(
    epochMillis: Long,
    onTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate: LocalDate? by remember { mutableStateOf(null) }

    val zone = ZoneId.systemDefault()
    val currentDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val currentHour = Instant.ofEpochMilli(epochMillis).atZone(zone).hour
    val currentMinute = Instant.ofEpochMilli(epochMillis).atZone(zone).minute

    Row(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showDatePicker = true }) {
            Text("发生时间：${Formatters.dateTime(epochMillis)}")
        }
        Spacer(Modifier.width(8.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        pendingDate = selected?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        } ?: currentDate
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = pendingDate ?: currentDate
                        val millis = date
                            .atTime(timeState.hour, timeState.minute)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        onTimeChange(millis)
                        showTimePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
        )
    }
}
