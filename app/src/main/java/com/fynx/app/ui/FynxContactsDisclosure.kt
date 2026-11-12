package com.fynx.app.ui

/**
 * Versioned, account-scoped acknowledgement for the Contacts matching disclosure.
 *
 * This acknowledgement covers only the Contacts discovery explanation. It is not
 * acceptance of FYNX Terms of Use or the Privacy Policy.
 */
internal object FynxContactsDisclosure {
    const val CURRENT_VERSION = 1
    const val PREFERENCES_NAME = "fynx_contacts_disclosure"

    fun preferenceKey(accountStorageKey: String): String {
        val account = accountStorageKey.trim().lowercase()
        require(account.isNotBlank()) { "An account key is required for Contacts disclosure." }
        return "accepted_version_$account"
    }

    fun requiresDisclosure(storedVersion: Int): Boolean =
        storedVersion < CURRENT_VERSION
}
