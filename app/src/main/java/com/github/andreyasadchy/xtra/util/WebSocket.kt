package com.github.andreyasadchy.xtra.util

import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class WebSocket(
    private val url: String,
    private val trustManager: X509TrustManager?,
    private val listener: Listener,
    private val headers: Map<String, String>? = null,
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var messageByteArray: ByteArray? = null
    private var useCompression = false
    private var nextFrameCompressed = false
    private val reconnect = ReconnectPolicy()

    /** Whether the current outage was already reported, so retries add no message each. */
    private var failureReported = false
    var coroutineScope: CoroutineScope? = null

    /** Connects, reads until the connection ends, and reconnects until cancelled or refused. */
    suspend fun start() = withContext(Dispatchers.IO) {
        while (isActive) {
            var rateLimited = false
            try {
                when (connect()) {
                    Handshake.OPEN -> {
                        reconnect.connected()
                        failureReported = false
                        var end = false
                        while (!end) {
                            end = readNextFrame()
                        }
                    }
                    Handshake.RETRY -> {}
                    Handshake.RATE_LIMITED -> rateLimited = true
                    Handshake.REFUSED -> {
                        close()
                        return@withContext
                    }
                }
            } catch (e: CancellationException) {
                ensureActive()
            } catch (e: SSLHandshakeException) {
                if (e.isCertificateFailure()) {
                    // Retrying cannot fix a certificate this device does not trust.
                    listener.onDisconnect(this@WebSocket, e.toString(), e.stackTraceToString())
                    close()
                    return@withContext
                }
                // A handshake cut short by a bad connection is retried like any other failure.
                reportFailure(e)
            } catch (e: Exception) {
                reportFailure(e)
            }
            close()
            delay(reconnect.nextDelayMs(rateLimited))
        }
    }

    /** Reports a failure unless this socket closed it itself or the outage was already reported. */
    private suspend fun reportFailure(e: Exception) {
        if (socket?.isClosed != true && !failureReported) {
            failureReported = true
            listener.onDisconnect(this@WebSocket, e.toString(), e.stackTraceToString())
        }
    }

    private suspend fun reportFailure(message: String) {
        if (!failureReported) {
            failureReported = true
            listener.onDisconnect(this@WebSocket, message)
        }
    }

    private enum class Handshake { OPEN, RETRY, RATE_LIMITED, REFUSED }

    private suspend fun connect(): Handshake = withContext(Dispatchers.IO) {
        val urlWithoutScheme = url.substringAfter("://")
        val host = urlWithoutScheme.substringBefore("/")
        val path = urlWithoutScheme.substringAfter('/', "")
        val socketFactory = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> SSLSocketFactory.getDefault() as SSLSocketFactory
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> SSLContext.getDefault().socketFactory
            else -> {
                val sslContext = SSLContext.getInstance("TLSv1.3")
                sslContext.init(null, arrayOf(trustManager), null)
                sslContext.socketFactory
            }
        }
        // Connect and handshake under timeouts: on a weak signal a plain createSocket could
        // wait minutes for each attempt. Held in [socket] first so a disconnect can abort it.
        val tcp = Socket()
        socket = tcp
        tcp.connect(InetSocketAddress(host, 443), CONNECT_TIMEOUT_MS)
        val tls = socketFactory.createSocket(tcp, host, 443, true) as SSLSocket
        socket = tls
        tls.soTimeout = HANDSHAKE_TIMEOUT_MS
        tls.startHandshake()
        inputStream = tls.inputStream
        outputStream = tls.outputStream
        val reader = BufferedReader(InputStreamReader(inputStream))
        val writer = BufferedWriter(OutputStreamWriter(outputStream))
        val key = Base64.encodeToString(Random.nextBytes(16), Base64.NO_WRAP)
        writer.write("GET /$path HTTP/1.1\r\n")
        writer.write("Host: $host\r\n")
        writer.write("Upgrade: websocket\r\n")
        writer.write("Connection: Upgrade\r\n")
        writer.write("Sec-WebSocket-Key: $key\r\n")
        writer.write("Sec-WebSocket-Version: 13\r\n")
        writer.write("Sec-WebSocket-Extensions: permessage-deflate\r\n")
        headers?.forEach {
            writer.write("${it.key}: ${it.value}\r\n")
        }
        writer.write("\r\n")
        writer.flush()
        useCompression = false
        messageByteArray = null
        var validated = false
        val status = reader.readLine() ?: throw EOFException("Connection closed before the handshake response")
        if (!status.startsWith("HTTP/1.1 101", true)) {
            val code = status.substringAfter(' ').take(3)
            return@withContext when {
                code == "429" -> {
                    reportFailure(status)
                    Handshake.RATE_LIMITED
                }
                // A gateway or server error passes; any other answer will not change on retry.
                code.startsWith("5") -> {
                    reportFailure(status)
                    Handshake.RETRY
                }
                else -> {
                    listener.onDisconnect(this@WebSocket, status)
                    Handshake.REFUSED
                }
            }
        }
        var line = reader.readLine()
        while (!line.isNullOrBlank()) {
            when {
                line.startsWith("Sec-WebSocket-Accept", true) -> {
                    val messageDigest = MessageDigest.getInstance("SHA-1")
                    messageDigest.update((key + ACCEPT_UUID).toByteArray())
                    val acceptString = Base64.encodeToString(messageDigest.digest(), Base64.NO_WRAP)
                    if (line.substringAfter(": ") == acceptString) {
                        validated = true
                    }
                }
                line.startsWith("Sec-WebSocket-Extensions", true) -> {
                    useCompression = line.substringAfter(": ").split(", ").find {
                        it.startsWith("permessage-deflate", true)
                    } != null
                }
            }
            line = reader.readLine()
        }
        if (!validated) {
            listener.onDisconnect(this@WebSocket, "")
            return@withContext Handshake.REFUSED
        }
        // Open connections stay quiet for long stretches; keepalives detect a dead one instead.
        tls.soTimeout = 0
        listener.onConnect(this@WebSocket)
        return@withContext Handshake.OPEN
    }

    suspend fun readNextFrame(): Boolean = withContext(Dispatchers.IO) {
        val firstByte = inputStream!!.read()
        if (firstByte < 0) {
            return@withContext true
        }
        val isFinalFrame = firstByte and FIN_BIT != 0
        val compressed = useCompression && firstByte and COMPRESSED_BIT != 0
        val opcode = firstByte and OPCODE
        val isControlFrame = firstByte and OPCODE_CONTROL_FRAME != 0
        val secondByte = inputStream!!.read()
        if (secondByte < 0) {
            return@withContext true
        }
        val lengthBytes = secondByte and LENGTH
        val length = when (lengthBytes) {
            LENGTH_SHORT -> {
                val size = 2
                val array = ByteArray(size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    inputStream?.readNBytes(array, 0, size)
                } else {
                    inputStream?.let {
                        var offset = 0
                        while (offset < size) {
                            val count = it.read(array, 0 + offset, size - offset)
                            if (count < 0) {
                                break
                            }
                            offset += count
                        }
                    }
                }
                ByteBuffer.wrap(array).short.toInt() and 0xffff
            }
            LENGTH_LONG -> {
                val size = 8
                val array = ByteArray(size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    inputStream?.readNBytes(array, 0, size)
                } else {
                    inputStream?.let {
                        var offset = 0
                        while (offset < size) {
                            val count = it.read(array, 0 + offset, size - offset)
                            if (count < 0) {
                                break
                            }
                            offset += count
                        }
                    }
                }
                ByteBuffer.wrap(array).long.toInt()
            }
            else -> lengthBytes
        }
        val data = ByteArray(length)
        if (length > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                inputStream?.readNBytes(data, 0, length)
            } else {
                inputStream?.let {
                    var offset = 0
                    while (offset < length) {
                        val count = it.read(data, 0 + offset, length - offset)
                        if (count < 0) {
                            break
                        }
                        offset += count
                    }
                }
            }
        }
        if (isControlFrame) {
            when (opcode) {
                OPCODE_PING -> {
                    writeControlFrame(OPCODE_PONG, data)
                }
                OPCODE_CLOSE -> {
                    val code = data.copyOf(2)
                    writeControlFrame(OPCODE_CLOSE, code)
                    close()
                }
            }
        } else {
            when (opcode) {
                OPCODE_TEXT, OPCODE_CONTINUATION -> {
                    val messageData = if ((opcode == OPCODE_CONTINUATION && nextFrameCompressed) || compressed) {
                        val decompressedStream = ByteArrayOutputStream()
                        val inflater = Inflater(true)
                        try {
                            val inflaterStream = InflaterOutputStream(decompressedStream, inflater)
                            inflaterStream.write(data)
                            inflaterStream.write(0x0000ffff)
                            inflaterStream.close()
                        } finally {
                            // Streams only end inflaters they created; free the native zlib state now, not at GC.
                            inflater.end()
                        }
                        decompressedStream.toByteArray()
                    } else {
                        data
                    }
                    messageByteArray.let {
                        messageByteArray = if (it != null) {
                            it + messageData
                        } else {
                            messageData
                        }
                    }
                    if (isFinalFrame) {
                        nextFrameCompressed = false
                        listener.onMessage(this@WebSocket, messageByteArray!!.decodeToString())
                        messageByteArray = null
                    } else {
                        if (opcode != OPCODE_CONTINUATION) {
                            nextFrameCompressed = compressed
                        }
                    }
                }
            }
        }
        return@withContext false
    }

    /** Writes to [target] when given, otherwise to the connection in use. */
    private suspend fun writeControlFrame(opcode: Int, data: ByteArray, target: Socket? = null) = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val firstByte = FIN_BIT or opcode
        output.write(firstByte)
        val dataSize = data.size
        val secondByte = MASKED_BIT or dataSize
        output.write(secondByte)
        val maskKey = Random.nextBytes(4)
        output.write(maskKey)
        if (dataSize > 0) {
            output.write(mask(data, maskKey))
        }
        if (target != null) {
            if (!target.isClosed) {
                output.writeTo(target.outputStream)
            }
        } else if (socket?.isClosed == false) {
            outputStream?.let { output.writeTo(it) }
        }
    }

    suspend fun write(message: String) = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        var firstByte = FIN_BIT or OPCODE_TEXT
        val messageBytes = message.toByteArray()
        val data = if (useCompression && messageBytes.size >= MINIMUM_DEFLATE_SIZE) {
            firstByte = firstByte or COMPRESSED_BIT
            val compressedStream = ByteArrayOutputStream()
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            try {
                val deflaterStream = DeflaterOutputStream(compressedStream, deflater)
                deflaterStream.write(messageBytes)
                deflaterStream.close()
            } finally {
                deflater.end()
            }
            val compressedBytes = compressedStream.toByteArray()
            if (compressedBytes.takeLast(5) == EMPTY_DEFLATE_BLOCK) {
                compressedBytes.dropLast(4).toByteArray()
            } else {
                compressedBytes + 0x00
            }
        } else {
            messageBytes
        }
        output.write(firstByte)
        val dataSize = data.size
        when {
            dataSize <= LENGTH_BYTE_MAX -> {
                val secondByte = MASKED_BIT or dataSize
                output.write(secondByte)
            }
            dataSize <= LENGTH_SHORT_MAX -> {
                val secondByte = MASKED_BIT or LENGTH_SHORT
                output.write(secondByte)
                val sizeBytes = ByteBuffer.allocate(2).putShort(dataSize.toShort()).array()
                output.write(sizeBytes)
            }
            else -> {
                val secondByte = MASKED_BIT or LENGTH_LONG
                output.write(secondByte)
                val sizeBytes = ByteBuffer.allocate(8).putLong(dataSize.toLong()).array()
                output.write(sizeBytes)
            }
        }
        val maskKey = Random.nextBytes(4)
        output.write(maskKey)
        output.write(mask(data, maskKey))
        if (socket?.isClosed == false) {
            outputStream?.let { output.writeTo(it) }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.let { closeGracefully(it) }
    }

    /** The connection in use now, to pass to [disconnect] later. */
    val connection: Any?
        get() = socket

    /**
     * Disconnects [connection] and nothing else. A reconnect replaces the socket this
     * object holds, so a late keepalive expiry must not look it up again.
     */
    suspend fun disconnect(connection: Any?) = withContext(Dispatchers.IO) {
        (connection as? Socket)?.let { closeGracefully(it) }
    }

    /**
     * Sends a close frame on [target] and closes it five seconds later. It closes at once if
     * the frame cannot be written, as while a connection is still being made, or if cancelled.
     */
    private suspend fun closeGracefully(target: Socket) {
        if (!target.isClosed) {
            try {
                writeControlFrame(OPCODE_CLOSE, ByteBuffer.allocate(2).putShort(1000).array(), target)
                delay(5000)
            } catch (e: Exception) {

            } finally {
                try {
                    target.close()
                } catch (e: Exception) {

                }
            }
        }
    }

    private suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
        } catch (e: Exception) {

        }
    }

    interface Listener {
        suspend fun onConnect(webSocket: WebSocket) {}
        suspend fun onMessage(webSocket: WebSocket, message: String) {}
        suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String? = null) {}
    }

    companion object {
        internal const val CONNECT_TIMEOUT_MS = 10_000
        internal const val HANDSHAKE_TIMEOUT_MS = 15_000

        /** Whether the handshake failed on the certificate rather than on the connection. */
        internal fun SSLHandshakeException.isCertificateFailure(): Boolean =
            generateSequence<Throwable>(this) { it.cause }.take(10).any { it is CertificateException || it is CertPathValidatorException }

        /** Client frames are XOR-masked with a 4-byte key (RFC 6455 5.3), in one pass without a List<Byte>. */
        internal fun mask(data: ByteArray, key: ByteArray): ByteArray {
            val masked = ByteArray(data.size)
            for (i in data.indices) {
                masked[i] = (data[i].toInt() xor key[i and 3].toInt()).toByte()
            }
            return masked
        }

        private const val ACCEPT_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val FIN_BIT = 128
        private const val COMPRESSED_BIT = 64
        private const val OPCODE = 15
        private const val OPCODE_CONTROL_FRAME = 8
        private const val MASKED_BIT = 128
        private const val LENGTH = 127
        private const val LENGTH_SHORT = 126
        private const val LENGTH_LONG = 127
        private const val LENGTH_BYTE_MAX = 125
        private const val LENGTH_SHORT_MAX = 0xffff
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xa
        private val EMPTY_DEFLATE_BLOCK = listOf(0x00, 0x00, 0x00, 0xFF, 0xFF)
        private const val MINIMUM_DEFLATE_SIZE = 1024
    }
}