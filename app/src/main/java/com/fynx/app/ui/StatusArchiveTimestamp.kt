package com.fynx.app.ui

fun formatStatusTimestamp(timeMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))
    return if (now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR)) {
        "Today, $time"
    } else {
        java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))
    }
}
