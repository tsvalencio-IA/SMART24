package br.com.thiaguinhosolucoes.smart24vision

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder

/** DESCRIBE distinguishes credential/path/network errors before starting native playback. */
class CameraRtspProbe(
    private val socketFactory: (String, Int, Int) -> Socket = { host, port, timeout ->
        Socket().also { socket ->
            try { socket.connect(InetSocketAddress(host, port), timeout) }
            catch (error: Exception) { socket.close(); throw error }
        }
    },
    private val timeoutMs: Int = 1800
) {
    enum class Status { VIDEO, SDP_RESPONSE, AUTH_REQUIRED, AUTH_REJECTED, AUTH_UNSUPPORTED,
        PATH_NOT_FOUND, NO_VIDEO, NO_RESPONSE, NOT_RTSP, ERROR }
    data class Result(val status: Status, val code: Int? = null)
    private data class Response(val code: Int, val headers: Map<String, List<String>>, val body: String)

    fun portOpen(host: String, port: Int, timeout: Int = 650): Boolean =
        runCatching { socketFactory(host, port, timeout).use { true } }.getOrDefault(false)

    fun describe(url: String): Result {
        val uri = URI(url)
        val requestUri = "rtsp://${uri.rawAuthority.substringAfterLast('@')}${uri.rawPath.orEmpty()}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
        val credentials = uri.rawUserInfo?.split(':', limit = 2)?.map {
            URLDecoder.decode(it.replace("+", "%2B"), "UTF-8")
        }.orEmpty()
        val user = credentials.getOrNull(0).orEmpty()
        val password = credentials.getOrNull(1).orEmpty()
        var retryAuthorization: String? = null
        var previousChallenges: List<String> = emptyList()
        return try {
            // Authenticate on the socket that issued the nonce. Reconnect only if that
            // connection actually closes or fails, with at most one recovery connection.
            repeat(2) { connectionAttempt ->
                try {
                    socketFactory(uri.host.removePrefix("[").removeSuffix("]"),
                        uri.port.takeIf { it > 0 } ?: 554, timeoutMs).use { socket ->
                        socket.soTimeout = timeoutMs
                        val stream = BufferedInputStream(socket.getInputStream())
                        var sequence = 1
                        var response = request(socket, stream, requestUri, retryAuthorization, sequence++)
                        if (response.code in setOf(401, 403)) {
                            if (user.isBlank()) return Result(Status.AUTH_REQUIRED, response.code)
                            val challenges = response.headers["www-authenticate"].orEmpty()
                            if (retryAuthorization != null && !freshDigestChallenge(challenges, previousChallenges))
                                return Result(Status.AUTH_REJECTED, response.code)
                            retryAuthorization = CameraHttpAuth.authorization(challenges, user, password, "DESCRIBE", requestUri)
                                ?: return Result(Status.AUTH_UNSUPPORTED, response.code)
                            previousChallenges = challenges
                            response = request(socket, stream, requestUri, retryAuthorization, sequence++)
                            // A server may expire a nonce in-place; refresh exactly once.
                            if (response.code == 401 && staleDigestChallenge(response.headers["www-authenticate"].orEmpty())) {
                                val updated = response.headers["www-authenticate"].orEmpty()
                                retryAuthorization = CameraHttpAuth.authorization(updated, user, password, "DESCRIBE", requestUri)
                                    ?: return Result(Status.AUTH_UNSUPPORTED, response.code)
                                previousChallenges = updated
                                response = request(socket, stream, requestUri, retryAuthorization, sequence)
                            }
                            if (response.code in setOf(401, 403)) return Result(Status.AUTH_REJECTED, response.code)
                        }
                        return when (response.code) {
                            200 -> when {
                                Regex("^m=video\\s", RegexOption.MULTILINE).containsMatchIn(response.body) -> Result(Status.VIDEO, 200)
                                response.body.isBlank() && response.headers["content-type"].orEmpty().any { "sdp" in it.lowercase() } -> Result(Status.SDP_RESPONSE, 200)
                                else -> Result(Status.NO_VIDEO, 200)
                            }
                            404 -> Result(Status.PATH_NOT_FOUND, response.code)
                            else -> Result(Status.ERROR, response.code)
                        }
                    }
                } catch (_: IOException) {
                    if (connectionAttempt == 1 || retryAuthorization == null) return Result(Status.NO_RESPONSE)
                }
            }
            Result(Status.NO_RESPONSE)
        } catch (_: NotRtsp) { Result(Status.NOT_RTSP)
        } catch (_: Exception) { Result(Status.NO_RESPONSE) }
    }

    private fun staleDigestChallenge(challenges: List<String>): Boolean = challenges.any {
        it.startsWith("Digest ", true) && Regex("(?:^|[,\\s])stale\\s*=\\s*\"?true(?:\"|[,\\s]|$)", RegexOption.IGNORE_CASE).containsMatchIn(it)
    }

    private fun freshDigestChallenge(challenges: List<String>, previous: List<String>): Boolean {
        if (staleDigestChallenge(challenges)) return true
        fun nonce(values: List<String>): String? {
            val digest = values.firstOrNull { it.startsWith("Digest ", true) } ?: return null
            val match = Regex("(?:^|[,\\s])nonce\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))", RegexOption.IGNORE_CASE).find(digest) ?: return null
            return match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        val current = nonce(challenges) ?: return false
        return current != nonce(previous)
    }

    private fun request(socket: Socket, stream: BufferedInputStream, requestUri: String, auth: String?, cseq: Int): Response {
        val request = buildString {
            append("DESCRIBE $requestUri RTSP/1.0\r\nCSeq: $cseq\r\nAccept: application/sdp\r\nUser-Agent: SMART24/3.2\r\n")
            if (auth != null) append("Authorization: $auth\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()
        val status = readLine(stream)
        if (!status.startsWith("RTSP/")) throw NotRtsp()
        val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: throw NotRtsp()
        val headers = linkedMapOf<String, MutableList<String>>()
        var finished = false
        repeat(80) {
            if (!finished) {
                val line = readLine(stream)
                if (line.isEmpty()) finished = true
                else {
                    val separator = line.indexOf(':')
                    if (separator > 0) headers.getOrPut(line.substring(0, separator).lowercase()) { mutableListOf() }
                        .add(line.substring(separator + 1).trim())
                }
            }
        }
        check(finished) { "Cabeçalho RTSP excessivo" }
        val length = headers["content-length"]?.firstOrNull()?.toIntOrNull() ?: 0
        require(length in 0..131072)
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = stream.read(body, offset, length - offset)
            if (count < 0) throw EOFException()
            offset += count
        }
        return Response(code, headers, String(body, 0, offset, Charsets.UTF_8))
    }

    private fun readLine(stream: BufferedInputStream): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val next = stream.read()
            if (next < 0 && bytes.size() == 0) throw EOFException()
            if (next < 0 || next == 10) return bytes.toString("UTF-8").removeSuffix("\r")
            bytes.write(next)
        }
        error("Linha RTSP excessiva")
    }
    private class NotRtsp : Exception()
}
