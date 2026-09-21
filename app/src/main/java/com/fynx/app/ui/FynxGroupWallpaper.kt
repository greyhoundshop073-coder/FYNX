package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val FynxGroupWallpaperOptions=listOf("Classic","Midnight","Aurora","Sunrise")

object FynxGroupWallpaperStore {
    private const val PREFS="fynx_group_wallpapers"

    private fun accountKey(context: Context): String =
        FynxAuthStore.accountStorageKey(context)?.let(::storageKey) ?: "signed_out"

    private fun key(context: Context, groupId: String) = "${accountKey(context)}_${groupId.trim()}"

    fun load(context:Context,groupId:String):String=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key(context,groupId),"Classic")?:"Classic"
    fun save(context:Context,groupId:String,name:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key(context,groupId),name).apply()}

    private fun storageKey(value: String): String = value.map { character ->
        when {
            character.isLetterOrDigit() -> character
            else -> '_'
        }
    }.joinToString("").take(80).ifBlank { "account" }
}

@Composable
fun FynxGroupWallpaperBackground(groupId:String,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val name=remember(groupId){FynxGroupWallpaperStore.load(context,groupId)}
    val base=when(name){
        "Midnight"->Color(0xFF171A20)
        "Aurora"->Color(0xFF182323)
        "Sunrise"->Color(0xFF211D21)
        else->Color(0xFF202326)
    }
    Box(modifier.background(base)){
        if(name=="Classic") FynxGroupDoodlePattern()
        content()
    }
}

