package com.syedtechdevelopers.imageenhancerpro.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Internet on/off, kept live. Enhancing needs internet (ads pay for the free app). */
class Net(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    private val main = Handler(Looper.getMainLooper())

    var online by mutableStateOf(check()); internal set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post { refresh() } }
        override fun onLost(network: Network) { main.postDelayed({ refresh() }, 300) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { main.post { refresh() } }
    }

    init {
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    fun check(): Boolean = runCatching {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    fun refresh(): Boolean {
        online = check()
        return online
    }

    fun close() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
