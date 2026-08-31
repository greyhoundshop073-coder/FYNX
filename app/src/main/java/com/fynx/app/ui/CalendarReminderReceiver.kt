package com.fynx.app.ui
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fynx.app.MainActivity
class CalendarReminderReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val title=intent.getStringExtra("calendar_title")?:"Calendar event";val id=intent.getLongExtra("calendar_id",System.currentTimeMillis())
  val date=intent.getStringExtra("calendar_date")?:return;val time=intent.getStringExtra("calendar_time")?:return;val repeat=intent.getStringExtra("calendar_repeat")?:"None"
  val channel="fynx_calendar_reminders";val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  nm.createNotificationChannel(NotificationChannel(channel,"Calendar reminders",NotificationManager.IMPORTANCE_DEFAULT))
  val pi=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  nm.notify(id.hashCode(),NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("FYNX calendar reminder").setContentText(title).setContentIntent(pi).setAutoCancel(true).build())
  if(repeat!="None")CalendarReminderScheduler.schedule(context,FynxCalendarEvent(id,title,date,time,"",repeat))
 }
}
