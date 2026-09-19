package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Resolves the device's current location to a human-readable place label.
 * FYNX stores only the label on a post; raw coordinates never leave the device.
 */
object FynxPostLocationClient {
    suspend fun currentPlace(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) throw SecurityException("Location permission is required.")

            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: throw IllegalStateException("Location services are unavailable.")
            val providers = buildList {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            }
            val location = providers.asSequence()
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .minByOrNull { it.ageMillis() }
                ?: throw IllegalStateException("FYNX could not get your current location. Turn on Location and try again.")

            resolvePlace(context, location)
        }
    }

    private fun resolvePlace(context: Context, location: Location): String {
        if (!Geocoder.isPresent()) throw IllegalStateException("Address lookup is unavailable on this device.")
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(location.latitude, location.longitude, 1).orEmpty()
        val address = addresses.firstOrNull() ?: throw IllegalStateException("FYNX could not identify this location.")
        val label = listOfNotNull(
            address.locality?.trim()?.takeIf { it.isNotBlank() },
            address.subAdminArea?.trim()?.takeIf { it.isNotBlank() && it != address.locality?.trim() },
            address.adminArea?.trim()?.takeIf { it.isNotBlank() && it != address.locality?.trim() },
            address.countryName?.trim()?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(", ")
        return label.takeIf { it.isNotBlank() }
            ?: address.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("FYNX could not identify this location.")
    }

    private fun Location.ageMillis(): Long =
        (System.currentTimeMillis() - time).coerceAtLeast(0L)
}
