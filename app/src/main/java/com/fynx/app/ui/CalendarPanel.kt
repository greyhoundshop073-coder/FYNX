package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val calendarFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

data class FynxCalendarEvent(val id: Long, val title: String, val date: String)

@Composable
fun CalendarPanel() {
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(calendarFormat.format(Calendar.getInstance().time)) }
    var title by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(emptyList<FynxCalendarEvent>()) }
    var nextId by remember { mutableLongStateOf(1L) }

    val month = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset); set(Calendar.DAY_OF_MONTH, 1) }
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.US).format(month.time)
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDay = month.get(Calendar.DAY_OF_WEEK) - 1

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { monthOffset-- }) { Text("‹") }
            Text(monthTitle, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { monthOffset++ }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it) }
        }
        Spacer(Modifier.height(6.dp))
        val cells = List(firstDay) { null } + (1..days).map { it }
        Column {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    week.forEach { day ->
                        if (day == null) Spacer(Modifier.size(40.dp)) else {
                            val date = Calendar.getInstance().apply { set(Calendar.YEAR, month.get(Calendar.YEAR)); set(Calendar.MONTH, month.get(Calendar.MONTH)); set(Calendar.DAY_OF_MONTH, day) }
                            val dateText = calendarFormat.format(date.time)
                            OutlinedButton(onClick = { selectedDate = dateText }, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) { Text(day.toString()) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Events for $selectedDate", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(title, { title = it }, label = { Text("Event") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { val clean = title.trim(); if (clean.isNotEmpty()) { events = events + FynxCalendarEvent(nextId++, clean, selectedDate); title = "" } }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events.filter { it.date == selectedDate }, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(event.title); TextButton(onClick = { events = events.filterNot { it.id == event.id } }) { Text("Delete") } } }
            }
        }
    }
}