@Composable
private fun FynxGroupDoodlePattern(){
    Canvas(Modifier.fillMaxSize()){
        val ink=MaterialTheme.colorScheme.onSurface.copy(alpha=0.035f)
        val sw=1.15.dp.toPx()
        val cellW=190.dp.toPx()
        val cellH=165.dp.toPx()
        fun line(a:Offset,b:Offset)=drawLine(ink,a,b,sw)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(color=ink,radius=r,center=Offset(x,y),style=Stroke(width=sw))
        fun people(x:Float,y:Float,s:Float){
            circle(x,y,9*s); circle(x+28*s,y+3*s,8*s)
            line(Offset(x,y+9*s),Offset(x,y+30*s)); line(Offset(x+28*s,y+11*s),Offset(x+28*s,y+30*s))
            line(Offset(x,y+16*s),Offset(x-13*s,y+25*s)); line(Offset(x,y+16*s),Offset(x+13*s,y+25*s))
            line(Offset(x+28*s,y+17*s),Offset(x+17*s,y+24*s)); line(Offset(x+28*s,y+17*s),Offset(x+39*s,y+24*s))
            line(Offset(x,y+30*s),Offset(x-9*s,y+43*s)); line(Offset(x,y+30*s),Offset(x+9*s,y+43*s))
            line(Offset(x+28*s,y+30*s),Offset(x+20*s,y+43*s)); line(Offset(x+28*s,y+30*s),Offset(x+36*s,y+43*s))
        }
        fun bubble(x:Float,y:Float,s:Float){
            drawRoundRect(color=ink,topLeft=Offset(x,y),size=Size(54*s,36*s),cornerRadius=CornerRadius(11*s,11*s),style=Stroke(width=sw))
            line(Offset(x+12*s,y+36*s),Offset(x+7*s,y+46*s)); line(Offset(x+20*s,y+13*s),Offset(x+35*s,y+13*s))
        }
        fun link(x:Float,y:Float,s:Float){ circle(x,y,9*s); circle(x+31*s,y+20*s,9*s); line(Offset(x+7*s,y+7*s),Offset(x+24*s,y+13*s)) }
        fun handshake(x:Float,y:Float,s:Float){
            line(Offset(x,y+14*s),Offset(x+17*s,y)); line(Offset(x+17*s,y),Offset(x+34*s,y+14*s))
            line(Offset(x+9*s,y+18*s),Offset(x+20*s,y+29*s)); line(Offset(x+20*s,y+29*s),Offset(x+31*s,y+18*s)); line(Offset(x+20*s,y+29*s),Offset(x+27*s,y+36*s))
        }
        fun camera(x:Float,y:Float,s:Float){
            drawRoundRect(color=ink,topLeft=Offset(x,y),size=Size(54*s,38*s),cornerRadius=CornerRadius(7*s,7*s),style=Stroke(width=sw))
            circle(x+27*s,y+19*s,10*s); line(Offset(x+11*s,y),Offset(x+19*s,y-8*s))
        }
        fun house(x:Float,y:Float,s:Float){
            line(Offset(x,y+22*s),Offset(x+27*s,y)); line(Offset(x+27*s,y),Offset(x+54*s,y+22*s))
            line(Offset(x+5*s,y+19*s),Offset(x+5*s,y+52*s)); line(Offset(x+49*s,y+19*s),Offset(x+49*s,y+52*s)); line(Offset(x+5*s,y+52*s),Offset(x+49*s,y+52*s))
            drawRoundRect(color=ink,topLeft=Offset(x+22*s,y+35*s),size=Size(10*s,17*s),cornerRadius=CornerRadius(2*s,2*s),style=Stroke(width=sw))
        }
        fun groupCircle(x:Float,y:Float,s:Float){
            circle(x,y,25*s); circle(x-10*s,y-5*s,5*s); circle(x+10*s,y-5*s,5*s); circle(x,y+9*s,5*s)
            line(Offset(x-5*s,y-1*s),Offset(x-1*s,y+4*s)); line(Offset(x+5*s,y-1*s),Offset(x+1*s,y+4*s))
        }
        fun coffee(x:Float,y:Float,s:Float){
            drawRoundRect(color=ink,topLeft=Offset(x,y),size=Size(40*s,34*s),cornerRadius=CornerRadius(5*s,5*s),style=Stroke(width=sw))
            drawArc(color=ink,startAngle=-90f,sweepAngle=180f,useCenter=false,topLeft=Offset(x+34*s,y+8*s),size=Size(16*s,17*s),style=Stroke(width=sw))
            line(Offset(x+9*s,y-7*s),Offset(x+6*s,y-13*s)); line(Offset(x+20*s,y-7*s),Offset(x+23*s,y-13*s))
        }
        fun soccer(x:Float,y:Float,s:Float){
            circle(x+25*s,y+25*s,24*s)
            line(Offset(x+25*s,y+10*s),Offset(x+14*s,y+18*s)); line(Offset(x+25*s,y+10*s),Offset(x+36*s,y+18*s))
            line(Offset(x+14*s,y+18*s),Offset(x+18*s,y+31*s)); line(Offset(x+36*s,y+18*s),Offset(x+32*s,y+31*s)); line(Offset(x+18*s,y+31*s),Offset(x+32*s,y+31*s))
        }
        val icons=listOf<(Float,Float)->Unit>({x,y->people(x,y,.85f)},{x,y->bubble(x,y,.82f)},{x,y->link(x,y,.9f)},{x,y->handshake(x,y,.82f)},{x,y->groupCircle(x,y,.82f)},{x,y->camera(x,y,.88f)},{x,y->house(x,y,.86f)},{x,y->coffee(x,y,.92f)},{x,y->soccer(x,y,.82f)})
        var row=0; var y=-55f
        while(y<size.height+cellH){
            var col=0; var x=if(row%2==0)-38f else -133f
            while(x<size.width+cellW){ icons[(row*3+col*5)%icons.size](x+(col%3)*9f,y+(row%2)*7f); x+=cellW; col++ }
            y+=cellH; row++
        }
    }
}

@Composable
fun FynxGroupWallpaperDialog(groupId:String,onDismiss:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    var selected by remember(groupId){mutableStateOf(FynxGroupWallpaperStore.load(context,groupId))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Group wallpaper")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FynxGroupWallpaperOptions.forEach{name->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name);RadioButton(selected==name,onClick={selected=name;FynxGroupWallpaperStore.save(context,groupId,name)})}}}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
