package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val calendarFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private const val PREFS = "fynx_calendar_events"
private const val KEY_EVENTS = "events"

data class FynxCalendarEvent(val id: Long, val title: String, val date: String, val time: String = "", val notes: String = "")

private fun encode(events: List<FynxCalendarEvent>): String = events.joinToString("\\n") { listOf(it.id, it.title.replace("|", "/"), it.date, it.time.replace("|", "/"), it.notes.replace("|", "/")).joinToString("|") }
private fun decode(raw: String): List<FynxCalendarEvent> = raw.lineSequence().mapNotNull { line -> val p = line.split("|", limit = 5); if (p.size == 5) p[0].toLongOrNull()?.let { FynxCalendarEvent(it, p[1], p[2], p[3], p[4]) } else null }.toList()
private fun saveEvents(context: Context, events: List<FynxCalendarEvent>) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, encode(events)).apply() }

@Composable
fun CalendarPanel() {
    val context = LocalContext.current
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(calendarFormat.format(Calendar.getInstance().time)) }
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, "") ?: "")) }
    var nextId by remember { mutableLongStateOf((events.maxOfOrNull { it.id } ?: 0L) + 1L) }
    var editing by remember { mutableStateOf<FynxCalendarEvent?>(null) }

    if (editing != null) { EventEditor(editing!!, { editing = null }) { updated -> events = events.map { if (it.id == updated.id) updated else it }; saveEvents(context, events); selectedDate = updated.date; editing = null }; return }

    val month = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset); set(Calendar.DAY_OF_MONTH, 1) }
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.US).format(month.time)
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDay = month.get(Calendar.DAY_OF_WEEK) - 1
    val cells = List(firstDay) { null } + (1..days).map { it }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { monthOffset-- }) { Text("‹") }; Text(monthTitle, style = MaterialTheme.typography.titleLarge); TextButton(onClick = { monthOffset++ }) { Text("›") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it) } }
        Spacer(Modifier.height(6.dp))
        Column { cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { week.forEach { day -> if (day == null) Spacer(Modifier.size(40.dp)) else { val date = Calendar.getInstance().apply { set(Calendar.YEAR, month.get(Calendar.YEAR)); set(Calendar.MONTH, month.get(Calendar.MONTH)); set(Calendar.DAY_OF_MONTH, day) }; val dateText = calendarFormat.format(date.time); OutlinedButton(onClick = { selectedDate = dateText }, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) { Text(day.toString()) } } } } } }
        Spacer(Modifier.height(12.dp)); Text("Events for $selectedDate", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(title, { title = it }, label = { Text("Event title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) { OutlinedTextField(time, { time = it }, label = { Text("Time") }, placeholder = { Text("e.g. 14:30") }, singleLine = true, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(onClick = { val clean = title.trim(); if (clean.isNotEmpty()) { val event = FynxCalendarEvent(nextId++, clean, selectedDate, time.trim(), notes.trim()); events = events + event; saveEvents(context, events); title = ""; time = ""; notes = "" } }) { Text("Add") } }
        OutlinedTextField(notes, { notes = it }, label = { Text("Details / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(events.filter { it.date == selectedDate }, key = { it.id }) { event -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text(event.title, style = MaterialTheme.typography.titleMedium); if (event.time.isNotBlank()) Text(event.time, style = MaterialTheme.typography.labelMedium); if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodyMedium); Row { TextButton(onClick = { editing = event }) { Text("Edit") }; TextButton(onClick = { events = events.filterNot { it.id == event.id }; saveEvents(context, events) }) { Text("Delete") } } } } } }
    }
}

@Composable
private fun EventEditor(event: FynxCalendarEvent, onCancel: () -> Unit, onSave: (FynxCalendarEvent) -> Unit) {
    var title by remember(event) { mutableStateOf(event.title) }; var date by remember(event) { mutableStateOf(event.date) }; var time by remember(event) { mutableStateOf(event.time) }; var notes by remember(event) { mutableStateOf(event.notes) }
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = onCancel) { Text("Cancel") }; Text("Edit event", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { onSave(event.copy(title = title.trim().ifEmpty { event.title }, date = date.trim().ifEmpty { event.date }, time = time.trim(), notes = notes.trim())) }) { Text("Save") } }; HorizontalDivider(); Spacer(Modifier.height(16.dp)); OutlinedTextField(title, { title = it }, label = { Text("Event title") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(date, { date = it }, label = { Text("Date") }, placeholder = { Text("yyyy-MM-dd") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(time, { time = it }, label = { Text("Time") }, placeholder = { Text("e.g. 14:30") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(notes, { notes = it }, label = { Text("Details / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
}
