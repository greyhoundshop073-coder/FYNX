package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FynxChatWallpaperOptions = listOf("FYNX Default", "Midnight", "Aurora", "Sunrise", "Ocean", "Minimal")

@Composable
fun FynxChatWallpaperBackground(modifier: Modifier = Modifier, wallpaperOverride: String? = null, content: @Composable BoxScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaper = wallpaperOverride ?: FynxPreferencesStore.loadChatWallpaper(context)
    val base = when (wallpaper) {
        "Midnight" -> Color(0xFF171A20)
        "Aurora" -> Color(0xFF182323)
        "Sunrise" -> Color(0xFF211D21)
        "Ocean" -> Color(0xFF17242B)
        "Minimal" -> MaterialTheme.colorScheme.background
        else -> Color(0xFF202326)
    }
    Box(modifier.background(base)) {
        if (wallpaper == "FYNX Default") FynxChatDoodlePattern()
        content()
    }
}

@Composable
fun FynxChatDoodlePattern() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val ink = Color.White.copy(alpha = 0.035f)
        val sw = 1.15.dp.toPx()
        val cellW = 190.dp.toPx()
        val cellH = 165.dp.toPx()
        fun line(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) = drawLine(ink, a, b, sw)
        fun circle(x: Float, y: Float, r: Float) = drawCircle(ink, r, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
        fun bubble(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Size(54*s,36*s), androidx.compose.ui.geometry.CornerRadius(11*s,11*s), style=androidx.compose.ui.graphics.drawscope.Stroke(width=sw))
            line(androidx.compose.ui.geometry.Offset(x+12*s,y+36*s), androidx.compose.ui.geometry.Offset(x+7*s,y+46*s))
            line(androidx.compose.ui.geometry.Offset(x+20*s,y+13*s), androidx.compose.ui.geometry.Offset(x+35*s,y+13*s))
        }
        fun camera(x: Float,y: Float,s: Float) {
            drawRoundRect(ink, androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Size(54*s,38*s), androidx.compose.ui.geometry.CornerRadius(7*s,7*s), style=androidx.compose.ui.graphics.drawscope.Stroke(width=sw))
            circle(x+27*s,y+19*s,10*s)
            line(androidx.compose.ui.geometry.Offset(x+11*s,y), androidx.compose.ui.geometry.Offset(x+19*s,y-8*s))
        }
        fun bicycle(x: Float,y: Float,s: Float) {
            circle(x,y,15*s); circle(x+50*s,y,15*s)
            line(androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Offset(x+22*s,y-24*s))
            line(androidx.compose.ui.geometry.Offset(x+22*s,y-24*s),androidx.compose.ui.geometry.Offset(x+50*s,y))
            line(androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Offset(x+40*s,y))
            line(androidx.compose.ui.geometry.Offset(x+22*s,y-24*s),androidx.compose.ui.geometry.Offset(x+31*s,y-33*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y-25*s),androidx.compose.ui.geometry.Offset(x+11*s,y-34*s))
        }
        fun map(x: Float,y: Float,s: Float) {
            line(androidx.compose.ui.geometry.Offset(x,y+8*s),androidx.compose.ui.geometry.Offset(x+18*s,y))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y),androidx.compose.ui.geometry.Offset(x+38*s,y+8*s))
            line(androidx.compose.ui.geometry.Offset(x+38*s,y+8*s),androidx.compose.ui.geometry.Offset(x+58*s,y))
            line(androidx.compose.ui.geometry.Offset(x+58*s,y),androidx.compose.ui.geometry.Offset(x+58*s,y+42*s))
            line(androidx.compose.ui.geometry.Offset(x+58*s,y+42*s),androidx.compose.ui.geometry.Offset(x+38*s,y+50*s))
            line(androidx.compose.ui.geometry.Offset(x+38*s,y+50*s),androidx.compose.ui.geometry.Offset(x+18*s,y+42*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y+42*s),androidx.compose.ui.geometry.Offset(x,y+50*s))
            line(androidx.compose.ui.geometry.Offset(x,y+50*s),androidx.compose.ui.geometry.Offset(x,y+8*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y),androidx.compose.ui.geometry.Offset(x+18*s,y+42*s))
            line(androidx.compose.ui.geometry.Offset(x+38*s,y+8*s),androidx.compose.ui.geometry.Offset(x+38*s,y+50*s))
        }
        fun palette(x: Float,y: Float,s: Float) {
            circle(x+24*s,y+24*s,24*s)
            circle(x+39*s,y+12*s,4*s); circle(x+15*s,y+13*s,3*s); circle(x+11*s,y+29*s,3*s); circle(x+22*s,y+40*s,3*s)
            drawCircle(Color(0xFF202326),8*s,androidx.compose.ui.geometry.Offset(x+39*s,y+34*s))
        }
        fun lightbulb(x: Float,y: Float,s: Float) {
            circle(x+24*s,y+21*s,17*s)
            line(androidx.compose.ui.geometry.Offset(x+14*s,y+34*s),androidx.compose.ui.geometry.Offset(x+18*s,y+44*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y+44*s),androidx.compose.ui.geometry.Offset(x+30*s,y+44*s))
            line(androidx.compose.ui.geometry.Offset(x+30*s,y+44*s),androidx.compose.ui.geometry.Offset(x+34*s,y+34*s))
            line(androidx.compose.ui.geometry.Offset(x+24*s,y),androidx.compose.ui.geometry.Offset(x+24*s,y-8*s))
            line(androidx.compose.ui.geometry.Offset(x+3*s,y+9*s),androidx.compose.ui.geometry.Offset(x-3*s,y+4*s))
            line(androidx.compose.ui.geometry.Offset(x+45*s,y+9*s),androidx.compose.ui.geometry.Offset(x+51*s,y+4*s))
        }
        fun house(x: Float,y: Float,s: Float) {
            line(androidx.compose.ui.geometry.Offset(x,y+22*s),androidx.compose.ui.geometry.Offset(x+27*s,y))
            line(androidx.compose.ui.geometry.Offset(x+27*s,y),androidx.compose.ui.geometry.Offset(x+54*s,y+22*s))
            line(androidx.compose.ui.geometry.Offset(x+5*s,y+19*s),androidx.compose.ui.geometry.Offset(x+5*s,y+52*s))
            line(androidx.compose.ui.geometry.Offset(x+49*s,y+19*s),androidx.compose.ui.geometry.Offset(x+49*s,y+52*s))
            line(androidx.compose.ui.geometry.Offset(x+5*s,y+52*s),androidx.compose.ui.geometry.Offset(x+49*s,y+52*s))
            drawRoundRect(ink,androidx.compose.ui.geometry.Offset(x+22*s,y+35*s),androidx.compose.ui.geometry.Size(10*s,17*s),androidx.compose.ui.geometry.CornerRadius(2*s,2*s),style=androidx.compose.ui.graphics.drawscope.Stroke(width=sw))
        }
        fun guitar(x: Float,y: Float,s: Float) {
            circle(x+18*s,y+32*s,13*s); circle(x+34*s,y+19*s,10*s)
            line(androidx.compose.ui.geometry.Offset(x+34*s,y+10*s),androidx.compose.ui.geometry.Offset(x+34*s,y-14*s))
            line(androidx.compose.ui.geometry.Offset(x+29*s,y-14*s),androidx.compose.ui.geometry.Offset(x+39*s,y-14*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y+19*s),androidx.compose.ui.geometry.Offset(x+34*s,y+10*s))
        }
        fun rocket(x: Float,y: Float,s: Float) {
            line(androidx.compose.ui.geometry.Offset(x+20*s,y+44*s),androidx.compose.ui.geometry.Offset(x+20*s,y+4*s))
            line(androidx.compose.ui.geometry.Offset(x+20*s,y+4*s),androidx.compose.ui.geometry.Offset(x+32*s,y-9*s))
            line(androidx.compose.ui.geometry.Offset(x+32*s,y-9*s),androidx.compose.ui.geometry.Offset(x+44*s,y+4*s))
            line(androidx.compose.ui.geometry.Offset(x+44*s,y+4*s),androidx.compose.ui.geometry.Offset(x+44*s,y+44*s))
            line(androidx.compose.ui.geometry.Offset(x+20*s,y+44*s),androidx.compose.ui.geometry.Offset(x+44*s,y+44*s))
            circle(x+32*s,y+14*s,5*s)
            line(androidx.compose.ui.geometry.Offset(x+20*s,y+24*s),androidx.compose.ui.geometry.Offset(x+10*s,y+35*s))
            line(androidx.compose.ui.geometry.Offset(x+44*s,y+24*s),androidx.compose.ui.geometry.Offset(x+54*s,y+35*s))
        }
        fun soccer(x: Float,y: Float,s: Float) {
            circle(x+25*s,y+25*s,24*s)
            line(androidx.compose.ui.geometry.Offset(x+25*s,y+10*s),androidx.compose.ui.geometry.Offset(x+14*s,y+18*s))
            line(androidx.compose.ui.geometry.Offset(x+25*s,y+10*s),androidx.compose.ui.geometry.Offset(x+36*s,y+18*s))
            line(androidx.compose.ui.geometry.Offset(x+14*s,y+18*s),androidx.compose.ui.geometry.Offset(x+18*s,y+31*s))
            line(androidx.compose.ui.geometry.Offset(x+36*s,y+18*s),androidx.compose.ui.geometry.Offset(x+32*s,y+31*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y+31*s),androidx.compose.ui.geometry.Offset(x+32*s,y+31*s))
        }
        fun coffee(x: Float,y: Float,s: Float) {
            drawRoundRect(ink,androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Size(40*s,34*s),androidx.compose.ui.geometry.CornerRadius(5*s,5*s),style=androidx.compose.ui.graphics.drawscope.Stroke(width=sw))
            drawArc(ink,-90f,180f,false,androidx.compose.ui.geometry.Offset(x+34*s,y+8*s),androidx.compose.ui.geometry.Size(16*s,17*s),style=androidx.compose.ui.graphics.drawscope.Stroke(width=sw))
            line(androidx.compose.ui.geometry.Offset(x+9*s,y-7*s),androidx.compose.ui.geometry.Offset(x+6*s,y-13*s)); line(androidx.compose.ui.geometry.Offset(x+20*s,y-7*s),androidx.compose.ui.geometry.Offset(x+23*s,y-13*s))
        }
        fun flower(x: Float,y: Float,s: Float) {
            circle(x,y,6*s); circle(x,y-13*s,9*s); circle(x+13*s,y,9*s); circle(x,y+13*s,9*s); circle(x-13*s,y,9*s)
            line(androidx.compose.ui.geometry.Offset(x,y+19*s),androidx.compose.ui.geometry.Offset(x,y+43*s))
            line(androidx.compose.ui.geometry.Offset(x,y+30*s),androidx.compose.ui.geometry.Offset(x-12*s,y+25*s))
        }
        fun cloud(x: Float,y: Float,s: Float) {
            circle(x+13*s,y+18*s,11*s); circle(x+30*s,y+13*s,15*s); circle(x+48*s,y+20*s,11*s)
            line(androidx.compose.ui.geometry.Offset(x+3*s,y+27*s),androidx.compose.ui.geometry.Offset(x+58*s,y+27*s))
        }
        val icons=listOf<(Float,Float)->Unit>(
            {x,y->bicycle(x,y,.88f)},{x,y->camera(x,y,.88f)},{x,y->map(x,y,.82f)},{x,y->palette(x,y,.86f)},
            {x,y->lightbulb(x,y,.82f)},{x,y->house(x,y,.86f)},{x,y->guitar(x,y,.9f)},{x,y->rocket(x,y,.9f)},
            {x,y->soccer(x,y,.78f)},{x,y->coffee(x,y,.9f)},{x,y->flower(x,y,.9f)},{x,y->cloud(x,y,.82f)},{x,y->bubble(x,y,.82f)}
        )
        var row=0; var y=-55f
        while(y<size.height+cellH){
            var col=0; var x=if(row%2==0)-38f else -133f
            while(x<size.width+cellW){
                icons[(row*3+col*5)%icons.size](x+(col%3)*9f,y+(row%2)*7f)
                x+=cellW; col++
            }
            y+=cellH; row++
        }
    }
}

