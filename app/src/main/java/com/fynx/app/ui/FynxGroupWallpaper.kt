package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FynxGroupWallpaperOptions=listOf("Classic","Midnight","Aurora","Sunrise")

object FynxGroupWallpaperStore {
    private const val PREFS="fynx_group_wallpapers"
    fun load(context:Context,groupId:String):String=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(groupId,"Classic")?:"Classic"
    fun save(context:Context,groupId:String,name:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(groupId,name).apply()}
}

@Composable
fun FynxGroupWallpaperBackground(groupId:String,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val name=remember(groupId){FynxGroupWallpaperStore.load(context,groupId)}
    val brush=when(name){"Midnight"->Brush.verticalGradient(listOf(Color(0xFF101522),Color(0xFF263248)));"Aurora"->Brush.verticalGradient(listOf(Color(0xFF102A2A),Color(0xFF18243B)));"Sunrise"->Brush.verticalGradient(listOf(Color(0xFF3A2A1D),Color(0xFF241D35)));else->Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.surfaceVariant))}
    Box(modifier.background(brush),content=content)
}

@Composable
fun FynxGroupWallpaperDialog(groupId:String,onDismiss:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    var selected by remember(groupId){mutableStateOf(FynxGroupWallpaperStore.load(context,groupId))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Group wallpaper")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FynxGroupWallpaperOptions.forEach{name->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name);RadioButton(selected==name,onClick={selected=name;FynxGroupWallpaperStore.save(context,groupId,name)})}}}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
