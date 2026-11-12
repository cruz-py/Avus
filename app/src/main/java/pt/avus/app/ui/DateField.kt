package pt.avus.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import pt.avus.app.letter.LetterGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val storedFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val digitsFormat = DateTimeFormatter.ofPattern("ddMMyyyy")

/**
 * Date input stored as "dd/mm/aaaa". The text field only holds digits and the slashes are drawn by a
 * [VisualTransformation], so the cursor never jumps while typing. A calendar button opens a date picker.
 */
@Composable
fun DateFieldInput(
    label: String,
    value: String,
    emptyHint: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = LetterGenerator.parseDate(value)
    val digits = parsed?.format(digitsFormat) ?: value.filter { it.isDigit() }.take(8)
    val age = LetterGenerator.age(value)
    val hint = when {
        value.isBlank() -> emptyHint
        age != null -> "$age anos"
        digits.length < 8 -> emptyHint
        else -> "Data inválida"
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = digits,
        onValueChange = { typed -> onChange(withSlashes(typed.filter { it.isDigit() }.take(8))) },
        label = { Text(label) },
        supportingText = if (hint.isNotBlank()) ({ Text(hint) }) else null,
        isError = digits.length == 8 && age == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = DateSlashesTransformation,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Escolher no calendário")
            }
        },
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    )

    if (showPicker) {
        val today = LocalDate.now()
        // Most people using the app are elderly: open the calendar around 1945 when nothing is filled in yet.
        val initial = parsed?.takeIf { age != null } ?: LocalDate.of(1945, 1, 1)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsed?.takeIf { age != null }?.toUtcMillis(),
            initialDisplayedMonthMillis = initial.toUtcMillis(),
            yearRange = 1900..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toUtcMillis()
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(storedFormat))
                        }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = pickerState, showModeToggle = true)
        }
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** "01021940" -> "01/02/1940"; partial input gets slashes only between groups already started. */
internal fun withSlashes(digits: String) = buildString {
    digits.forEachIndexed { i, c ->
        if (i == 2 || i == 4) append('/')
        append(c)
    }
}

private object DateSlashesTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                offset + (if (offset > 2) 1 else 0) + (if (offset > 4) 1 else 0)

            override fun transformedToOriginal(offset: Int) =
                (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(withSlashes(digits)), mapping)
    }
}
