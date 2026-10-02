package br.com.thiaguinhosolucoes.smart24vision

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.net.ConnectException
import java.util.Base64
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CameraProtocolTest {
    @Test fun digestMatchesPublishedRfc2617Vector() {
        val challenge = "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        val header = CameraHttpAuth.authorization(listOf(challenge), "Mufasa", "Circle Of Life", "GET", "/dir/index.html", "0a4f113b")!!
        assertTrue(header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(header.contains("qop=auth"))
    }

    @Test fun unsupportedDigestQopDoesNotSendPlaintextFallback() {
        assertNull(CameraHttpAuth.authorization(listOf("Digest realm=\"camera\", nonce=\"one\", qop=\"auth-int\""), "operator", "private-password", "DESCRIBE", "rtsp://127.0.0.1/live"))
    }

    @Test fun cameraChallengeWithoutCredentialsStopsWithAnExplicitReason() {
        TestCamera { unauthorized() }.use { server ->
            val result = CameraRtspProbe(timeoutMs = 400).describe(server.url())
            assertEquals(CameraRtspProbe.Status.AUTH_REQUIRED, result.status)
            assertEquals(1, server.requests.size)
            val plan = CameraConnectionPlanner(server.probe()).resolve("192.168.15.5", "", "", 554, server.url())
            assertTrue(plan.needsCredentials)
            assertTrue(plan.urls.isEmpty())
            assertTrue(plan.message.contains("NVR/RTSP"))
            assertFalse(plan.report.contains("private-password"))
        }
    }

    @Test fun percentEncodedCredentialsAuthenticateWithoutAppearingInTheRequestUri() {
        TestCamera { request ->
            val authorization = request.lineSequence().firstOrNull { it.startsWith("Authorization:") }?.substringAfter(':')?.trim()
            val decoded = authorization?.takeIf { it.startsWith("Basic ") }?.removePrefix("Basic ")
                ?.let { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
            if (decoded == "u@ser:p:a ss?#/+") video() else unauthorized()
        }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "u@ser", "p:a ss?#/+", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.VIDEO, server.probe().describe(url).status)
            assertEquals(2, server.requests.size)
            assertTrue(server.requests.all { !it.lineSequence().first().contains("u%40ser") && !it.lineSequence().first().contains("p%3A") })
        }
    }

    @Test fun rejectedCredentialsAreNotRetriedAcrossEveryPath() {
        TestCamera { unauthorized() }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "wrong-user", "wrong-password", server.port, server.url()).single()
            val plan = CameraConnectionPlanner(server.probe()).resolve("127.0.0.1", "", "", 554, url)
            assertTrue(plan.needsCredentials)
            assertTrue(plan.message.contains("recusou"))
            assertEquals(2, server.requests.size)
            assertFalse(plan.report.contains("wrong-password"))
            assertFalse(plan.report.contains("wrong-user"))
        }
    }

    @Test fun digestNonceBoundToTcpConnectionAuthenticatesOnTheSameSocket() {
        val nonce = AtomicReference("")
        TestCamera(keepAlive = true, onConnection = { nonce.set("connection-$it") }) { request ->
            if (validDigest(request, nonce.get())) video() else digestChallenge(nonce.get())
        }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "rtsp-test", "rtsp-test-password", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.VIDEO, server.probe().describe(url).status)
            assertEquals(1, server.connections.get())
            assertEquals(2, server.requests.size)
            assertTrue(server.requests[0].contains("CSeq: 1"))
            assertTrue(server.requests[1].contains("CSeq: 2"))
        }
    }

    @Test fun anExpiredDigestNonceIsRefreshedOnceOnTheSameConnection() {
        val requests = AtomicInteger()
        TestCamera(keepAlive = true) { request ->
            when (requests.incrementAndGet()) {
                1 -> digestChallenge("old")
                2 -> digestChallenge("fresh", stale = true)
                else -> if (validDigest(request, "fresh")) video() else digestChallenge("fresh")
            }
        }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "rtsp-test", "rtsp-test-password", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.VIDEO, server.probe().describe(url).status)
            assertEquals(1, server.connections.get())
            assertEquals(3, server.requests.size)
        }
    }

    @Test fun aClosedChallengeConnectionAllowsOneReconnectWithAFreshNonce() {
        val nonce = AtomicReference("")
        TestCamera(keepAlive = true, onConnection = { nonce.set("connection-$it") }) { request ->
            when {
                validDigest(request, nonce.get()) -> video()
                nonce.get() == "connection-1" -> digestChallenge(nonce.get())
                    .replace("Content-Length:", "Connection: close\r\nContent-Length:")
                else -> digestChallenge(nonce.get())
            }
        }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "rtsp-test", "rtsp-test-password", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.VIDEO, server.probe().describe(url).status)
            assertEquals(2, server.connections.get())
            assertEquals(3, server.requests.size)
        }
    }

    @Test fun repeatedStaleChallengesCannotCauseAnUnboundedCredentialRetry() {
        TestCamera(keepAlive = true) { digestChallenge("always-stale", stale = true) }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "wrong-user", "wrong-password", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.AUTH_REJECTED, server.probe().describe(url).status)
            assertEquals(1, server.connections.get())
            assertEquals(3, server.requests.size)
        }
    }

    @Test fun ordinaryDigestCredentialRejectionStopsAfterOneAuthenticatedRequest() {
        TestCamera(keepAlive = true) { digestChallenge("one") }.use { server ->
            val url = MobileRtspCandidates.build("127.0.0.1", "wrong-user", "wrong-password", server.port, server.url()).single()
            assertEquals(CameraRtspProbe.Status.AUTH_REJECTED, server.probe().describe(url).status)
            assertEquals(1, server.connections.get())
            assertEquals(2, server.requests.size)
        }
    }

    @Test fun aMissingPathIsDistinguishedFromAnUnreachableCamera() {
        TestCamera { "RTSP/1.0 404 Not Found\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n" }.use { server ->
            val plan = CameraConnectionPlanner(server.probe()).resolve("127.0.0.1", "", "", 554, server.url())
            assertTrue(plan.urls.isEmpty())
            assertTrue(plan.message.contains("caminhos"))
            assertTrue(plan.report.contains("404"))
        }
    }

    @Test fun anAudioOnlySessionCannotBeReportedAsCameraVideo() {
        TestCamera { sdp("v=0\r\nm=audio 0 RTP/AVP 0\r\n") }.use { server ->
            assertEquals(CameraRtspProbe.Status.NO_VIDEO, server.probe().describe(server.url()).status)
        }
    }

    @Test fun anHttpPortCannotBeMisidentifiedAsAnRtspServer() {
        TestCamera { "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n" }.use { server ->
            assertEquals(CameraRtspProbe.Status.NOT_RTSP, server.probe().describe(server.url()).status)
        }
    }

    @Test fun explicitUrlKeepsItsOnlyAddressAndValidVideo() {
        TestCamera { video() }.use { server ->
            val plan = CameraConnectionPlanner(server.probe()).resolve("192.168.15.5", "", "", 554, server.url())
            assertEquals(listOf(server.url()), plan.urls)
            assertEquals(1, server.requests.size)
            assertFalse(plan.report.contains("192.168.15.5"))
        }
    }

    @Test fun onvifCannotRedirectCredentialsToAnotherCamera() {
        assertNull(OnvifStreamResolver.sameCameraUrl("http://192.168.15.99/onvif/media", "192.168.15.5", setOf("http")))
        assertNull(OnvifStreamResolver.sameCameraUrl("http://alice:secret@192.168.15.5/onvif/media", "192.168.15.5", setOf("http")))
        assertEquals("rtsp://192.168.15.5:554/custom", OnvifStreamResolver.sameCameraUrl("rtsp://0.0.0.0:554/custom", "192.168.15.5", setOf("rtsp")))
    }

    @Test(expected = IllegalArgumentException::class) fun onvifXmlDoesNotResolveExternalEntities() {
        OnvifStreamResolver.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///private-data'>]><x>&e;</x>")
    }

    private class TestCamera(private val keepAlive: Boolean = false,
                             private val onConnection: (Int) -> Unit = {},
                             private val reply: (String) -> String) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort
        val requests = CopyOnWriteArrayList<String>()
        val connections = AtomicInteger()
        private val thread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        onConnection(connections.incrementAndGet())
                        socket.soTimeout = 600
                        val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                        do {
                            val first = reader.readLine() ?: break
                            val request = buildString {
                                append(first).append('\n')
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                    append(line).append('\n')
                                }
                            }
                            requests += request
                            val sequence = request.lineSequence().first { it.startsWith("CSeq:") }.substringAfter(':').trim()
                            val response = reply(request).replace("CSeq: 1\r\n", "CSeq: $sequence\r\n")
                            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                            socket.getOutputStream().flush()
                            if (response.contains("Connection: close\r\n")) break
                        } while (keepAlive)
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
        }.apply { isDaemon = true; start() }
        fun url() = "rtsp://127.0.0.1:$port/unlisted-camera-path"
        fun probe() = CameraRtspProbe(socketFactory = { host, wantedPort, timeout ->
            if (wantedPort != port || host != "127.0.0.1") throw ConnectException()
            Socket().apply { connect(java.net.InetSocketAddress(host, wantedPort), timeout) }
        }, timeoutMs = 400)
        override fun close() { server.close(); thread.join(1000) }
    }

    companion object {
        private fun digestChallenge(nonce: String, stale: Boolean = false) =
            "RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\nWWW-Authenticate: Digest realm=\"session-camera\", nonce=\"$nonce\", qop=\"auth\", stale=$stale\r\nContent-Length: 0\r\n\r\n"
        private fun validDigest(request: String, nonce: String): Boolean {
            val header = request.lineSequence().firstOrNull { it.startsWith("Authorization: Digest ") } ?: return false
            val fields = Regex("([\\w-]+)=(?:\"([^\"]*)\"|([^,\\s]+))").findAll(header)
                .associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val uri = request.lineSequence().first().split(' ')[1]
            fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val ha1 = md5("rtsp-test:session-camera:rtsp-test-password")
            val expected = md5("$ha1:$nonce:${fields["nc"]}:${fields["cnonce"]}:auth:${md5("DESCRIBE:$uri")}")
            return fields["username"] == "rtsp-test" && fields["nonce"] == nonce && fields["uri"] == uri &&
                fields["qop"] == "auth" && fields["response"] == expected
        }
        private fun unauthorized() = "RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\nWWW-Authenticate: Basic realm=\"Camera\"\r\nContent-Length: 0\r\n\r\n"
        private fun video() = sdp("v=0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\n")
        private fun sdp(body: String) = "RTSP/1.0 200 OK\r\nCSeq: 1\r\nContent-Type: application/sdp\r\nContent-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n\r\n$body"
    }
}
