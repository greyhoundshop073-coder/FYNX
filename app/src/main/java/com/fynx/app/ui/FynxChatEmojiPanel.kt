package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val FynxEmojiCategories = linkedMapOf(
    "Recent" to listOf("😂","❤️","👍","🙏","🔥","😍","😊","🎉","😭","😮","😢","😡"),
    "Smileys" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😋","😛","😝","😜","🤪","🤨","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🫡","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕"),
    "People" to listOf("👋","🤚","🖐️","✋","👌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👍","👎","✊","👊","🤲","👏","🙌","👐","🤝","🙏","💪","🫶","👀","🧠","❤️"),
    "Animals" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧","🐦","🐤","🦄","🐝","🦋","🐢","🐍","🦎","🐙","🦀","🐠","🐟","🐬","🐳","🦈"),
    "Food" to listOf("🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🍕","🍔","🍟","🌭","🌮","🌯","🍿","🍜","🍣","🍱","🍰","🎂","🍪","🍩","☕","🧃"),
    "Travel" to listOf("🚗","🚕","🚌","🏎️","🚓","🚑","🚒","✈️","🚀","🚲","🛵","🚂","🚢","🏠","🏢","🌍","🌎","🌏","🏖️","⛺","🗺️"),
    "Objects" to listOf("📱","💻","⌚","📷","🎧","🎤","🎸","🎮","📚","💡","🔑","🎁","💰","💎","⚽","🏀","🏆","🎯","🎵","🔔","⭐","✨","💥"),
    "Symbols" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️","✅","❌","❗","❓","‼️","⁉️","⭕","🚫","💯","♻️","⚠️")
)

@Composable
fun FynxChatEmojiPanel(modifier: Modifier = Modifier, onEmojiSelected: (String) -> Unit, onClose: (() -> Unit)? = null) {
    var category by remember { mutableStateOf("Recent") }
    var query by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(FynxEmojiCategories["Recent"].orEmpty()) }
    val source = if (category == "Recent") recent else FynxEmojiCategories[category].orEmpty()
    val emojis = source.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search emoji") }, leadingIcon = { Icon(Icons.Default.Search, "Search emoji") })
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close emoji panel") }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FynxEmojiCategories.keys.forEach { name -> FilterChip(selected = category == name, onClick = { category = name; query = "" }, label = { Text(name.take(3)) }) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp, max = 260.dp), contentPadding = PaddingValues(4.dp)) {
                items(emojis) { emoji ->
                    Box(Modifier.padding(2.dp).aspectRatio(1f).clickable {
                        recent = listOf(emoji) + recent.filterNot { it == emoji }.take(31)
                        onEmojiSelected(emoji)
                    }) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
