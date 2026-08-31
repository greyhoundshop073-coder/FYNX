package com.fynx.app.ui
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
object CalendarReminderScheduler{
 private const val ACTION="com.fynx.app.CALENDAR_REMINDER"
 fun schedule(context:Context,event:FynxCalendarEvent){
  val trigger=nextOccurrence(event)?:return
  val i=Intent(context,CalendarReminderReceiver::class.java).apply{action=ACTION;putExtra("calendar_id",event.id);putExtra("calendar_title",event.title);putExtra("calendar_date",event.date);putExtra("calendar_time",event.time);putExtra("calendar_repeat",event.repeat)}
  val p=PendingIntent.getBroadcast(context,event.id.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val a=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&a.canScheduleExactAlarms())a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,p) else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,p)
 }
 fun cancel(context:Context,eventId:Long){val i=Intent(context,CalendarReminderReceiver::class.java).apply{action=ACTION};val p=PendingIntent.getBroadcast(context,eventId.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(p)}
 private fun nextOccurrence(event:FynxCalendarEvent):Long?{
  if(event.time.isBlank())return null
  return try{
   val f=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).apply{isLenient=false};val parsed=f.parse(event.date+" "+event.time)?:return null
   if(event.repeat=="None"){if(parsed.time>System.currentTimeMillis())parsed.time else null}else{
    val c=Calendar.getInstance().apply{time=parsed};val now=System.currentTimeMillis()
    while(c.timeInMillis<=now){when(event.repeat){"Daily"->c.add(Calendar.DAY_OF_YEAR,1);"Weekly"->c.add(Calendar.WEEK_OF_YEAR,1);"Monthly"->c.add(Calendar.MONTH,1);"Yearly"->c.add(Calendar.YEAR,1);else->return null}}
    c.timeInMillis
   }
  }catch(_:Exception){null}
 }
}
