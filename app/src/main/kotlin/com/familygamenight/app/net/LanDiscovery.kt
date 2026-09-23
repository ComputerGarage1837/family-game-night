package com.familygamenight.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.familygamenight.core.net.NSD_SERVICE_TYPE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class FoundGame(val name: String, val host: String, val port: Int)

/** Finds (and advertises) games on the local Wi-Fi using Android's network service discovery. */
@Suppress("DEPRECATION") // resolveService / host are deprecated on API 34 but still the simplest path back to API 26
class LanDiscovery(context: Context) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    private val _found = MutableStateFlow<List<FoundGame>>(emptyList())
    val found: StateFlow<List<FoundGame>> = _found.asStateFlow()

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun advertise(name: String, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        registration = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stopAdvertising() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun startSearching() {
        stopSearching()
        _found.value = emptyList()
        multicastLock = wifi.createMulticastLock("family-game-night").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) = enqueueResolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.name == info.serviceName }
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stopSearching() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }
    }

    // Older Android versions can only resolve one service at a time.
    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            resolveQueue.removeFirstOrNull().also { if (it == null) resolving = false }
        } ?: return
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = resolveNext()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    if (host != null) {
                        val g = FoundGame(info.serviceName, host, info.port)
                        _found.value = _found.value.filterNot { it.name == g.name } + g
                    }
                    resolveNext()
                }
            })
        }.onFailure { resolveNext() }
    }

    companion object {
        /** This device's Wi-Fi address(es), to show the host so others can type it in. */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
    }
}
