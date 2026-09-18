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
        val ink=Color.White.copy(alpha=0.052f)
        val sw=1.1.dp.toPx()
        val w=210.dp.toPx()
        val h=175.dp.toPx()

        fun line(a:Offset,b:Offset)=drawLine(ink,a,b,sw)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(color=ink,radius=r,center=Offset(x,y),style=Stroke(width=sw))
        fun person(x:Float,y:Float,s:Float){
            circle(x,y,7*s)
            line(Offset(x,y+7*s),Offset(x,y+27*s))
            line(Offset(x,y+13*s),Offset(x-10*s,y+21*s))
            line(Offset(x,y+13*s),Offset(x+10*s,y+21*s))
            line(Offset(x,y+27*s),Offset(x-8*s,y+38*s))
            line(Offset(x,y+27*s),Offset(x+8*s,y+38*s))
        }
        fun peopleTogether(x:Float,y:Float,s:Float){
            person(x,y,s)
            person(x+28*s,y+5*s,s*.92f)
            person(x+55*s,y+1*s,s*.88f)
            line(Offset(x+10*s,y+21*s),Offset(x+20*s,y+24*s))
            line(Offset(x+36*s,y+24*s),Offset(x+47*s,y+21*s))
        }
        fun bubble(x:Float,y:Float,s:Float){
            drawRoundRect(color=ink,topLeft=Offset(x,y),size=Size(42*s,28*s),cornerRadius=CornerRadius(9*s,9*s),style=Stroke(width=sw))
            line(Offset(x+8*s,y+28*s),Offset(x+5*s,y+36*s))
        }
        fun link(x:Float,y:Float,s:Float){
            circle(x,y,7*s);circle(x+28*s,y+18*s,7*s)
            line(Offset(x+5*s,y+5*s),Offset(x+23*s,y+13*s))
        }
        fun handshake(x:Float,y:Float,s:Float){
            line(Offset(x,y+10*s),Offset(x+15*s,y))
            line(Offset(x+15*s,y),Offset(x+30*s,y+10*s))
            line(Offset(x+8*s,y+13*s),Offset(x+18*s,y+23*s))
            line(Offset(x+18*s,y+23*s),Offset(x+28*s,y+13*s))
            line(Offset(x+18*s,y+23*s),Offset(x+24*s,y+29*s))
        }
        fun camera(x:Float,y:Float,s:Float){
            drawRect(color=ink,topLeft=Offset(x,y),size=Size(42*s,30*s),style=Stroke(width=sw))
            circle(x+21*s,y+15*s,7*s)
            line(Offset(x+8*s,y),Offset(x+14*s,y-6*s))
        }
        fun house(x:Float,y:Float,s:Float){
            line(Offset(x,y+18*s),Offset(x+21*s,y))
            line(Offset(x+21*s,y),Offset(x+42*s,y+18*s))
            line(Offset(x,y+18*s),Offset(x,y+43*s))
            line(Offset(x+42*s,y+18*s),Offset(x+42*s,y+43*s))
            line(Offset(x,y+43*s),Offset(x+42*s,y+43*s))
        }
        fun groupCircle(x:Float,y:Float,s:Float){
            circle(x,y,20*s)
            circle(x-8*s,y-4*s,4*s);circle(x+8*s,y-4*s,4*s);circle(x,y+7*s,4*s)
            line(Offset(x-4*s,y-1*s),Offset(x-1*s,y+3*s))
            line(Offset(x+4*s,y-1*s),Offset(x+1*s,y+3*s))
        }

        var row=0
        var y=-30f
        while(y<size.height+h){
            var col=0
            var x=if(row%2==0)-45f else -145f
            while(x<size.width+w){
                when((row*7+col)%9){
                    0->peopleTogether(x,y+30,.52f)
                    1->bubble(x+18,y+50,.72f)
                    2->link(x+20,y+35,.8f)
                    3->handshake(x+18,y+35,.72f)
                    4->groupCircle(x+38,y+50,.78f)
                    5->camera(x+24,y+18,.68f)
                    6->house(x+10,y+22,.68f)
                    7->{ person(x+34,y+35,.62f); bubble(x+52,y+18,.55f) }
                    else->{ peopleTogether(x+4,y+25,.44f); line(Offset(x+20,y+58),Offset(x+52,y+38)) }
                }
                x+=w
                col++
            }
            y+=h
            row++
        }
    }
}

@Composable
fun FynxGroupWallpaperDialog(groupId:String,onDismiss:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    var selected by remember(groupId){mutableStateOf(FynxGroupWallpaperStore.load(context,groupId))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Group wallpaper")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FynxGroupWallpaperOptions.forEach{name->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name);RadioButton(selected==name,onClick={selected=name;FynxGroupWallpaperStore.save(context,groupId,name)})}}}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
