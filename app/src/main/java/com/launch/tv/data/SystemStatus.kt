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
                val level = wifiLevel(context)
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

    private fun wifiLevel(context: Context): Int {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val rssi = wifi.connectionInfo?.rssi ?: return 2
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
        } catch (_: Exception) {
            2
        }
    }
}
