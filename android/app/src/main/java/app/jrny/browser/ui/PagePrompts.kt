package app.jrny.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import app.jrny.browser.R
import app.jrny.browser.tab.PagePrompt
import app.jrny.browser.tab.Tab
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Shows what [tab]'s page asks through its scripts and form fields (SPEC, "Page dialogs"): an
 * alert, a question, a line of text, a choice from a list, a date, a time or a color, or whether
 * to leave the page. Each dialog names the site, so a page can't pass its words off as Okapi's.
 * Dismissing a dialog is the page's "Cancel".
 */
@Composable
fun PagePrompts(tab: Tab) {
    val prompt = tab.prompt ?: return
    val site = prompt.site
    val close: () -> Unit = { tab.closePrompt() }
    val dismiss: () -> Unit = {
        prompt.dismiss()
        close()
    }
    // Okapi names the site itself. GeckoView's own wording is long, and a page can't set it.
    val heading = if (site.isNotEmpty()) stringResource(R.string.page_says, site) else null
    when (prompt) {
        is PagePrompt.Alert -> AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.testTag("page-alert"),
            title = heading?.let { { Text(it) } },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(onClick = { prompt.ok(); close() }, modifier = Modifier.testTag("page-prompt-ok")) { Text(stringResource(R.string.ok)) }
            },
        )
        is PagePrompt.Confirm -> AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.testTag("page-confirm"),
            title = heading?.let { { Text(it) } },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(onClick = { prompt.answer(true); close() }, modifier = Modifier.testTag("page-prompt-ok")) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { prompt.answer(false); close() }, modifier = Modifier.testTag("page-prompt-cancel")) { Text(stringResource(R.string.cancel)) }
            },
        )
        is PagePrompt.Text -> {
            var text by remember(prompt) { mutableStateOf(prompt.defaultValue) }
            AlertDialog(
                onDismissRequest = dismiss,
                modifier = Modifier.testTag("page-text-prompt"),
                title = heading?.let { { Text(it) } },
                text = {
                    Column {
                        if (prompt.message.isNotBlank()) Text(prompt.message, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { prompt.answer(text); close() }),
                            modifier = Modifier.fillMaxWidth().testTag("page-prompt-field"),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { prompt.answer(text); close() }, modifier = Modifier.testTag("page-prompt-ok")) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is PagePrompt.LeavePage -> AlertDialog(
            onDismissRequest = { prompt.answer(false); close() },
            modifier = Modifier.testTag("page-leave"),
            title = { Text(stringResource(R.string.leave_page_title)) },
            text = { Text(stringResource(R.string.leave_page_body)) },
            confirmButton = { TextButton(onClick = { prompt.answer(true); close() }) { Text(stringResource(R.string.leave)) } },
            dismissButton = { TextButton(onClick = { prompt.answer(false); close() }) { Text(stringResource(R.string.stay)) } },
        )
        is PagePrompt.Resend -> AlertDialog(
            onDismissRequest = { prompt.answer(false); close() },
            modifier = Modifier.testTag("page-resend"),
            title = { Text(stringResource(R.string.resend_title)) },
            text = { Text(stringResource(R.string.resend_body)) },
            confirmButton = { TextButton(onClick = { prompt.answer(true); close() }) { Text(stringResource(R.string.resend)) } },
            dismissButton = { TextButton(onClick = { prompt.answer(false); close() }) { Text(stringResource(R.string.cancel)) } },
        )
        // A list field is the page's form, not a message from the site: no heading.
        is PagePrompt.Choice -> ChoiceDialog(prompt, null, close, dismiss)
        is PagePrompt.DateTime -> DateTimeDialog(prompt, close, dismiss)
        is PagePrompt.Color -> ColorDialog(prompt, close, dismiss)
    }
}

/** A `<select>`: a tap chooses one item; for several, they are ticked and confirmed with OK. */
@Composable
private fun ChoiceDialog(prompt: PagePrompt.Choice, heading: String?, close: () -> Unit, dismiss: () -> Unit) {
    val chosen = remember(prompt) {
        mutableStateListOf<String>().apply { addAll(flatten(prompt.items).filter { it.selected }.map { it.id }) }
    }
    val rows = remember(prompt) { rows(prompt.items, depth = 0) }
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("page-choice"),
        title = heading?.let { { Text(it) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(rows) { (item, depth) ->
                    when {
                        item.separator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        item.items.isNotEmpty() -> Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = (depth * 16).dp, top = 12.dp, bottom = 4.dp),
                        )
                        else -> {
                            val selected = item.id in chosen
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !item.disabled, role = if (prompt.multiple) Role.Checkbox else Role.RadioButton) {
                                        if (prompt.multiple) {
                                            if (selected) chosen.remove(item.id) else chosen.add(item.id)
                                        } else {
                                            prompt.choose(listOf(item.id))
                                            close()
                                        }
                                    }
                                    .padding(start = (depth * 16).dp)
                                    .testTag("page-choice-item"),
                            ) {
                                if (prompt.multiple) {
                                    Checkbox(checked = selected, onCheckedChange = null, enabled = !item.disabled)
                                } else {
                                    RadioButton(selected = selected, onClick = null, enabled = !item.disabled)
                                }
                                Text(
                                    item.label,
                                    color = if (item.disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.multiple) TextButton(onClick = { prompt.choose(chosen.toList()); close() }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun flatten(items: List<PagePrompt.Choice.Item>): List<PagePrompt.Choice.Item> = items.flatMap { listOf(it) + flatten(it.items) }

private fun rows(items: List<PagePrompt.Choice.Item>, depth: Int): List<Pair<PagePrompt.Choice.Item, Int>> =
    items.flatMap { listOf(it to depth) + rows(it.items, depth + 1) }

/**
 * A date or time for a form field. A date-and-time field asks for the date, then the time. A
 * month or week field takes the month or week of the date chosen. "Clear" empties the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(prompt: PagePrompt.DateTime, close: () -> Unit, dismiss: () -> Unit) {
    val answer: (String) -> Unit = { prompt.answer(it); close() }
    val kind = prompt.kind
    val initial = remember(prompt) { parseInitial(prompt) }
    var pickedDate by remember(prompt) { mutableStateOf<LocalDate?>(null) }
    val needsDate = kind != PagePrompt.DateTime.Kind.Time
    if (needsDate && pickedDate == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.first?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.testTag("page-date"),
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    when (kind) {
                        PagePrompt.DateTime.Kind.Date -> answer(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        // The form's value is in ASCII digits whatever the device's language writes.
                        PagePrompt.DateTime.Kind.Month -> answer(String.format(Locale.ROOT, "%04d-%02d", date.year, date.monthValue))
                        PagePrompt.DateTime.Kind.Week -> answer(
                            String.format(Locale.ROOT, "%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                        )
                        else -> pickedDate = date
                    }
                }) { Text(stringResource(if (kind == PagePrompt.DateTime.Kind.DateTimeLocal) R.string.next else R.string.ok)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { answer("") }) { Text(stringResource(R.string.clear)) }
                    TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) { DatePicker(state) }
        return
    }
    val time = initial.second ?: LocalTime.of(12, 0)
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("page-time"),
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                val chosen = LocalTime.of(state.hour, state.minute)
                val date = pickedDate
                if (date == null) {
                    answer(chosen.format(HOURS_MINUTES))
                } else {
                    answer(LocalDateTime.of(date, chosen).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")))
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { answer("") }) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private val HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm")

/** The field's own value, as a date and a time, where it has them. */
private fun parseInitial(prompt: PagePrompt.DateTime): Pair<LocalDate?, LocalTime?> {
    val value = prompt.value
    return runCatching {
        when (prompt.kind) {
            PagePrompt.DateTime.Kind.Date -> LocalDate.parse(value) to null
            PagePrompt.DateTime.Kind.Month -> LocalDate.parse("$value-01") to null
            PagePrompt.DateTime.Kind.Week -> null to null
            PagePrompt.DateTime.Kind.Time -> null to LocalTime.parse(value)
            PagePrompt.DateTime.Kind.DateTimeLocal -> LocalDateTime.parse(value).let { it.toLocalDate() to it.toLocalTime() }
        }
    }.getOrDefault(null to null)
}

/** A color for a form field: one of a set of swatches, or a color code. */
@Composable
private fun ColorDialog(prompt: PagePrompt.Color, close: () -> Unit, dismiss: () -> Unit) {
    var code by remember(prompt) { mutableStateOf(prompt.value.ifBlank { "#000000" }) }
    val valid = HEX.matches(code)
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("page-color"),
        title = { Text(stringResource(R.string.choose_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SWATCHES.forEach { swatch ->
                        val color = Color(swatch.toColorInt())
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .border(if (swatch.equals(code, ignoreCase = true)) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable(role = Role.Button) { code = swatch },
                        )
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    singleLine = true,
                    isError = !valid,
                    label = { Text(stringResource(R.string.color_hex)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.answer(code.lowercase()); close() }, enabled = valid) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val HEX = Regex("#[0-9a-fA-F]{6}")

private val SWATCHES = listOf(
    "#000000", "#5f6368", "#9aa0a6", "#ffffff",
    "#d93025", "#e8710a", "#f9ab00", "#1e8e3e",
    "#12b5cb", "#1a73e8", "#9334e6", "#e52592",
)
