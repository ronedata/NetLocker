package com.netlocker.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the device's currently-available Wi-Fi and Cellular [Network] handles
 * *independently* of which one is the system's "default" route.
 *
 * This is what makes NetLocker's Wi-Fi/Mobile-Data toggles behave like two real
 * independent switches instead of one heuristic: when relaying a partially-restricted
 * app's traffic we bind its egress socket directly to [NetworkSnapshot.wifi] or
 * [NetworkSnapshot.cellular] as appropriate, regardless of which network Android is
 * currently defaulting other apps to. If the required transport isn't currently up,
 * there is nothing to bind to and the traffic is correctly blocked — not silently
 * routed over the wrong transport.
 *
 * Uses [ConnectivityManager.registerNetworkCallback] (passive observation), not
 * `requestNetwork` — NetLocker never asks Android to *bring up* a radio, it only
 * reacts to networks that are already up for the user's own reasons. No permission
 * beyond ACCESS_NETWORK_STATE is required for this.
 */
class TransportMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _snapshot = MutableStateFlow(NetworkSnapshot(wifi = null, cellular = null))
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var cellularCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (wifiCallback != null) return // already running

        wifiCallback = registerFor(NetworkCapabilities.TRANSPORT_WIFI) { network ->
            _snapshot.value = _snapshot.value.copy(wifi = network)
        }
        cellularCallback = registerFor(NetworkCapabilities.TRANSPORT_CELLULAR) { network ->
            _snapshot.value = _snapshot.value.copy(cellular = network)
        }
    }

    fun stop() {
        wifiCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        cellularCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        wifiCallback = null
        cellularCallback = null
        _snapshot.value = NetworkSnapshot(wifi = null, cellular = null)
    }

    private fun registerFor(transportType: Int, onChange: (Network?) -> Unit): ConnectivityManager.NetworkCallback {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(transportType)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange(network)
            override fun onLost(network: Network) = onChange(null)
            override fun onUnavailable() = onChange(null)
        }
        connectivityManager.registerNetworkCallback(request, callback)
        return callback
    }
}

data class NetworkSnapshot(
    val wifi: Network?,
    val cellular: Network?,
)
