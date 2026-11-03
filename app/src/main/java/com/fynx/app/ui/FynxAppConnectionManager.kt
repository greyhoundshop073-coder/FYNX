package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
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
    private var drainJob: Job? = null
    private var generation = 0L
    private var started = false

    private companion object {
        const val CONNECTED_RECHECK_DELAY_MS = 30_000L
    }

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
        drainJob?.cancel()
        drainJob = null
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
        drainJob?.cancel()
        drainJob = null
        scope.cancel()
    }

    private fun registerCallback() {
        val manager = connectivityManager ?: return
        if (callback != null) return

        val newCallback = object : ConnectivityManager.NetworkCallback() {
            // Android delivers capabilities after availability. Evaluate then, when
            // the default route's actual capabilities are available to inspect.
            override fun onAvailable(network: Network) = Unit
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = refreshDefaultNetwork()
            override fun onLost(network: Network) = refreshDefaultNetwork()
        }

        // HTTP requests from FynxBackendClient use Android's default network.
        // Track that same network so a secondary, non-default network cannot make
        // the header claim that the request path has usable transport.
        runCatching {
            manager.registerDefaultNetworkCallback(newCallback)
            callback = newCallback
        }
    }

    /**
     * A network transition must invalidate an in-flight or sleeping health probe.
     * Otherwise evaluate() sees probeJob active and ignores the transition until
     * a long request/retry or the 30-second connected recheck finishes.
     */
    private fun refreshDefaultNetwork() {
        if (!started) return
        generation++
        probeJob?.cancel()
        probeJob = null
        evaluate()
    }

    private fun unregisterCallback() {
        val manager = connectivityManager
        val current = callback ?: return
        callback = null
        runCatching { manager?.unregisterNetworkCallback(current) }
    }

    private fun hasUsableTransport(): Boolean {
        val manager = connectivityManager ?: return false
        // Match the network Android will actually use for ordinary HTTP requests.
        // If default-network handover is in progress, wait for its callback instead
        // of treating an unrelated secondary network as the active route.
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
            drainJob?.cancel()
            drainJob = null
            _state.value = State.WAITING_FOR_NETWORK
            return
        }

        if (probeJob?.isActive == true) return

        _state.value = State.CONNECTING
        val currentGeneration = ++generation
        probeJob = scope.launch {
            var attempt = 0
            while (started && currentGeneration == generation) {
                if (!hasUsableTransport()) {
                    _state.value = State.WAITING_FOR_NETWORK
                    return@launch
                }

                val reachable = FynxBackendClient.health(appContext).isSuccess
                if (reachable && currentGeneration == generation && started && hasUsableTransport()) {
                    _state.value = State.CONNECTED
                    drainOfflineOutbox()
                    attempt = 0
                    delay(CONNECTED_RECHECK_DELAY_MS)
                    continue
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

    private fun drainOfflineOutbox() {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch(Dispatchers.IO) {
            runCatching { FynxOfflineSync.drain(appContext) }
        }
    }
}
