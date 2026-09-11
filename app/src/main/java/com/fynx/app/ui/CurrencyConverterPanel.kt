package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val supportedCurrencies = listOf("USD", "NGN", "EUR", "GBP", "AED", "JPY", "CAD", "AUD", "INR", "CNY", "ZAR", "GHS", "KES")
private val fallbackRatesFromUsd: Map<String, Double> = linkedMapOf(
    "USD" to 1.0, "NGN" to 1650.0, "EUR" to 0.86, "GBP" to 0.75,
    "AED" to 3.67, "JPY" to 147.0, "CAD" to 1.38, "AUD" to 1.53,
    "INR" to 88.0, "CNY" to 7.15, "ZAR" to 17.5, "GHS" to 12.5, "KES" to 129.0
)
private const val RATE_CONNECT_TIMEOUT_MS = 8_000
private const val RATE_READ_TIMEOUT_MS = 12_000
private const val RATE_MAX_RESPONSE_BYTES = 1_000_000
private const val RATE_RETRIES = 2

@Composable
fun CurrencyConverterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("USD") }
    var to by remember { mutableStateOf("NGN") }
    var converted by remember { mutableStateOf<Double?>(null) }
    var rates by remember { mutableStateOf<Map<String, Double>>(fallbackRatesFromUsd) }
    var ratesUpdated by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadRates(base: String) {
        loading = true
        error = null
        scope.launch {
            val result = fetchOpenRates(context, base)
            result.onSuccess { fresh -> rates = fresh; ratesUpdated = true }
                .onFailure { ratesUpdated = false; error = "Live rates could not be loaded. Using the last available reference rates." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadRates(from) }

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Currency Converter 💱", style = MaterialTheme.typography.headlineSmall)
        Text(if (ratesUpdated) "Live reference rates loaded. Rates are indicative and not for settlement." else "Reference conversion with automatic live-rate refresh when available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(amountText, { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(18); converted = null }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CurrencyChoice("From", from) { from = it; converted = null; loadRates(it) }
        CurrencyChoice("To", to) { to = it; converted = null }
        Button(enabled = !loading, onClick = {
            val amount = amountText.toDoubleOrNull(); val fromRate = rates[from]; val toRate = rates[to]
            if (amount != null && amount >= 0.0 && fromRate != null && toRate != null) {
                converted = if (from == "USD") amount * toRate else if (to == "USD") amount / fromRate else amount / fromRate * toRate; error = null
            } else error = "Enter a valid amount and choose supported currencies."
        }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Updating rates…" else "Convert") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        converted?.let { value ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Converted amount", style = MaterialTheme.typography.titleMedium)
                Text("${money(value)} $to", style = MaterialTheme.typography.headlineMedium)
                val reference = if (from == "USD") rates[to] ?: 0.0 else if (to == "USD") 1.0 / (rates[from] ?: 1.0) else (rates[to] ?: 0.0) / (rates[from] ?: 1.0)
                Text("Reference: 1 $from ≈ ${money(reference)} $to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ratesUpdated) Text("Rates by ExchangeRate-API • updated periodically", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}

@Composable
private fun CurrencyChoice(label: String, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { supportedCurrencies.forEach { code -> DropdownMenuItem(text = { Text(code) }, onClick = { onSelected(code); expanded = false }) } }
    }
}

private suspend fun fetchOpenRates(context: Context, base: String): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
    var lastError: Throwable? = null
    repeat(RATE_RETRIES + 1) { attempt ->
        try {
            awaitValidatedNetwork(context)
            val result = fetchOpenRatesOnce(base)
            if (result.isSuccess || attempt == RATE_RETRIES) return@withContext result
            lastError = result.exceptionOrNull()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            lastError = error
        }
        if (attempt < RATE_RETRIES) { awaitValidatedNetwork(context); delay(750L * (attempt + 1)) }
    }
    Result.failure(lastError ?: IOException("Rate service unavailable"))
}

private fun fetchOpenRatesOnce(base: String): Result<Map<String, Double>> = runCatching {
    val connection = (URL("https://open.er-api.com/v6/latest/${base.uppercase(Locale.US)}").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = RATE_CONNECT_TIMEOUT_MS
        readTimeout = RATE_READ_TIMEOUT_MS
        useCaches = false
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        setRequestProperty("User-Agent", "FYNX-Android/1")
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > RATE_MAX_RESPONSE_BYTES) error("Rate response is too large")
                out.write(buffer, 0, count)
            }
            out.toString(Charsets.UTF_8.name())
        }.orEmpty()
        if (status !in 200..299) error("HTTP $status")
        val json = JSONObject(body)
        if (json.optString("result") != "success") error("Rate service unavailable")
        val source = json.getJSONObject("rates")
        buildMap { supportedCurrencies.forEach { code -> if (source.has(code)) put(code, source.getDouble(code)) }; put(base.uppercase(Locale.US), 1.0) }
    } finally { connection.disconnect() }
}

private fun awaitValidatedNetwork(context: Context) {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: throw IOException("Network service unavailable")
    val valid = manager.allNetworks.any { network ->
        val capabilities = manager.getNetworkCapabilities(network)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    if (!valid) throw IOException("Network connection is unavailable")
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
