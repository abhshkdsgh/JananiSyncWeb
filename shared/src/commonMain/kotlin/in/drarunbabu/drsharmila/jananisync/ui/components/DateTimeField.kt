package `in`.drarunbabu.drsharmila.jananisync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import `in`.drarunbabu.drsharmila.jananisync.ui.localTimestampParts
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis
import kotlin.time.Instant

/** Calendar dates use UTC only for the picker API; clinical times are local wall times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    dateOnly: Boolean = false,
    initialYearsAgo: Int = 0,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var showDate by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<String?>(null) }
    val display = if (dateOnly && value.length == 8) {
        "${value.take(4)}-${value.substring(4, 6)}-${value.takeLast(2)}"
    } else value
    Box(modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = display,
            enabled = enabled,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                Row {
                    IconButton(enabled = enabled, onClick = { showDate = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Choose $label")
                    }
                    trailingContent?.invoke()
                }
            }
        )
        // A separate hit target opens the dialog from the whole read-only field.
        Box(Modifier.matchParentSize().padding(end = if (trailingContent != null) 112.dp else 64.dp)
            .clickable(enabled = enabled, onClickLabel = "Choose $label") { showDate = true })
    }
    if (showDate && enabled) {
        val initialDate = runCatching {
            Instant.parse("${display.take(10)}T00:00:00Z").toEpochMilliseconds()
        }.getOrNull() ?: if (initialYearsAgo > 0) {
            val local = localTimestampParts(currentTimeMillis())
            val year = local[0] - initialYearsAgo
            val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
            val day = if (local[1] == 2 && local[2] == 29 && !leapYear) 28 else local[2]
            val date = "${year.toString().padStart(4, '0')}-${local[1].toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            Instant.parse("${date}T00:00:00Z").toEpochMilliseconds()
        } else null
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        val date = Instant.fromEpochMilliseconds(picker.selectedDateMillis!!).toString().take(10)
                        showDate = false
                        if (dateOnly) onValueChange(date.replace("-", "")) else pendingDate = date
                    }
                ) { Text(if (dateOnly) "OK" else "Next: time") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(picker) }
    }
    pendingDate?.takeIf { enabled }?.let { date ->
        val now = localTimestampParts(currentTimeMillis())
        val time = value.substringAfter(' ', "").split(':')
        val picker = rememberTimePickerState(
            initialHour = time.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: now[3],
            initialMinute = time.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: now[4],
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pendingDate = null },
            title = { Text("$label · time") },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("$date ${picker.hour.toString().padStart(2, '0')}:${picker.minute.toString().padStart(2, '0')}")
                    pendingDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pendingDate = null }) { Text("Cancel") } }
        )
    }
}
