package com.fynx.app.ui

data class AiConversationContext(
    val summary: String = "",
    val currentTask: String = ""
)

fun buildAiConversationContext(messages: List<AiMessage>): AiConversationContext {
    val meaningful = messages.filter { it.text.isNotBlank() && it.text != "Hi, I'm FYNX AI. Ask me anything about your FYNX experience." }
    val userMessages = meaningful.filter { it.fromUser }.takeLast(6)
    val latest = userMessages.lastOrNull()?.text.orEmpty()
    val task = when {
        latest.contains("find", true) || latest.contains("search", true) -> "Finding information"
        latest.contains("write", true) || latest.contains("rewrite", true) || latest.contains("caption", true) -> "Writing or improving content"
        latest.contains("buy", true) || latest.contains("product", true) || latest.contains("marketplace", true) -> "Marketplace discovery"
        latest.contains("friend", true) || latest.contains("follow", true) || latest.contains("people", true) -> "People and connections"
        latest.contains("message", true) || latest.contains("chat", true) -> "Messaging"
        latest.contains("calendar", true) || latest.contains("plan", true) || latest.contains("todo", true) -> "Planning"
        else -> ""
    }
    val summary = userMessages.joinToString(" | ") { it.text.trim().take(220) }.take(900)
    return AiConversationContext(summary = summary, currentTask = task)
}
