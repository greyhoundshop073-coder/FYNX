package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun GroupChatPanel(group:GroupChat,currentUsername:String,onBack:()->Unit,onGroupChanged:(GroupChat)->Unit={}){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val isAdmin=group.isAdmin(currentUsername)
    var newMember by remember{mutableStateOf("")};var description by remember{mutableStateOf(group.description)};var text by remember{mutableStateOf("")}
    var messages by remember(group.id){mutableStateOf(loadGroupMessages(context,group.id))};var attachment by remember{mutableStateOf<Uri?>(null)};var attachmentType by remember{mutableStateOf("image")};var showCamera by remember{mutableStateOf(false)};var showWallpaper by remember{mutableStateOf(false)}
    var recording by remember{mutableStateOf<MediaRecorder?>(null)};var recordingFile by remember{mutableStateOf<File?>(null)};var recordingStarted by remember{mutableLongStateOf(0L)};var elapsed by remember{mutableLongStateOf(0L)};var isRecording by remember{mutableStateOf(false)};var syncing by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun feedback(){if(Build.VERSION.SDK_INT>=31){(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE))}else{@Suppress("DEPRECATION")(context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(35)};runCatching{ToneGenerator(AudioManager.STREAM_NOTIFICATION,60).startTone(ToneGenerator.TONE_PROP_ACK,70)}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){attachment=uri;attachmentType=if(context.contentResolver.getType(uri)?.startsWith("video/")==true)"video" else "image"}}
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){val file=File(context.cacheDir,"fynx_group_voice_${System.currentTimeMillis()}.m4a");runCatching{createCompatibleMediaRecorder(context).apply{setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setOutputFile(file.absolutePath);prepare();start();recording=this;recordingFile=file;recordingStarted=System.currentTimeMillis();elapsed=0;isRecording=true}}.onFailure{error=it.message?:"Microphone unavailable"}}}
    LaunchedEffect(isRecording,recordingStarted){while(isRecording){elapsed=(System.currentTimeMillis()-recordingStarted).coerceAtLeast(0);kotlinx.coroutines.delay(150)}}
    LaunchedEffect(group.id){
        if(FynxBackendClient.hasAccessToken(context)){
            syncing=true
            if(isAdmin){
                val remoteGroup=FynxGroup(group.id,group.name,group.description,FynxGroupVisibility.PRIVATE,group.adminUsernames.firstOrNull()?:currentUsername,group.memberUsernames.map{FynxGroupMember(it,if(it in group.adminUsernames)FynxGroupRole.ADMIN else FynxGroupRole.MEMBER)})
                FynxGroupRemoteClient.syncGroup(context,remoteGroup).onFailure{error=it.message}
            }
            FynxGroupRemoteClient.loadMessages(context,group.id).onSuccess{remote->messages=remote.map{FynxGroupRemoteClient.toChatMessage(it,currentUsername,FynxBackendClient.baseUrl(context))};saveGroupMessages(context,group.id,messages)}.onFailure{error=it.message};syncing=false
        }
    }
    fun sendMessage(message:ChatMessage){messages=messages+message;saveGroupMessages(context,group.id,messages);feedback();scope.launch{FynxGroupRemoteClient.sendMessage(context,group.id,message).onSuccess{server->messages=messages.map{if(it.id==message.id)FynxGroupRemoteClient.toChatMessage(server,currentUsername,FynxBackendClient.baseUrl(context))else it};saveGroupMessages(context,group.id,messages);error=null}.onFailure{e->messages=messages.filterNot{it.id==message.id};saveGroupMessages(context,group.id,messages);error=e.message?:"Message could not be sent."}}}
    fun stopVoice(){val r=recording?:return;val file=recordingFile;val duration=System.currentTimeMillis()-recordingStarted;runCatching{r.stop()};r.release();recording=null;recordingFile=null;isRecording=false;elapsed=0;if(file!=null&&file.exists()&&file.length()>0&&duration>=300)sendMessage(ChatMessage("Voice message",true,UUID.randomUUID().toString(),delivered=true,read=true,attachmentUri=Uri.fromFile(file).toString(),attachmentType="audio"))else file?.delete()}
    fun send(){if(text.isBlank()&&attachment==null)return;val a=attachment;val m=ChatMessage(text.trim().ifBlank{if(attachmentType=="video")"Video" else "Photo"},true,UUID.randomUUID().toString(),delivered=true,read=true,attachmentUri=a?.toString(),attachmentType=if(a==null)null else attachmentType);text="";attachment=null;sendMessage(m)}
    FynxGroupWallpaperBackground(group.id,Modifier.fillMaxSize()){Column(Modifier.fillMaxSize()){
        Surface(tonalElevation=3.dp){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹")};FynxAvatar(group.name,Modifier.size(42.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(group.name,style=MaterialTheme.typography.titleMedium);Text("${group.memberUsernames.size} members${if(syncing)" • Syncing…" else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={showWallpaper=true}){Icon(Icons.Default.Wallpaper,"Group wallpaper")}}}
        error?.let{Text(it,Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=10.dp)){items(messages,key={it.id}){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.fromMe)Arrangement.End else Arrangement.Start){Surface(color=if(m.fromMe)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(18.dp),modifier=Modifier.widthIn(max=330.dp)){Column(Modifier.padding(10.dp)){if(m.attachmentUri!=null&&m.attachmentType!="audio")FynxRemoteMedia(m.attachmentUri!!,m.attachmentType?:"image",Modifier.sizeIn(maxWidth=290.dp,maxHeight=240.dp));if(m.attachmentType=="audio")FynxRemoteAudio(m.attachmentUri?:m.voiceUri.orEmpty(),Modifier.fillMaxWidth());if(m.text.isNotBlank()&&m.attachmentType!="audio")Text(m.text);if(m.fromMe)Text("✓✓",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}
        Surface(tonalElevation=3.dp,modifier=Modifier.navigationBarsPadding().imePadding()){Column(Modifier.fillMaxWidth().padding(8.dp)){if(attachment!=null)Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(bottom=5.dp)){Icon(if(attachmentType=="video")Icons.Default.Videocam else Icons.Default.Image,null);Text(if(attachmentType=="video")"Video ready"else"Photo ready",Modifier.weight(1f));IconButton(onClick={attachment=null}){Icon(Icons.Default.Close,"Remove")}}
            if(isRecording)Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(bottom=6.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text("●",color=MaterialTheme.colorScheme.error);Spacer(Modifier.width(8.dp));Text("Recording ${elapsed/1000}s",Modifier.weight(1f));TextButton(onClick={recording?.release();recording=null;recordingFile?.delete();recordingFile=null;isRecording=false}){Text("Cancel")};Button(onClick={stopVoice()}){Text("Send")}}}
            else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={picker.launch("image/* video/*")}){Icon(Icons.Default.AttachFile,"Attach media")};IconButton(onClick={showCamera=true}){Icon(Icons.Default.PhotoCamera,"FYNX camera")};OutlinedTextField(text,{text=it.take(4000)},Modifier.weight(1f),placeholder={Text("Message group…")},singleLine=true);IconButton(onClick={micPermission.launch(Manifest.permission.RECORD_AUDIO)}){Icon(Icons.Default.Mic,"Record voice")};IconButton(onClick={send()},enabled=text.isNotBlank()||attachment!=null){Icon(Icons.Default.Send,"Send")}}
        }}
        if(isAdmin)Surface(tonalElevation=1.dp){Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(description,{description=it;onGroupChanged(group.copy(description=it))},Modifier.weight(1f),singleLine=true,label={Text("Group description")});OutlinedTextField(newMember,{newMember=it},Modifier.weight(1f),singleLine=true,label={Text("Add username")});Button(enabled=newMember.isNotBlank(),onClick={onGroupChanged(group.addMember(newMember.trim()));newMember=""}){Text("Add")}}}
    }}
    if(showCamera)Dialog(onDismissRequest={showCamera=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){FynxCameraCapturePanel(onCaptured={uri,type->attachment=uri;attachmentType=type;showCamera=false},onDismiss={showCamera=false})}}
    if(showWallpaper)FynxGroupWallpaperDialog(group.id){showWallpaper=false}
}

