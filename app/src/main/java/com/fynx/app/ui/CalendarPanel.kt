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
private val calendarFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US)
private const val PREFS="fynx_calendar_events"
private const val KEY_EVENTS="events"
data class FynxCalendarEvent(val id:Long,val title:String,val date:String,val time:String="",val notes:String="",val repeat:String="None")
private fun encode(events:List<FynxCalendarEvent>)=events.joinToString("\n"){listOf(it.id,it.title,it.date,it.time,it.notes,it.repeat).joinToString("|"){v->v.toString().replace("|","/").replace("\n"," ")}}
private fun decode(raw:String)=raw.replace("\\n","\n").lineSequence().mapNotNull{line->val x=line.split("|",limit=6);when{x.size==6->x[0].toLongOrNull()?.let{FynxCalendarEvent(it,x[1],x[2],x[3],x[4],x[5])};x.size==5->x[0].toLongOrNull()?.let{FynxCalendarEvent(it,x[1],x[2],x[3],x[4])};else->null}}.toList()
private fun save(c:Context,e:List<FynxCalendarEvent>)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_EVENTS,encode(e)).apply()
@Composable fun CalendarPanel(){
 val c=LocalContext.current;var offset by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf(calendarFormat.format(Calendar.getInstance().time))}
 var title by remember{mutableStateOf("")};var time by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var repeat by remember{mutableStateOf("None")}
 var events by remember{mutableStateOf(decode(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_EVENTS,"")?:""))};var nextId by remember{mutableLongStateOf((events.maxOfOrNull{it.id}?:0)+1)}
 var editing by remember{mutableStateOf<FynxCalendarEvent?>(null)}
 if(editing!=null){EventEditor(editing!!,{editing=null}){u->CalendarReminderScheduler.cancel(c,u.id);events=events.map{if(it.id==u.id)u else it};save(c,events);CalendarReminderScheduler.schedule(c,u);selected=u.date;editing=null};return}
 val month=Calendar.getInstance().apply{add(Calendar.MONTH,offset);set(Calendar.DAY_OF_MONTH,1)}
 val cells=List(month.get(Calendar.DAY_OF_WEEK)-1){null}+(1..month.getActualMaximum(Calendar.DAY_OF_MONTH)).map{it}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({offset--}){Text("‹")};Text(SimpleDateFormat("MMMM yyyy",Locale.US).format(month.time),style=MaterialTheme.typography.titleLarge);TextButton({offset++}){Text("›")}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("S","M","T","W","T","F","S").forEach{Text(it)}}
  cells.chunked(7).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){week.forEach{day->if(day==null)Spacer(Modifier.size(40.dp))else{val d=Calendar.getInstance().apply{set(month.get(Calendar.YEAR),month.get(Calendar.MONTH),day)};val ds=calendarFormat.format(d.time);OutlinedButton({selected=ds},Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){Text(day.toString())}}}}}
  Spacer(Modifier.height(10.dp));Text("Events for "+selected,style=MaterialTheme.typography.titleMedium)
  OutlinedTextField(title,{title=it},label={Text("Event title")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(Modifier.fillMaxWidth()){OutlinedTextField(time,{time=it},label={Text("Time")},singleLine=true,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));Button({val t=title.trim();if(t.isNotEmpty()){val e=FynxCalendarEvent(nextId++,t,selected,time.trim(),notes.trim(),repeat);events=events+e;save(c,events);CalendarReminderScheduler.schedule(c,e);title="";time="";notes="";repeat="None"}}){Text("Add")}}
  OutlinedTextField(notes,{notes=it},label={Text("Details / notes")},modifier=Modifier.fillMaxWidth(),minLines=2)
  Row{Text("Repeat: "+repeat);TextButton({repeat=when(repeat){"None"->"Daily";"Daily"->"Weekly";"Weekly"->"Monthly";"Monthly"->"Yearly";else->"None"}}){Text("Change")}}
  LazyColumn{items(events.filter{it.date==selected},key={it.id}){e->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(e.title,style=MaterialTheme.typography.titleMedium);if(e.time.isNotBlank())Text(e.time);if(e.repeat!="None")Text("Repeats "+e.repeat.lowercase());if(e.notes.isNotBlank())Text(e.notes);Row{TextButton({editing=e}){Text("Edit")};TextButton({CalendarReminderScheduler.cancel(c,e.id);events=events.filterNot{it.id==e.id};save(c,events)}){Text("Delete")}}}}}}
 }
}
@Composable private fun EventEditor(e:FynxCalendarEvent,onCancel:()->Unit,onSave:(FynxCalendarEvent)->Unit){
 var title by remember(e){mutableStateOf(e.title)};var date by remember(e){mutableStateOf(e.date)};var time by remember(e){mutableStateOf(e.time)};var notes by remember(e){mutableStateOf(e.notes)};var repeat by remember(e){mutableStateOf(e.repeat)}
 Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({onCancel()}){Text("Cancel")};Text("Edit event",style=MaterialTheme.typography.titleLarge);TextButton({onSave(e.copy(title=title.trim().ifEmpty{e.title},date=date.trim().ifEmpty{e.date},time=time.trim(),notes=notes.trim(),repeat=repeat))}){Text("Save")}};HorizontalDivider();OutlinedTextField(title,{title=it},label={Text("Event title")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it},label={Text("Date")},modifier=Modifier.fillMaxWidth());OutlinedTextField(time,{time=it},label={Text("Time")},modifier=Modifier.fillMaxWidth());OutlinedTextField(notes,{notes=it},label={Text("Details / notes")},modifier=Modifier.fillMaxWidth(),minLines=3);Row{Text("Repeat: "+repeat);TextButton({repeat=when(repeat){"None"->"Daily";"Daily"->"Weekly";"Weekly"->"Monthly";"Monthly"->"Yearly";else->"None"}}){Text("Change")}}}
}
