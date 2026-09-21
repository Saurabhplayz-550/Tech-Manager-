package com.example.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlin.random.Random

object NetworkUtil {

    fun generateRandomPin(): String {
        val number = Random.nextInt(100000, 999999)
        return number.toString()
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) model else "$manufacturer $model"
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First look for hotspot interfaces (ap0, wlan1, etc.) or wlan0
            var fallbackIp: String? = null

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (name.contains("ap") || name.contains("hotspot")) {
                            return host
                        }
                        if (name.contains("wlan")) {
                            return host
                        }
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            fallbackIp = host
                        }
                    }
                }
            }
            return fallbackIp
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isConnectedToWifiOrHotspot(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return getLocalIpAddress() != null
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return getLocalIpAddress() != null
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || getLocalIpAddress() != null
    }
}
