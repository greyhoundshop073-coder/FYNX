package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.fynx.app.people.FynxPeopleDiscovery

private fun mergeContacts(primary: List<DeviceContact>, sim: List<DeviceContact>): List<DeviceContact> {
    val merged = linkedMapOf<String, DeviceContact>()
    (primary + sim).forEach { contact ->
        val key = FynxPeopleDiscovery.normalizePhone(contact.phone)
        if (key.length >= 7 && !merged.containsKey(key)) merged[key] = contact
    }
    return merged.values.sortedBy { it.name.lowercase() }
}

private fun readSimContacts(context: Context): List<DeviceContact> {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val output = linkedMapOf<String, DeviceContact>()
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.RawContacts.ACCOUNT_TYPE)
    runCatching {
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, "${ContactsContract.RawContacts.ACCOUNT_TYPE} LIKE ?", arrayOf("%SIM%"), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val phone = if (phoneIndex >= 0) cursor.getString(phoneIndex).orEmpty() else ""
                val normalized = FynxPeopleDiscovery.normalizePhone(phone)
                if (normalized.length >= 7) output[normalized] = DeviceContact(name, normalized)
            }
        }
    }
    return output.values.toList()
}
private fun readDeviceContacts(context: Context): List<DeviceContact> {
    val output = linkedMapOf<String, DeviceContact>()
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->