@Composable
fun FynxChatPersonalizationDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var wallpaper by remember { mutableStateOf(FynxPreferencesStore.loadChatWallpaper(context)) }
    var listView by remember { mutableStateOf(FynxPreferencesStore.loadChatListView(context)) }
    var nightMode by remember { mutableStateOf(FynxPreferencesStore.loadNightMode(context)) }
    var stickerAnimation by remember { mutableStateOf(FynxPreferencesStore.loadStickerAnimation(context)) }
    var emojiSize by remember { mutableStateOf(FynxPreferencesStore.loadEmojiSize(context)) }
    var section by remember { mutableStateOf("Appearance") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat & personalization") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = if (section == "Appearance") 0 else 1) {
                    Tab(selected = section == "Appearance", onClick = { section = "Appearance" }, text = { Text("Appearance") })
                    Tab(selected = section == "Chat", onClick = { section = "Chat" }, text = { Text("Chat") })
                }
                if (section == "Appearance") {
                    Text("Chat wallpaper", style = MaterialTheme.typography.titleMedium)
                    FynxChatWallpaperOptions.forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = wallpaper == option, onClick = { wallpaper = option; FynxPreferencesStore.saveChatWallpaper(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Automatic night mode", style = MaterialTheme.typography.titleMedium)
                    listOf("Follow system", "Off", "Scheduled").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = nightMode == option, onClick = { nightMode = option; FynxPreferencesStore.saveNightMode(context, option) })
                        }
                    }
                } else {
                    Text("Chat list view", style = MaterialTheme.typography.titleMedium)
                    listOf("Comfortable", "Compact", "Large").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = listView == option, onClick = { listView = option; FynxPreferencesStore.saveChatListView(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Stickers & Emoji", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sticker animation")
                        Switch(checked = stickerAnimation, onCheckedChange = { stickerAnimation = it; FynxPreferencesStore.saveStickerAnimation(context, it) })
                    }
                    listOf("Small", "Normal", "Large").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Emoji $option")
                            RadioButton(selected = emojiSize == option, onClick = { emojiSize = option; FynxPreferencesStore.saveEmojiSize(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    Text("English", style = MaterialTheme.typography.bodyLarge)
                    Text("Additional languages will appear here when full FYNX translations are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun AppearanceDialog(current: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("System", "Light", "Dark")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, text = {
        Column { options.forEach { option -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(option); RadioButton(selected = current == option, onClick = { onSelected(option) }) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun AccentDialog(current: FynxAccent, onSelected: (FynxAccent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Colors & accent") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            FynxAccent.entries.forEach { option ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(option.name); RadioButton(selected = current == option, onClick = { onSelected(option) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun ChatPersonalizationDialog(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Chat settings") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Read receipts"); Switch(checked = settings.readReceipts, onCheckedChange = { onSettingsChange(settings.copy(readReceipts = it)) }) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Story replies"); Switch(checked = settings.storyReplies, onCheckedChange = { onSettingsChange(settings.copy(storyReplies = it)) }) }
            Text("More chat appearance and personalization options", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
