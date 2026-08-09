package br.com.thiaguinhosolucoes.smart24vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.concurrent.thread

class RtspCompatibilityProxyTest {
    @Test
    fun addsMissingCSeqFromMatchingRequest() {
        val fakeCamera = ServerSocket(0)
        val releaseCamera = CountDownLatch(1)
        thread(isDaemon = true) {
            fakeCamera.accept().use { camera ->
                val request = readRtspHeader(camera.getInputStream())
                assertTrue(request.contains("CSeq: 7"))
                camera.getOutputStream().apply {
                    write("RTSP/1.0 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"camera\"\r\n\r\n".toByteArray())
                    flush()
                }
                releaseCamera.await(3, TimeUnit.SECONDS)
            }
        }

        val repairedCSeq = AtomicInteger(-1)
        val proxy = RtspCompatibilityProxy(
            upstreamHost = "127.0.0.1",
            upstreamPort = fakeCamera.localPort,
            upstreamSocketFactory = SocketFactory.getDefault(),
            onCSeqRepair = repairedCSeq::set
        )
        proxy.media3SocketFactory.createSocket("camera.local", 554).use { playerSocket ->
            playerSocket.soTimeout = 3_000
            playerSocket.getOutputStream().apply {
                write("OPTIONS rtsp://camera.local/onvif1 RTSP/1.0\r\nCSeq: 7\r\n\r\n".toByteArray())
                flush()
            }
            val response = readRtspHeader(playerSocket.getInputStream())
            assertTrue(response.startsWith("RTSP/1.0 401"))
            assertTrue(response.contains("CSeq: 7"))
            assertEquals(1, Regex("(?im)^CSeq\\s*:").findAll(response).count())
            assertEquals(7, repairedCSeq.get())
        }

        releaseCamera.countDown()
        proxy.close()
        fakeCamera.close()
    }

    @Test
    fun preservesValidResponseAndInterleavedVideoBytes() {
        val fakeCamera = ServerSocket(0)
        val releaseCamera = CountDownLatch(1)
        val interleaved = byteArrayOf(0x24, 0x00, 0x00, 0x04, 0x01, 0x02, 0x03, 0x04)
        thread(isDaemon = true) {
            fakeCamera.accept().use { camera ->
                readRtspHeader(camera.getInputStream())
                camera.getOutputStream().apply {
                    write("RTSP/1.0 200 OK\r\nCSeq: 8\r\nPublic: OPTIONS\r\n\r\n".toByteArray())
                    write(interleaved)
                    flush()
                }
                releaseCamera.await(3, TimeUnit.SECONDS)
            }
        }

        val repairs = AtomicInteger(0)
        val proxy = RtspCompatibilityProxy(
            upstreamHost = "127.0.0.1",
            upstreamPort = fakeCamera.localPort,
            upstreamSocketFactory = SocketFactory.getDefault(),
            onCSeqRepair = { repairs.incrementAndGet() }
        )
        proxy.media3SocketFactory.createSocket("camera.local", 554).use { playerSocket ->
            playerSocket.soTimeout = 3_000
            playerSocket.getOutputStream().apply {
                write("OPTIONS rtsp://camera.local/onvif1 RTSP/1.0\r\nCSeq: 8\r\n\r\n".toByteArray())
                flush()
            }
            val response = readRtspHeader(playerSocket.getInputStream())
            assertEquals(1, Regex("(?im)^CSeq\\s*:").findAll(response).count())
            assertEquals(0, repairs.get())

            val receivedInterleaved = ByteArray(interleaved.size)
            readExactly(playerSocket.getInputStream(), receivedInterleaved)
            assertArrayEquals(interleaved, receivedInterleaved)
        }

        releaseCamera.countDown()
        proxy.close()
        fakeCamera.close()
    }

    private fun readRtspHeader(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        var last4 = -1
        var last3 = -1
        var last2 = -1
        var last1 = -1
        while (true) {
            val next = input.read()
            check(next != -1) { "Conexão encerrada antes do cabeçalho RTSP." }
            bytes.write(next)
            last4 = last3
            last3 = last2
            last2 = last1
            last1 = next
            if (last4 == '\r'.code && last3 == '\n'.code && last2 == '\r'.code && last1 == '\n'.code) {
                return bytes.toString(Charsets.ISO_8859_1.name())
            }
        }
    }

    private fun readExactly(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            check(count > 0) { "Conexão encerrada durante pacote intercalado." }
            offset += count
        }
    }
}
