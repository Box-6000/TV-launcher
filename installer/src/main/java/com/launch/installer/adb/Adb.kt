package com.launch.installer.adb

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import kotlin.math.min

class AdbException(message: String) : Exception(message)

class AdbKeys(context: Context) {
    private val privateFile = File(context.filesDir, "adb_pkcs8")
    private val publicFile = File(context.filesDir, "adb_pub")
    private val lock = Any()

    fun publicKeyPayload(): ByteArray = synchronized(lock) {
        loadOrCreate().second
    }

    fun sign(token: ByteArray): ByteArray = synchronized(lock) {
        val privateKey = loadOrCreate().first
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        val block = ByteArray(SIGNATURE_PADDING.size + token.size)
        SIGNATURE_PADDING.copyInto(block)
        token.copyInto(block, SIGNATURE_PADDING.size)
        cipher.doFinal(block)
    }

    private fun loadOrCreate(): Pair<PrivateKey, ByteArray> {
        if (privateFile.exists() && publicFile.exists()) {
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(privateFile.readBytes()),
            )
            return privateKey to publicFile.readBytes()
        }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.genKeyPair()
        val publicKey = pair.public as RSAPublicKey
        val payload = adbPublicKey(publicKey)
        privateFile.writeBytes(pair.private.encoded)
        publicFile.writeBytes(payload)
        return pair.private to payload
    }

    companion object {
        private const val WORDS = 64
        private val SIGNATURE_PADDING = signaturePadding()

        private fun signaturePadding(): ByteArray {
            val digestInfo = byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
            )
            val padding = ByteArray(236)
            padding[0] = 0x00
            padding[1] = 0x01
            val separator = padding.size - digestInfo.size - 1
            for (index in 2 until separator) padding[index] = 0xff.toByte()
            padding[separator] = 0x00
            digestInfo.copyInto(padding, separator + 1)
            return padding
        }

        fun adbPublicKey(publicKey: RSAPublicKey): ByteArray {
            val r32 = BigInteger.ZERO.setBit(32)
            var modulus = publicKey.modulus
            var rr = BigInteger.ZERO.setBit(WORDS * 32).modPow(BigInteger.valueOf(2), modulus)
            val n0inv = modulus.remainder(r32).modInverse(r32).negate().toInt()
            val nWords = IntArray(WORDS)
            val rrWords = IntArray(WORDS)
            for (index in 0 until WORDS) {
                val rrSplit = rr.divideAndRemainder(r32)
                rr = rrSplit[0]
                rrWords[index] = rrSplit[1].toInt()
                val nSplit = modulus.divideAndRemainder(r32)
                modulus = nSplit[0]
                nWords[index] = nSplit[1].toInt()
            }
            val raw = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            raw.putInt(WORDS)
            raw.putInt(n0inv)
            nWords.forEach(raw::putInt)
            rrWords.forEach(raw::putInt)
            raw.putInt(publicKey.publicExponent.toInt())
            val body = Base64.encodeToString(raw.array(), Base64.NO_WRAP) + " launch@phone\u0000"
            return body.toByteArray(Charsets.UTF_8)
        }
    }
}

