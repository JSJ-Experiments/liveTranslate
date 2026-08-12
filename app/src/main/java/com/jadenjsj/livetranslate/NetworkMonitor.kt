package com.jadenjsj.livetranslate

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class NetworkMonitor(context: Context, private val onStateChange: (Boolean) -> Unit = {}) : Closeable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val mutableOnline = MutableStateFlow(connectivity.isCurrentlyOnline())
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback,
        )
    }

    private fun refresh() {
        val online = connectivity.isCurrentlyOnline()
        if (online != mutableOnline.value) onStateChange(online)
        mutableOnline.value = online
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
