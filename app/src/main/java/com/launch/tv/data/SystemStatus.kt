package com.launch.tv.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import com.launch.tv.model.NetKind
import com.launch.tv.model.NetStatus

object SystemStatus {
    fun net(context: Context): NetStatus {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            if (!online) return NetStatus(NetKind.Offline, 0)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val level = wifiLevel(context, capabilities)
                NetStatus(NetKind.Wifi, level)
            } else {
                NetStatus(NetKind.Ethernet, 3)
            }
        } catch (_: Exception) {
            NetStatus(NetKind.Offline, 0)
        }
    }

    fun battery(context: Context): Int? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
            if (!present) return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            (level * 100) / scale
        } catch (_: Exception) {
            null
        }
    }

    private fun wifiLevel(context: Context, capabilities: NetworkCapabilities?): Int {
        val fromCaps = capabilities?.let { caps ->
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val reported = caps.signalStrength
                if (reported in 0..4) {
                    when (reported) {
                        0, 1 -> 0
                        2 -> 1
                        3 -> 2
                        else -> 3
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
        val fromRssi = rssiLevel(context)
        return when {
            fromCaps != null && fromRssi != null -> minOf(fromCaps, fromRssi)
            fromCaps != null -> fromCaps
            fromRssi != null -> fromRssi
            else -> 1
        }
    }

    private fun rssiLevel(context: Context): Int? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val rssi = wifi.connectionInfo?.rssi ?: return null
            if (rssi >= 0 || rssi < -100) return null
            when {
                rssi >= -60 -> 3
                rssi >= -70 -> 2
                rssi >= -80 -> 1
                else -> 0
            }
        } catch (_: Exception) {
            null
        }
    }
}
