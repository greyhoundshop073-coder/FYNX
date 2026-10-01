package com.fynx.app.ui

data class FynxFeelingActivityOption(
    val type: String,
    val label: String,
    val icon: String
)

object FynxFeelingActivityLibrary {
    val options = listOf(
        FynxFeelingActivityOption("FEELING", "Happy", "😊"),
        FynxFeelingActivityOption("FEELING", "Excited", "🤩"),
        FynxFeelingActivityOption("FEELING", "Grateful", "🙏"),
        FynxFeelingActivityOption("FEELING", "Loved", "❤️"),
        FynxFeelingActivityOption("FEELING", "Proud", "🥳"),
        FynxFeelingActivityOption("FEELING", "Blessed", "✨"),
        FynxFeelingActivityOption("FEELING", "Relaxed", "😌"),
        FynxFeelingActivityOption("FEELING", "Sad", "😔"),
        FynxFeelingActivityOption("FEELING", "Angry", "😠"),
        FynxFeelingActivityOption("FEELING", "Confident", "💪"),
        FynxFeelingActivityOption("ACTIVITY", "Watching a movie", "🎬"),
        FynxFeelingActivityOption("ACTIVITY", "Listening to music", "🎧"),
        FynxFeelingActivityOption("ACTIVITY", "Playing a game", "🎮"),
        FynxFeelingActivityOption("ACTIVITY", "Travelling", "✈️"),
        FynxFeelingActivityOption("ACTIVITY", "Celebrating", "🎉"),
        FynxFeelingActivityOption("ACTIVITY", "Working", "💼"),
        FynxFeelingActivityOption("ACTIVITY", "Eating", "🍽️"),
        FynxFeelingActivityOption("ACTIVITY", "Drinking coffee", "☕"),
        FynxFeelingActivityOption("ACTIVITY", "Exercising", "🏃"),
        FynxFeelingActivityOption("ACTIVITY", "Studying", "📚")
    )

    fun find(type: String, label: String): FynxFeelingActivityOption? =
        options.firstOrNull { it.type == type && it.label.equals(label, ignoreCase = true) }
}
