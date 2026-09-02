package com.fynx.app.ui

import android.content.Context

/**
 * Unified people-discovery contract for username and phone-number lookup.
 *
 * The current app has no server-wide social directory, so lookup is intentionally
 * backend-ready: username searches can use locally known profiles while phone
 * lookup waits for a secure verified-account backend. No phone numbers are
 * stored in relationship profiles or exposed by this layer.
 */
enum class FynxPeopleSearchMethod {
    USERNAME,
    PHONE
}

enum class FynxPhoneDiscoveryVisibility {
    EVERYONE,
    CONTACTS_ONLY,
    NOBODY
}

data class FynxPeopleSearchRequest(
    val method: FynxPeopleSearchMethod,
    val value: String
)

object FynxPeopleDiscovery {
    fun normalizeUsername(value: String): String = value.trim().let {
        if (it.startsWith("@")) it else "@$it"
    }

    /** Keeps only digits and a leading + so the server can apply country-aware normalization later. */
    fun normalizePhone(value: String): String {
        val trimmed = value.trim()
        val plus = trimmed.startsWith("+")
        val digits = trimmed.filter(Char::isDigit)
        return if (plus) "+$digits" else digits
    }

    fun validate(request: FynxPeopleSearchRequest): String? = when (request.method) {
        FynxPeopleSearchMethod.USERNAME -> {
            val username = normalizeUsername(request.value)
            if (username.length < 2 || !username.drop(1).all { it.isLetterOrDigit() || it == '_' || it == '.' }) {
                "Enter a valid FYNX username."
            } else null
        }
        FynxPeopleSearchMethod.PHONE -> {
            val phone = normalizePhone(request.value)
            if (phone.length < 7) "Enter a complete phone number with country code." else null
        }
    }
}

/** Device-local privacy preference until account settings are backed by the FYNX server. */
class FynxPhoneDiscoveryPrivacyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fynx_people_discovery", Context.MODE_PRIVATE)

    fun load(): FynxPhoneDiscoveryVisibility = runCatching {
        FynxPhoneDiscoveryVisibility.valueOf(
            prefs.getString("phone_discovery_visibility", FynxPhoneDiscoveryVisibility.EVERYONE.name)
                ?: FynxPhoneDiscoveryVisibility.EVERYONE.name
        )
    }.getOrDefault(FynxPhoneDiscoveryVisibility.EVERYONE)

    fun save(value: FynxPhoneDiscoveryVisibility) {
        prefs.edit().putString("phone_discovery_visibility", value.name).apply()
    }
}