class AdbSession private constructor(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
    private val maxPayload: Int,
) : AutoCloseable {
    private val writeLock = Any()
    private var nextId = 1

    fun shell(command: String, timeoutMs: Int = 20_000): ShellResult {
        socket.soTimeout = timeoutMs
        val marker = "__LIX_${System.nanoTime()}"
        val stream = open("shell:$command; echo $marker:${'$'}?")
        val text = stream.readUntilClose().toString(Charsets.UTF_8)
        stream.closeQuietly()
        val cut = text.lastIndexOf(marker)
        if (cut < 0) return ShellResult(text.trim(), -1)
        val code = text.substring(cut + marker.length).trim().removePrefix(":").toIntOrNull() ?: -1
        return ShellResult(text.substring(0, cut).trimEnd(), code)
    }

    fun push(file: File, remotePath: String, onProgress: (Int) -> Unit) {
        require(remotePath.startsWith("/data/local/tmp/"))
        require(remotePath.matches(Regex("/data/local/tmp/[A-Za-z0-9._-]+")))
        socket.soTimeout = 30_000
        val stream = open("sync:")
        val mode = "$remotePath,33188".toByteArray(Charsets.UTF_8)
        stream.write(syncHeader("SEND", mode.size) + mode)
        val chunkSize = (maxPayload - 64).coerceIn(1024, 64 * 1024)
        val buffer = ByteArray(chunkSize)
        var sent = 0L
        val total = file.length().coerceAtLeast(1L)
        file.inputStream().use { source ->
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                stream.write(syncHeader("DATA", count) + buffer.copyOf(count))
                sent += count
                onProgress(((sent * 100) / total).toInt().coerceIn(0, 100))
            }
        }
        val done = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        done.put("DONE".toByteArray(Charsets.UTF_8))
        done.putInt((System.currentTimeMillis() / 1000L).toInt())
        stream.write(done.array())
        val status = stream.readExact(4).toString(Charsets.UTF_8)
        if (status == "FAIL") {
            val length = leInt(stream.readExact(4))
            val message = if (length in 1..4096) stream.readExact(length).toString(Charsets.UTF_8) else "отказ"
            stream.closeQuietly()
            throw AdbException("Телевизор не принял файл: $message")
        }
        if (status != "OKAY") {
            stream.closeQuietly()
            throw AdbException("Телевизор не подтвердил передачу лаунчера.")
        }
        stream.closeQuietly()
    }

    private fun open(destination: String): Stream {
        val localId = nextId++
        writePacket(CMD_OPEN, localId, 0, "$destination\u0000".toByteArray(Charsets.UTF_8))
        repeat(8) {
            val message = readPacket()
            when (message.command) {
                CMD_OKAY -> {
                    if (message.arg1 == localId) return Stream(localId, message.arg0)
                    if (message.arg0 == localId) return Stream(localId, message.arg1)
                }
                CMD_CLSE -> {
                    if (message.arg1 == localId || message.arg0 == localId) {
                        throw AdbException("Телевизор отклонил команду.")
                    }
                }
                CMD_WRTE -> writePacket(CMD_OKAY, message.arg1, message.arg0, ByteArray(0))
            }
        }
        throw AdbException("Не удалось открыть канал к телевизору.")
    }

    private fun writePacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        synchronized(writeLock) {
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(command)
            header.putInt(arg0)
            header.putInt(arg1)
            header.putInt(payload.size)
            header.putInt(checksum(payload))
            header.putInt(command.inv())
            output.write(header.array())
            if (payload.isNotEmpty()) output.write(payload)
            output.flush()
        }
    }

    private fun readPacket(): Packet {
        val header = ByteArray(24)
        readFully(input, header)
        if (header[0] == 0x16.toByte()) {
            throw AdbException(TLS_MESSAGE)
        }
        val view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = view.int
        val arg0 = view.int
        val arg1 = view.int
        val length = view.int
        val check = view.int
        val magic = view.int
        if (magic != command.inv() || length !in 0..(4 * 1024 * 1024)) {
            throw AdbException("Телевизор ответил не как Android-отладка. Если в меню только код сопряжения, включите обычную отладку по сети.")
        }
        val payload = ByteArray(length)
        if (length > 0) readFully(input, payload)
        if (!checksumOk(payload, check)) throw AdbException("Ответ телевизора повреждён.")
        if (command == CMD_STLS) throw AdbException(TLS_MESSAGE)
        return Packet(command, arg0, arg1, payload)
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private inner class Stream(val localId: Int, var remoteId: Int) {
        private val pending = ArrayDeque<Byte>()
        private var closed = false

        fun write(payload: ByteArray) {
            var offset = 0
            while (offset < payload.size) {
                val count = min(maxPayload, payload.size - offset)
                writePacket(CMD_WRTE, localId, remoteId, payload.copyOfRange(offset, offset + count))
                waitAck()
                offset += count
            }
        }

        fun readExact(count: Int): ByteArray {
            while (pending.size < count) {
                if (closed) throw AdbException("Телевизор закрыл передачу раньше времени.")
                pull()
            }
            return ByteArray(count) { pending.removeAt(0) }
        }

        fun readUntilClose(): ByteArray {
            val out = ByteArrayOutputStream()
            while (!closed) {
                val message = readPacket()
                when (message.command) {
                    CMD_WRTE -> {
                        out.write(message.payload)
                        writePacket(CMD_OKAY, localId, message.arg0, ByteArray(0))
                    }
                    CMD_CLSE -> {
                        closed = true
                        try {
                            writePacket(CMD_CLSE, localId, remoteId, ByteArray(0))
                        } catch (_: Exception) {
                        }
                    }
                    CMD_OKAY -> Unit
                    else -> throw AdbException("Сбой чтения ответа телевизора.")
                }
            }
            return out.toByteArray()
        }

        fun closeQuietly() {
            if (closed) return
            closed = true
            try {
                writePacket(CMD_CLSE, localId, remoteId, ByteArray(0))
            } catch (_: Exception) {
            }
        }

        private fun waitAck() {
            while (true) {
                val message = readPacket()
                when (message.command) {
                    CMD_OKAY -> if (message.arg1 == localId) return
                    CMD_WRTE -> {
                        message.payload.forEach { pending.add(it) }
                        writePacket(CMD_OKAY, localId, message.arg0, ByteArray(0))
                    }
                    CMD_CLSE -> {
                        closed = true
                        throw AdbException("Телевизор прервал передачу.")
                    }
                    else -> throw AdbException("Сбой передачи на телевизор.")
                }
            }
        }

        private fun pull() {
            val message = readPacket()
            when (message.command) {
                CMD_WRTE -> {
                    message.payload.forEach { pending.add(it) }
                    writePacket(CMD_OKAY, localId, message.arg0, ByteArray(0))
                }
                CMD_OKAY -> Unit
                CMD_CLSE -> {
                    closed = true
                    try {
                        writePacket(CMD_CLSE, localId, remoteId, ByteArray(0))
                    } catch (_: Exception) {
                    }
                }
                else -> throw AdbException("Сбой передачи на телевизор.")
            }
        }
    }

    companion object {
        private const val CMD_CNXN = 0x4e584e43
        private const val CMD_OPEN = 0x4e45504f
        private const val CMD_OKAY = 0x59414b4f
        private const val CMD_CLSE = 0x45534c43
        private const val CMD_WRTE = 0x45545257
        private const val CMD_AUTH = 0x48545541
        private const val CMD_STLS = 0x534c5453
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private const val VERSION = 0x01000001
        const val TLS_MESSAGE =
            "На телевизоре включена шифрованная беспроводная отладка с кодом. Включите обычную отладку по сети на порту 5555, если такой пункт есть. Через код сопряжения установщик не подключается, чтобы не обходить защиту телевизора вслепую."

        fun connect(
            host: String,
            port: Int,
            keys: AdbKeys,
            connectTimeoutMs: Int = 6_000,
            onAllow: () -> Unit,
        ): AdbSession {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = 8_000
                val session = handshake(socket, keys, onAllow)
                return session
            } catch (error: AdbException) {
                socket.close()
                throw error
            } catch (error: Exception) {
                socket.close()
                val text = error.message.orEmpty()
                if (text.contains("ECONNREFUSED", true) || text.contains("failed to connect", true) || text.contains("Connection refused", true)) {
                    throw AdbException("Порт отладки закрыт. Включите на телевизоре отладку по сети.")
                }
                if (error is java.net.SocketTimeoutException) {
                    throw AdbException("Телевизор не ответил. Проверьте, что он в той же сети и отладка включена.")
                }
                throw AdbException(text.ifBlank { "Не удалось подключиться к телевизору." })
            }
        }

        private fun handshake(socket: Socket, keys: AdbKeys, onAllow: () -> Unit): AdbSession {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            fun send(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
                val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                header.putInt(command)
                header.putInt(arg0)
                header.putInt(arg1)
                header.putInt(payload.size)
                header.putInt(checksum(payload))
                header.putInt(command.inv())
                output.write(header.array())
                if (payload.isNotEmpty()) output.write(payload)
                output.flush()
            }
            fun read(): Packet {
                val header = ByteArray(24)
                readFully(input, header)
                if (header[0] == 0x16.toByte()) throw AdbException(TLS_MESSAGE)
                val view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val command = view.int
                val arg0 = view.int
                val arg1 = view.int
                val length = view.int
                val check = view.int
                val magic = view.int
                if (magic != command.inv() || length !in 0..(1024 * 1024)) {
                    throw AdbException("Это не отладка Android. Установщик к такому устройству не подключается.")
                }
                val payload = ByteArray(length)
                if (length > 0) readFully(input, payload)
                if (!checksumOk(payload, check)) throw AdbException("Ответ телевизора повреждён.")
                if (command == CMD_STLS) throw AdbException(TLS_MESSAGE)
                return Packet(command, arg0, arg1, payload)
            }

            send(CMD_CNXN, VERSION, 256 * 1024, "host::\u0000".toByteArray(Charsets.UTF_8))
            var message = read()
            if (message.command == CMD_AUTH) {
                if (message.arg0 != AUTH_TOKEN) throw AdbException("Телевизор запросил незнакомую проверку.")
                send(CMD_AUTH, AUTH_SIGNATURE, 0, keys.sign(message.payload))
                message = read()
                if (message.command == CMD_AUTH) {
                    onAllow()
                    socket.soTimeout = 90_000
                    send(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, keys.publicKeyPayload())
                    try {
                        message = read()
                    } catch (_: java.net.SocketTimeoutException) {
                        throw AdbException("На экране телевизора не нажали «Разрешить». Подтвердите отладку и повторите.")
                    }
                }
            }
            if (message.command != CMD_CNXN) throw AdbException("Телевизор не пустил отладку.")
            val maxPayload = message.arg1.coerceIn(4 * 1024, 1024 * 1024)
            socket.soTimeout = 20_000
            return AdbSession(socket, input, output, maxPayload)
        }

        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) throw AdbException("Телевизор закрыл соединение.")
                offset += count
            }
        }

        private fun checksum(payload: ByteArray): Int {
            var sum = 0
            for (byte in payload) sum += byte.toInt() and 0xff
            return sum
        }

        // С протокола 0x01000001 (Android 9+) демон пишет в сумму 0:
        // целостность уже проверяет TCP. Ненулевую сумму по-прежнему сверяем.
        private fun checksumOk(payload: ByteArray, check: Int): Boolean =
            check == 0 || checksum(payload) == check

        private fun syncHeader(id: String, length: Int): ByteArray {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(id.toByteArray(Charsets.UTF_8))
            buffer.putInt(length)
            return buffer.array()
        }

        private fun leInt(bytes: ByteArray): Int =
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }
}

data class ShellResult(val text: String, val code: Int)

private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)
