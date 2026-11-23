package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DateFilterPreset {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_MONTH,
    SINGLE_DATE,
    DATE_RANGE
}

data class DateRangeFilterState(
    val preset: DateFilterPreset = DateFilterPreset.TODAY,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val label: String = "Hoy"
) {
    fun matches(timestamp: Long): Boolean {
        if (preset == DateFilterPreset.ALL || startTimestamp == null || endTimestamp == null) {
            return true
        }
        return timestamp in startTimestamp..endTimestamp
    }
}

object DateFilterUtils {
    private val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale("es", "ES"))

    fun startOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /**
     * Converts Material 3 DatePicker UTC midnight millis into local day start/end preserving the chosen calendar date.
     */
    fun utcPickerMillisToLocalDayMillis(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun allTime(): DateRangeFilterState = DateRangeFilterState(
        preset = DateFilterPreset.ALL,
        startTimestamp = null,
        endTimestamp = null,
        label = "Todo el historial"
    )

    fun today(referenceTimeMillis: Long = System.currentTimeMillis()): DateRangeFilterState {
        val start = startOfDay(referenceTimeMillis)
        val end = endOfDay(referenceTimeMillis)
        return DateRangeFilterState(
            preset = DateFilterPreset.TODAY,
            startTimestamp = start,
            endTimestamp = end,
            label = "Hoy (${shortDateFormat.format(Date(start))})"
        )
    }

    fun yesterday(referenceTimeMillis: Long = System.currentTimeMillis()): DateRangeFilterState {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val start = startOfDay(cal.timeInMillis)
        val end = endOfDay(cal.timeInMillis)
        return DateRangeFilterState(
            preset = DateFilterPreset.YESTERDAY,
            startTimestamp = start,
            endTimestamp = end,
            label = "Ayer (${shortDateFormat.format(Date(start))})"
        )
    }

    fun thisMonth(referenceTimeMillis: Long = System.currentTimeMillis()): DateRangeFilterState {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = startOfDay(cal.timeInMillis)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = endOfDay(cal.timeInMillis)
        val labelMonth = monthYearFormat.format(Date(start)).replaceFirstChar { it.uppercase() }
        return DateRangeFilterState(
            preset = DateFilterPreset.THIS_MONTH,
            startTimestamp = start,
            endTimestamp = end,
            label = "Mes: $labelMonth"
        )
    }

    fun singleDate(localDayMillis: Long): DateRangeFilterState {
        val start = startOfDay(localDayMillis)
        val end = endOfDay(localDayMillis)
        return DateRangeFilterState(
            preset = DateFilterPreset.SINGLE_DATE,
            startTimestamp = start,
            endTimestamp = end,
            label = shortDateFormat.format(Date(start))
        )
    }

    fun dateRange(startLocalMillis: Long, endLocalMillis: Long): DateRangeFilterState {
        val minMillis = minOf(startLocalMillis, endLocalMillis)
        val maxMillis = maxOf(startLocalMillis, endLocalMillis)
        val start = startOfDay(minMillis)
        val end = endOfDay(maxMillis)
        return DateRangeFilterState(
            preset = DateFilterPreset.DATE_RANGE,
            startTimestamp = start,
            endTimestamp = end,
            label = "${shortDateFormat.format(Date(start))} - ${shortDateFormat.format(Date(end))}"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateRangeFilterBar(
    state: DateRangeFilterState,
    onStateChange: (DateRangeFilterState) -> Unit,
    showAllOption: Boolean = true,
    showYesterdayOption: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showSingleDatePicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showAllOption) {
                    CompactDatePresetChip(
                        selected = state.preset == DateFilterPreset.ALL,
                        label = "Todo",
                        onClick = { onStateChange(DateFilterUtils.allTime()) },
                        testTag = "date_filter_all"
                    )
                }

                CompactDatePresetChip(
                    selected = state.preset == DateFilterPreset.TODAY,
                    label = "Hoy",
                    icon = Icons.Filled.Today,
                    onClick = { onStateChange(DateFilterUtils.today()) },
                    testTag = "date_filter_today"
                )

                if (showYesterdayOption) {
                    CompactDatePresetChip(
                        selected = state.preset == DateFilterPreset.YESTERDAY,
                        label = "Ayer",
                        onClick = { onStateChange(DateFilterUtils.yesterday()) },
                        testTag = "date_filter_yesterday"
                    )
                }

                CompactDatePresetChip(
                    selected = state.preset == DateFilterPreset.THIS_MONTH,
                    label = "Este Mes",
                    onClick = { onStateChange(DateFilterUtils.thisMonth()) },
                    testTag = "date_filter_month"
                )

                CompactDatePresetChip(
                    selected = state.preset == DateFilterPreset.SINGLE_DATE,
                    label = if (state.preset == DateFilterPreset.SINGLE_DATE) state.label else "Por Fecha",
                    icon = Icons.Filled.CalendarMonth,
                    onClick = { showSingleDatePicker = true },
                    testTag = "date_filter_single"
                )

                CompactDatePresetChip(
                    selected = state.preset == DateFilterPreset.DATE_RANGE,
                    label = if (state.preset == DateFilterPreset.DATE_RANGE) state.label else "Por Rango",
                    icon = Icons.Filled.DateRange,
                    onClick = { showRangePicker = true },
                    testTag = "date_filter_range"
                )
            }

            if (state.preset != DateFilterPreset.ALL) {
                Text(
                    text = "Período activo: ${state.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }

    if (showSingleDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startTimestamp ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showSingleDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val localMillis = DateFilterUtils.utcPickerMillisToLocalDayMillis(utcMillis)
                            onStateChange(DateFilterUtils.singleDate(localMillis))
                        }
                        showSingleDatePicker = false
                    }
                ) {
                    Text("Aplicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSingleDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showRangePicker) {
        val rangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.startTimestamp,
            initialSelectedEndDateMillis = state.endTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = rangePickerState.selectedStartDateMillis != null,
                    onClick = {
                        val startUtc = rangePickerState.selectedStartDateMillis
                        val endUtc = rangePickerState.selectedEndDateMillis ?: startUtc
                        if (startUtc != null && endUtc != null) {
                            val localStart = DateFilterUtils.utcPickerMillisToLocalDayMillis(startUtc)
                            val localEnd = DateFilterUtils.utcPickerMillisToLocalDayMillis(endUtc)
                            onStateChange(DateFilterUtils.dateRange(localStart, localEnd))
                        }
                        showRangePicker = false
                    }
                ) {
                    Text("Aplicar Rango")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DateRangePicker(
                state = rangePickerState,
                title = {
                    Text(
                        "Seleccionar Rango de Fechas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                    )
                },
                modifier = Modifier.height(420.dp)
            )
        }
    }
}

@Composable
private fun CompactDatePresetChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else null,
        label = {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .height(34.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    )
}
