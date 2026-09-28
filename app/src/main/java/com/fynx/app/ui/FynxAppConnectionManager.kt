package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Single app-level connection signal for the FYNX shell/header.
 *
 * Transport availability is deliberately kept separate from FYNX reachability:
 * Android can report an INTERNET-capable transport while the backend is unreachable.
 * The header only reports CONNECTED after an authenticated FYNX health request succeeds.
 */
class FynxAppConnectionManager(
    context: Context
) : DefaultLifecycleObserver {
    enum class State {
        WAITING_FOR_NETWORK,
        CONNECTING,
        CONNECTED
    }

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State.WAITING_FOR_NETWORK)
    val state: StateFlow<State> = _state.asStateFlow()

    private var lifecycleOwner: LifecycleOwner? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var probeJob: Job? = null
    private var generation = 0L
    private var started = false

    override fun onStart(owner: LifecycleOwner) {
        if (lifecycleOwner !== owner) {
            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = owner
        }
        started = true
        registerCallback()
        evaluate()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (lifecycleOwner === owner) stop()
    }

    fun stop() {
        started = false
        unregisterCallback()
        probeJob?.cancel()
        probeJob = null
        generation++
        _state.value = State.WAITING_FOR_NETWORK
    }

    fun dispose() {
        started = false
        lifecycleOwner?.lifecycle?.removeObserver(this)
        lifecycleOwner = null
        unregisterCallback()
        probeJob?.cancel()
        probeJob = null
        scope.cancel()
    }

    private fun registerCallback() {
        val manager = connectivityManager ?: return
        if (callback != null) return

        val newCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = evaluate()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = evaluate()
            override fun onLost(network: Network) = evaluate()
        }

        runCatching {
            manager.registerDefaultNetworkCallback(newCallback)
            callback = newCallback
        }
    }

    private fun unregisterCallback() {
        val manager = connectivityManager
        val current = callback ?: return
        callback = null
        runCatching { manager?.unregisterNetworkCallback(current) }
    }

    private fun hasUsableTransport(): Boolean {
        val manager = connectivityManager ?: return false
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun evaluate() {
        if (!started) return
        if (!hasUsableTransport()) {
            generation++
            probeJob?.cancel()
            probeJob = null
            _state.value = State.WAITING_FOR_NETWORK
            return
        }

        _state.value = State.CONNECTING
        probeJob?.cancel()
        val currentGeneration = ++generation
        probeJob = scope.launch {
            var attempt = 0
            while (started && currentGeneration == generation && hasUsableTransport()) {
                val reachable = FynxBackendClient.health(appContext).isSuccess
                if (reachable && currentGeneration == generation && started && hasUsableTransport()) {
                    _state.value = State.CONNECTED
                    return@launch
                }

                _state.value = State.CONNECTING
                val delayMs = (1_000L shl attempt.coerceAtMost(4)).coerceAtMost(30_000L)
                attempt = (attempt + 1).coerceAtMost(5)
                delay(delayMs)
            }

            if (currentGeneration == generation && started && !hasUsableTransport()) {
                _state.value = State.WAITING_FOR_NETWORK
            }
        }
    }
}
