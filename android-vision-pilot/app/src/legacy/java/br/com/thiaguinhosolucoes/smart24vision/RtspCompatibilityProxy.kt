package br.com.thiaguinhosolucoes.smart24vision

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import kotlin.concurrent.thread

/**
 * Normaliza respostas RTSP antigas sem expor a senha da câmera.
 *
 * Alguns firmwares respondem sem o cabeçalho CSeq obrigatório. O Media3 encerra o processo ao
 * receber essa resposta. Este proxy fica somente no loopback do aparelho, encaminha os bytes para
 * a câmera pela rede escolhida e repõe o CSeq correspondente quando ele realmente estiver ausente.
 * Pacotes RTP/RTCP intercalados continuam binários e não são modificados.
 */
internal class RtspCompatibilityProxy(
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val upstreamSocketFactory: SocketFactory,
    private val onCSeqRepair: (Int) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {}
) : Closeable {
    private data class Packet(val bytes: ByteArray, val isRtspMessage: Boolean)

    private val closed = AtomicBoolean(false)
    private val pendingCSeq = ConcurrentLinkedQueue<Int>()
    private val clientSocket = AtomicReference<Socket?>()
    private val upstreamSocket = AtomicReference<Socket?>()
    private val loopbackServer = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1)
    }

    /** SocketFactory entregue ao Media3; a URI continua contendo o IP real da câmera. */
    val media3SocketFactory: SocketFactory = LoopbackSocketFactory(loopbackServer.localPort)

    init {
        thread(name = "SMART24-RTSP-compat-accept", isDaemon = true) {
            acceptAndRelay()
        }
    }

    private fun acceptAndRelay() {
        try {
            val localClient = loopbackServer.accept().apply { tcpNoDelay = true }
            if (closed.get()) {
                localClient.close()
                return
            }
            clientSocket.set(localClient)

            val cameraSocket = upstreamSocketFactory.createSocket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(upstreamHost, upstreamPort), CONNECT_TIMEOUT_MS)
            }
            if (closed.get()) {
                cameraSocket.close()
                return
            }
            upstreamSocket.set(cameraSocket)

            thread(name = "SMART24-RTSP-app-to-camera", isDaemon = true) {
                relay(
                    input = localClient.getInputStream(),
                    output = cameraSocket.getOutputStream(),
                    cameraToApp = false
                )
            }
            thread(name = "SMART24-RTSP-camera-to-app", isDaemon = true) {
                relay(
                    input = cameraSocket.getInputStream(),
                    output = localClient.getOutputStream(),
                    cameraToApp = true
                )
            }
        } catch (error: Throwable) {
            failIfActive(error)
        }
    }

    private fun relay(input: InputStream, output: OutputStream, cameraToApp: Boolean) {
        try {
            while (!closed.get()) {
                val packet = readPacket(input) ?: break
                val forwarded = when {
                    !packet.isRtspMessage -> packet.bytes
                    cameraToApp -> repairResponseIfNeeded(packet.bytes)
                    else -> rememberRequestCSeq(packet.bytes)
                }
                output.write(forwarded)
                output.flush()
            }
        } catch (error: Throwable) {
            failIfActive(error)
            return
        }
        close()
    }

    private fun rememberRequestCSeq(message: ByteArray): ByteArray {
        findCSeq(message)?.let(pendingCSeq::offer)
        return message
    }

    private fun repairResponseIfNeeded(message: ByteArray): ByteArray {
        val text = message.toString(Charsets.ISO_8859_1)
        if (!text.startsWith("RTSP/", ignoreCase = true)) return message

        val existingCSeq = findCSeq(message)
        if (existingCSeq != null) {
            pendingCSeq.remove(existingCSeq)
            return message
        }

        val expectedCSeq = pendingCSeq.poll() ?: return message
        val headerBoundary = findHeaderBoundary(message) ?: return message
        val insertion = if (headerBoundary.delimiterLength == 4) {
            "\r\nCSeq: $expectedCSeq\r\n\r\n"
        } else {
            "\nCSeq: $expectedCSeq\n\n"
        }.toByteArray(Charsets.ISO_8859_1)

        val repaired = ByteArray(
            headerBoundary.start + insertion.size +
                (message.size - headerBoundary.start - headerBoundary.delimiterLength)
        )
        message.copyInto(repaired, endIndex = headerBoundary.start)
        insertion.copyInto(repaired, destinationOffset = headerBoundary.start)
        message.copyInto(
            repaired,
            destinationOffset = headerBoundary.start + insertion.size,
            startIndex = headerBoundary.start + headerBoundary.delimiterLength
        )
        onCSeqRepair(expectedCSeq)
        return repaired
    }

    private fun readPacket(input: InputStream): Packet? {
        val firstByte = input.read()
        if (firstByte == -1) return null
        if (firstByte == INTERLEAVED_MAGIC) {
            val channel = readRequiredByte(input)
            val sizeHigh = readRequiredByte(input)
            val sizeLow = readRequiredByte(input)
            val payloadSize = (sizeHigh shl 8) or sizeLow
            val packet = ByteArray(INTERLEAVED_HEADER_SIZE + payloadSize)
            packet[0] = firstByte.toByte()
            packet[1] = channel.toByte()
            packet[2] = sizeHigh.toByte()
            packet[3] = sizeLow.toByte()
            readExactly(input, packet, INTERLEAVED_HEADER_SIZE, payloadSize)
            return Packet(packet, isRtspMessage = false)
        }

        val message = ByteArrayOutputStream()
        message.write(firstByte)
        var last4 = -1
        var last3 = -1
        var last2 = -1
        var last1 = firstByte
        while (true) {
            val next = readRequiredByte(input)
            message.write(next)
            last4 = last3
            last3 = last2
            last2 = last1
            last1 = next
            val crlfBoundary = last4 == '\r'.code && last3 == '\n'.code &&
                last2 == '\r'.code && last1 == '\n'.code
            val lfBoundary = last2 == '\n'.code && last1 == '\n'.code
            if (crlfBoundary || lfBoundary) break
            if (message.size() > MAX_HEADER_BYTES) {
                throw IOException("Cabeçalho RTSP excedeu o limite de segurança.")
            }
        }

        val header = message.toByteArray()
        val contentLength = CONTENT_LENGTH_REGEX
            .find(header.toString(Charsets.ISO_8859_1))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        if (contentLength !in 0..MAX_BODY_BYTES) {
            throw IOException("Corpo RTSP excedeu o limite de segurança.")
        }
        if (contentLength > 0) {
            val body = ByteArray(contentLength)
            readExactly(input, body, 0, contentLength)
            message.write(body)
        }
        return Packet(message.toByteArray(), isRtspMessage = true)
    }

    private fun findCSeq(message: ByteArray): Int? = CSEQ_REGEX
        .find(message.toString(Charsets.ISO_8859_1))
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    private data class HeaderBoundary(val start: Int, val delimiterLength: Int)

    private fun findHeaderBoundary(message: ByteArray): HeaderBoundary? {
        for (index in 0..message.size - 4) {
            if (message[index] == '\r'.code.toByte() &&
                message[index + 1] == '\n'.code.toByte() &&
                message[index + 2] == '\r'.code.toByte() &&
                message[index + 3] == '\n'.code.toByte()
            ) {
                return HeaderBoundary(index, 4)
            }
        }
        for (index in 0..message.size - 2) {
            if (message[index] == '\n'.code.toByte() && message[index + 1] == '\n'.code.toByte()) {
                return HeaderBoundary(index, 2)
            }
        }
        return null
    }

    private fun readRequiredByte(input: InputStream): Int {
        val value = input.read()
        if (value == -1) throw EOFException("Conexão RTSP encerrada durante uma mensagem.")
        return value
    }

    private fun readExactly(input: InputStream, target: ByteArray, offset: Int, length: Int) {
        var total = 0
        while (total < length) {
            val read = input.read(target, offset + total, length - total)
            if (read == -1) throw EOFException("Conexão RTSP encerrada durante um pacote.")
            total += read
        }
    }

    private fun failIfActive(error: Throwable) {
        if (!closed.get()) onFailure(error)
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { loopbackServer.close() }
        runCatching { clientSocket.getAndSet(null)?.close() }
        runCatching { upstreamSocket.getAndSet(null)?.close() }
        pendingCSeq.clear()
    }

    private class LoopbackSocketFactory(private val proxyPort: Int) : SocketFactory() {
        override fun createSocket(): Socket = connect()

        override fun createSocket(host: String?, port: Int): Socket = connect()

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            connect(localHost, localPort)

        override fun createSocket(host: InetAddress?, port: Int): Socket = connect()

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = connect(localAddress, localPort)

        private fun connect(localAddress: InetAddress? = null, localPort: Int = 0): Socket =
            Socket().apply {
                if (localAddress != null || localPort != 0) {
                    bind(InetSocketAddress(localAddress, localPort))
                }
                connect(InetSocketAddress(InetAddress.getLoopbackAddress(), proxyPort), CONNECT_TIMEOUT_MS)
            }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val INTERLEAVED_MAGIC = 0x24
        const val INTERLEAVED_HEADER_SIZE = 4
        const val MAX_HEADER_BYTES = 128 * 1024
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
        val CSEQ_REGEX = Regex("(?im)^\\s*CSeq\\s*:\\s*(\\d+)\\s*$")
        val CONTENT_LENGTH_REGEX = Regex("(?im)^\\s*Content-Length\\s*:\\s*(\\d+)\\s*$")
    }
}
