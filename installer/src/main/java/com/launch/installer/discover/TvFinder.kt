package com.launch.installer.discover

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.launch.installer.DeviceKind
import com.launch.installer.FoundTv
import com.launch.installer.safety.identifyBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TvFinder(private val context: Context) {
    suspend fun scan(onUpdate: (List<FoundTv>) -> Unit): Boolean {
        val local = wifiNetwork()
        if (local == null) {
            onUpdate(emptyList())
            return false
        }
        val found = ConcurrentHashMap<String, FoundTv>()
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicast = wifi?.createMulticastLock("launch-installer")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            coroutineScope {
                launch { discoverMdns(found, onUpdate) }
                launch(Dispatchers.IO) { discoverSsdp(local.address, found, onUpdate) }
                launch(Dispatchers.IO) { sweep(local, found, onUpdate) }
                delay(10_000)
            }
        } finally {
            try {
                multicast?.release()
            } catch (_: Exception) {
            }
        }
        onUpdate(snapshot(found))
        return true
    }

    private suspend fun discoverMdns(found: ConcurrentHashMap<String, FoundTv>, onUpdate: (List<FoundTv>) -> Unit) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val types = listOf("_adb._tcp.", "_adb-tls-connect._tcp.", "_googlecast._tcp.", "_androidtvremote2._tcp.")
        withContext(Dispatchers.Main) {
            types.forEach { type ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        queue.trySend(serviceInfo)
                    }
                }
                listeners += listener
                runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
            }
        }
        coroutineScope {
            val resolver = launch(Dispatchers.Main) {
                for (info in queue) {
                    if (!isActive) break
                    resolve(nsd, info)?.let { service ->
                        merge(found, fromNsd(service))
                        onUpdate(snapshot(found))
                    }
                }
            }
            try {
                delay(7_500)
            } finally {
                queue.close()
                resolver.cancel()
                withContext(Dispatchers.Main) {
                    listeners.forEach { listener ->
                        runCatching { nsd.stopServiceDiscovery(listener) }
                    }
                }
            }
        }
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null) { }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo) { }
                    }
                })
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null) { }
            }
        }

    private fun fromNsd(service: NsdServiceInfo): FoundTv? {
        val host = ipv4(service) ?: return null
        val type = service.serviceType.orEmpty()
        val castName = service.txt("fn") ?: service.serviceName.orEmpty()
        val model = service.txt("md").orEmpty()
        val brand = identifyBrand(model, "", model, "$castName ${service.serviceName}")
        return when {
            type.contains("_adb-tls-connect") -> FoundTv(
                host = host,
                port = null,
                tlsPort = service.port,
                name = castName.ifBlank { "Android TV" },
                detail = "$host · шифрованная отладка",
                kind = DeviceKind.EncryptedDebug,
                hint = brand.debugHint,
            )
            type.contains("_adb._tcp") -> FoundTv(
                host = host,
                port = service.port.takeIf { it > 0 } ?: 5555,
                tlsPort = null,
                name = service.serviceName.ifBlank { "Android TV" },
                detail = "$host:${service.port}",
                kind = DeviceKind.Ready,
                hint = brand.debugHint,
            )
            else -> {
                if (!looksLikeScreen(castName, model, brand.title)) return null
                FoundTv(
                    host = host,
                    port = null,
                    tlsPort = null,
                    name = castName.ifBlank { brand.title },
                    detail = model.ifBlank { host },
                    kind = if (brand.foreign) DeviceKind.OtherSystem else DeviceKind.NeedsDebug,
                    hint = brand.debugHint,
                )
            }
        }
    }

    private suspend fun discoverSsdp(
        local: Inet4Address,
        found: ConcurrentHashMap<String, FoundTv>,
        onUpdate: (List<FoundTv>) -> Unit,
    ) {
        val request = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"
            ).toByteArray()
        val answers = mutableListOf<String>()
        runCatching {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(local, 0))
                socket.soTimeout = 700
                val packet = DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900)
                repeat(2) { socket.send(packet) }
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + 3_000
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val incoming = DatagramPacket(buffer, buffer.size)
                        socket.receive(incoming)
                        answers += String(incoming.data, 0, incoming.length, Charsets.UTF_8)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        answers.distinct().forEach { answer ->
            val location = header(answer, "LOCATION") ?: return@forEach
            val server = header(answer, "SERVER").orEmpty()
            val description = fetchText(location)
            val name = xmlTag(description, "friendlyName").orEmpty()
            val manufacturer = xmlTag(description, "manufacturer").orEmpty()
            val model = xmlTag(description, "modelName").orEmpty()
            val host = runCatching { URL(location).host }.getOrNull() ?: return@forEach
            if (!looksLikeIpv4(host)) return@forEach
            val brand = identifyBrand(manufacturer, manufacturer, model, "$server $name")
            val interesting = brand.id != "generic" || brand.foreign || looksLikeScreen(name, model, manufacturer)
            if (!interesting) return@forEach
            if (!looksLikeScreen(name, model, "$manufacturer $server") && brand.id == "generic") return@forEach
            merge(
                found,
                FoundTv(
                    host = host,
                    port = null,
                    tlsPort = null,
                    name = name.ifBlank { brand.title },
                    detail = model.ifBlank { manufacturer }.ifBlank { host },
                    kind = if (brand.foreign) DeviceKind.OtherSystem else DeviceKind.NeedsDebug,
                    hint = brand.debugHint,
                ),
            )
            onUpdate(snapshot(found))
        }
    }

    private suspend fun sweep(
        local: LocalNet,
        found: ConcurrentHashMap<String, FoundTv>,
        onUpdate: (List<FoundTv>) -> Unit,
    ) {
        val hosts = hosts(local)
        val gate = Semaphore(48)
        coroutineScope {
            hosts.map { host ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val adb = portOpen(host, 5555, 500)
                        val cast = portOpen(host, 8008, 400)
                        if (!adb && !cast) return@withPermit
                        val titled = if (cast) castTitle(host) else null
                        if (adb) {
                            merge(
                                found,
                                FoundTv(
                                    host = host,
                                    port = 5555,
                                    tlsPort = null,
                                    name = titled ?: "Android TV",
                                    detail = "$host:5555",
                                    kind = DeviceKind.Ready,
                                    hint = "",
                                ),
                            )
                            onUpdate(snapshot(found))
                        } else if (titled != null) {
                            val brand = identifyBrand(titled, "", titled, titled)
                            merge(
                                found,
                                FoundTv(
                                    host = host,
                                    port = null,
                                    tlsPort = null,
                                    name = titled,
                                    detail = host,
                                    kind = if (brand.foreign) DeviceKind.OtherSystem else DeviceKind.NeedsDebug,
                                    hint = brand.debugHint,
                                ),
                            )
                            onUpdate(snapshot(found))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun castTitle(host: String): String? {
        val xml = fetchText("http://$host:8008/ssdp/device-desc.xml")
        val manufacturer = xmlTag(xml, "manufacturer").orEmpty()
        val model = xmlTag(xml, "modelName").orEmpty()
        val friendly = xmlTag(xml, "friendlyName").orEmpty()
        if (manufacturer.isBlank() && model.isBlank() && friendly.isBlank()) return null
        val brand = identifyBrand(manufacturer, "", model, friendly)
        val modelLabel = model.takeIf { it.isNotBlank() && !it.equals("TV", true) && !it.equals("Chromecast", true) }
        val friendlyLabel = friendly.takeIf { it.isNotBlank() && !it.equals("TV", true) }
        return when {
            modelLabel != null && brand.id != "generic" && brand.title !in modelLabel -> "${brand.title} $modelLabel"
            modelLabel != null -> modelLabel
            friendlyLabel != null -> friendlyLabel
            brand.id != "generic" -> brand.title
            else -> null
        }
    }

    private fun nameScore(name: String): Int = when {
        name.isBlank() || name == "Android TV" || name.startsWith("adb-") -> 0
        name.equals("TV", true) -> 1
        else -> 2
    }

    private fun merge(found: ConcurrentHashMap<String, FoundTv>, incoming: FoundTv?) {
        if (incoming == null) return
        found.merge(incoming.host, incoming) { old, new ->
            val port = new.port ?: old.port
            val tls = new.tlsPort ?: old.tlsPort
            val ready = port != null
            val kind = when {
                ready -> DeviceKind.Ready
                old.kind == DeviceKind.OtherSystem || new.kind == DeviceKind.OtherSystem -> {
                    if (ready) DeviceKind.Ready else DeviceKind.OtherSystem
                }
                new.kind == DeviceKind.EncryptedDebug || old.kind == DeviceKind.EncryptedDebug -> DeviceKind.EncryptedDebug
                else -> DeviceKind.NeedsDebug
            }
            val name = if (nameScore(new.name) > nameScore(old.name)) new.name else old.name
            FoundTv(
                host = old.host,
                port = port,
                tlsPort = tls,
                name = name,
                detail = if (port != null) "${old.host}:$port" else new.detail.ifBlank { old.detail },
                kind = kind,
                hint = new.hint.ifBlank { old.hint },
            )
        }
    }

    private fun snapshot(found: ConcurrentHashMap<String, FoundTv>): List<FoundTv> =
        found.values.sortedWith(compareBy<FoundTv> { it.kind.ordinal }.thenBy { it.name })

    private fun wifiNetwork(): LocalNet? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val nets = manager.allNetworks.mapNotNull { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            ) {
                return@mapNotNull null
            }
            val link = manager.getLinkProperties(network) ?: return@mapNotNull null
            val address = link.linkAddresses.firstOrNull {
                val ip = it.address
                ip is Inet4Address && !ip.isLoopbackAddress && isPrivateLan(ip)
            } ?: return@mapNotNull null
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            Triple(wifi, address.address as Inet4Address, address.prefixLength.coerceIn(24, 30))
        }
        val chosen = nets.firstOrNull { it.first } ?: nets.firstOrNull() ?: return null
        return LocalNet(chosen.second, chosen.third)
    }

    private fun isPrivateLan(address: Inet4Address): Boolean {
        val raw = address.address
        val a = raw[0].toInt() and 0xff
        val b = raw[1].toInt() and 0xff
        return a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
    }

    private fun hosts(local: LocalNet): List<String> {
        val raw = local.address.address
        val ip = ((raw[0].toInt() and 0xff) shl 24) or
            ((raw[1].toInt() and 0xff) shl 16) or
            ((raw[2].toInt() and 0xff) shl 8) or
            (raw[3].toInt() and 0xff)
        val bits = if (local.prefix < 24) 8 else 32 - local.prefix
        val count = (1 shl bits) - 2
        val mask = if (bits >= 31) 0 else (-1 shl bits)
        val base = ip and mask
        val own = local.address.hostAddress
        return (1..count.coerceAtMost(254)).map { offset ->
            val value = base + offset
            "${(value shr 24) and 0xff}.${(value shr 16) and 0xff}.${(value shr 8) and 0xff}.${value and 0xff}"
        }.filter { it != own }
    }

    private fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun ipv4(service: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            service.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress?.let { return it }
        }
        @Suppress("DEPRECATION")
        return service.host?.hostAddress
    }

    private fun NsdServiceInfo.txt(key: String): String? =
        attributes[key]?.let { raw -> String(raw, Charsets.UTF_8).trim() }?.takeIf { it.isNotEmpty() }

    private fun header(message: String, name: String): String? =
        message.lineSequence()
            .firstOrNull { it.startsWith("$name:", true) }
            ?.substringAfter(':')
            ?.trim()

    private fun xmlTag(xml: String, name: String): String? =
        Regex("<$name>(.*?)</$name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("<.*?>"), "")
            ?.trim()

    private fun fetchText(location: String): String = try {
        val connection = URL(location).openConnection() as HttpURLConnection
        connection.connectTimeout = 1200
        connection.readTimeout = 1200
        connection.inputStream.bufferedReader().use { it.readText().take(8000) }
    } catch (_: Exception) {
        ""
    }

    private fun looksLikeIpv4(host: String): Boolean =
        host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    private fun looksLikeScreen(name: String, model: String, extra: String): Boolean {
        val text = "$name $model $extra".lowercase()
        val skip = listOf("speaker", "nest mini", "nest audio", "homepod", "echo", "printer", "router", "keenetic")
        if (skip.any { it in text }) return false
        val keep = listOf(
            "tv", "bravia", "mitv", "xiaomi", "tcl", "hisense", "philips", "sharp", "toshiba",
            "haier", "skyworth", "coocaa", "sony", "shield", "chromecast", "google", "fire",
            "dell", "samsung", "lg", "panasonic", "nokia", "realme", "oneplus", "changhong",
            "grundig", "display", "android", "box", "пристав",
        )
        return keep.any { it in text }
    }

    private data class LocalNet(val address: Inet4Address, val prefix: Int)
}