private fun saveGroupMessages(context:Context,groupId:String,messages:List<ChatMessage>){val a=org.json.JSONArray();messages.takeLast(100).forEach{m->a.put(org.json.JSONObject().apply{put("id",m.id);put("text",m.text);put("fromMe",m.fromMe);put("timestamp",m.timestamp);put("delivered",m.delivered);put("read",m.read);put("attachmentUri",m.attachmentUri?:"");put("attachmentType",m.attachmentType?:"");put("voiceUri",m.voiceUri?:"");put("voiceDurationMs",m.voiceDurationMs)})};context.getSharedPreferences("fynx_group_messages",Context.MODE_PRIVATE).edit().putString(groupId,a.toString()).apply()}
private fun loadGroupMessages(context:Context,groupId:String):List<ChatMessage>{val raw=context.getSharedPreferences("fynx_group_messages",Context.MODE_PRIVATE).getString(groupId,null)?:return emptyList();return runCatching{val a=org.json.JSONArray(raw);List(a.length()){i->val o=a.getJSONObject(i);ChatMessage(o.optString("text"),o.optBoolean("fromMe"),o.optString("id"),o.optLong("timestamp"),o.optBoolean("delivered"),o.optBoolean("read"),attachmentUri=o.optString("attachmentUri").ifBlank{null},attachmentType=o.optString("attachmentType").ifBlank{null},voiceUri=o.optString("voiceUri").ifBlank{null},voiceDurationMs=o.optLong("voiceDurationMs"))}}.getOrElse{emptyList()}}
private fun createCompatibleMediaRecorder(context:Context):MediaRecorder=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context)else@Suppress("DEPRECATION") MediaRecorder()
