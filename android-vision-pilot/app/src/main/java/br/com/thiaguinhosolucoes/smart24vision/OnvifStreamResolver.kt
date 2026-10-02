package br.com.thiaguinhosolucoes.smart24vision

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/** Read-only ONVIF Media/Media2: device clock, services, profiles and stream URIs. */
class OnvifStreamResolver(
    private val httpFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val portOpen: (String, Int) -> Boolean = { host, port -> CameraRtspProbe().portOpen(host, port) },
    private val checkCancelled: () -> Unit = {},
    private val progress: (String) -> Unit = {}
) {
    data class Result(val urls: List<String>, val authRequired: Boolean, val report: String)
    private data class Service(val url: String, val namespace: String)
    private class SoapFailure(val authentication: Boolean) : Exception()

    fun resolve(host: String, user: String, password: String, discovered: List<String>): Result {
        val endpoints = linkedSetOf<String>()
        discovered.mapNotNull { sameCameraUrl(it, host, setOf("http", "https")) }.forEach(endpoints::add)
        val authority = if (':' in host && !host.startsWith("[")) "[$host]" else host
        var denied = false
        val urls = linkedSetOf<String>()
        for (port in listOf(80, 5000, 8080, 8899, 8000, 2020)) {
            checkCancelled()
            if (endpoints.any { URI(it).let { uri -> (uri.port.takeIf { it > 0 } ?: if (uri.scheme == "https") 443 else 80) == port } }) continue
            if (portOpen(host.removePrefix("[").removeSuffix("]"), port)) endpoints += "http://$authority:$port/onvif/device_service"
        }
        for (endpoint in endpoints.take(7)) {
            checkCancelled()
            progress("Consultando o serviço ONVIF da câmera…")
            try {
                val clock = try { call(endpoint, DEVICE, "GetSystemDateAndTime", "<tds:GetSystemDateAndTime/>", user, password, 0, security = false) }
                    catch (error: SoapFailure) { null }
                val clockOffset = clock?.let(::utcDateTime)?.let { it.toEpochMilli() - System.currentTimeMillis() } ?: 0L
                val services = linkedSetOf<Service>()
                try {
                    val response = call(endpoint, DEVICE, "GetServices", "<tds:GetServices><tds:IncludeCapability>false</tds:IncludeCapability></tds:GetServices>", user, password, clockOffset)
                    response.elements("Service").forEach { service ->
                        val namespace = service.elements("Namespace").firstOrNull()?.textContent.orEmpty().trim()
                        val url = service.elements("XAddr").firstOrNull()?.textContent.orEmpty().trim()
                        if (namespace in setOf(MEDIA, MEDIA2)) sameCameraUrl(url, host, setOf("http", "https"))?.let { services += Service(it, namespace) }
                    }
                } catch (error: SoapFailure) { if (error.authentication) throw error }
                if (services.isEmpty()) {
                    try {
                        val response = call(endpoint, DEVICE, "GetCapabilities", "<tds:GetCapabilities><tds:Category>Media</tds:Category></tds:GetCapabilities>", user, password, clockOffset)
                        response.elements("Media").forEach { media ->
                            val url = media.elements("XAddr").firstOrNull()?.textContent.orEmpty().trim()
                            sameCameraUrl(url, host, setOf("http", "https"))?.let { services += Service(it, MEDIA) }
                        }
                    } catch (error: SoapFailure) { if (error.authentication) throw error }
                }
                services += Service(endpoint, MEDIA)
                val base = URI(endpoint)
                services += Service("${base.scheme}://${base.rawAuthority}/onvif/media_service", MEDIA)
                for (service in services.take(4)) {
                    checkCancelled()
                    try {
                        val prefix = if (service.namespace == MEDIA2) "tr2" else "trt"
                        val profiles = call(service.url, service.namespace, "GetProfiles", "<$prefix:GetProfiles/>", user, password, clockOffset)
                            .elements("Profiles").filter { it.getAttribute("token").isNotBlank() }
                            .sortedBy { profile ->
                                val width = profile.elements("Width").firstOrNull()?.textContent?.toIntOrNull() ?: 960
                                if (width in 640..1920) width else width + 10000
                            }.take(3)
                        for (profile in profiles) {
                            checkCancelled()
                            val token = escape(profile.getAttribute("token"))
                            val body = if (service.namespace == MEDIA2)
                                "<tr2:GetStreamUri><tr2:Protocol>RTSP</tr2:Protocol><tr2:ProfileToken>$token</tr2:ProfileToken></tr2:GetStreamUri>"
                            else "<trt:GetStreamUri><trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup><trt:ProfileToken>$token</trt:ProfileToken></trt:GetStreamUri>"
                            val response = call(service.url, service.namespace, "GetStreamUri", body, user, password, clockOffset)
                            response.elements("Uri").map { it.textContent.trim() }.mapNotNull {
                                sameCameraUrl(it, host, setOf("rtsp"), allowUserInfo = true)
                            }.forEach(urls::add)
                        }
                        if (urls.isNotEmpty()) break
                    } catch (error: SoapFailure) { if (error.authentication) throw error }
                }
                if (urls.isNotEmpty()) break
            } catch (error: SoapFailure) {
                denied = denied || error.authentication
                if (error.authentication) break
            }
        }
        return Result(urls.toList(), denied, when {
            urls.isNotEmpty() -> "ONVIF: ${urls.size} endereço(s) de vídeo informado(s) pela câmera."
            denied -> "ONVIF: acesso recusado; confira o usuário/senha ONVIF/NVR."
            else -> "ONVIF: nenhum endereço de vídeo obtido."
        })
    }

    private fun call(endpoint: String, namespace: String, operation: String, body: String,
                     user: String, password: String, clockOffset: Long, security: Boolean = true): Document {
        checkCancelled()
        val action = "$namespace/$operation"
        val created = Instant.ofEpochMilli(System.currentTimeMillis() + clockOffset).toString()
        val securityHeader = if (security && user.isNotBlank()) usernameToken(user, password, created) else ""
        val envelope = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:tds="$DEVICE" xmlns:trt="$MEDIA" xmlns:tr2="$MEDIA2" xmlns:tt="http://www.onvif.org/ver10/schema"
            xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
            xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
            xmlns:wsa="http://www.w3.org/2005/08/addressing">
            <s:Header><wsa:Action>$action</wsa:Action><wsa:To>${escape(endpoint)}</wsa:To><wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>$securityHeader</s:Header>
            <s:Body>$body</s:Body></s:Envelope>""".trimIndent().toByteArray(Charsets.UTF_8)
        var authorization: String? = null
        repeat(2) { attempt ->
            checkCancelled()
            val connection = try { httpFactory(URL(endpoint)) }
                catch (error: java.util.concurrent.CancellationException) { throw error }
                catch (_: Exception) { throw SoapFailure(false) }
            try {
                connection.connectTimeout = 1800
                connection.readTimeout = 1800
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"$action\"")
                connection.setRequestProperty("SOAPAction", "\"$action\"")
                authorization?.let { connection.setRequestProperty("Authorization", it) }
                connection.setFixedLengthStreamingMode(envelope.size)
                connection.outputStream.use { it.write(envelope) }
                val code = connection.responseCode
                if (code == 401 && attempt == 0) {
                    val challenge = connection.headerFields.filterKeys { it?.equals("WWW-Authenticate", true) == true }.values.flatten()
                    val requestUri = URI(endpoint).let { it.rawPath.orEmpty().ifEmpty { "/" } + (it.rawQuery?.let { q -> "?$q" } ?: "") }
                    authorization = CameraHttpAuth.authorization(challenge, user, password, "POST", requestUri)
                        ?: throw SoapFailure(true)
                } else {
                    if (code in setOf(401, 403)) throw SoapFailure(true)
                    val input = if (code in 200..299) connection.inputStream else connection.errorStream
                    val xml = input?.use { stream ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            checkCancelled()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            bytes.write(buffer, 0, count)
                            if (bytes.size() > 262144) throw SoapFailure(false)
                        }
                        bytes.toString("UTF-8")
                    }.orEmpty()
                    val document = parse(xml)
                    val fault = document.elements("Fault").firstOrNull()
                    if (fault != null) throw SoapFailure(fault.textContent.contains("NotAuthorized", true) || fault.textContent.contains("Unauthorized", true))
                    if (code !in 200..299) throw SoapFailure(false)
                    return document
                }
            } catch (error: java.util.concurrent.CancellationException) { throw error
            } catch (error: SoapFailure) { throw error
            } catch (_: Exception) { throw SoapFailure(false)
            } finally { connection.disconnect() }
        }
        throw SoapFailure(true)
    }

    companion object {
        private const val DEVICE = "http://www.onvif.org/ver10/device/wsdl"
        private const val MEDIA = "http://www.onvif.org/ver10/media/wsdl"
        private const val MEDIA2 = "http://www.onvif.org/ver20/media/wsdl"
        fun usernameToken(user: String, password: String, created: String, nonce: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(nonce + created.toByteArray(Charsets.UTF_8) + password.toByteArray(Charsets.UTF_8))
            val encoder = Base64.getEncoder()
            return """<wsse:Security s:mustUnderstand="1"><wsse:UsernameToken>
                <wsse:Username>${escape(user)}</wsse:Username>
                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">${encoder.encodeToString(digest)}</wsse:Password>
                <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${encoder.encodeToString(nonce)}</wsse:Nonce>
                <wsu:Created>${escape(created)}</wsu:Created></wsse:UsernameToken></wsse:Security>""".trimIndent()
        }
        fun sameCameraUrl(value: String, host: String, schemes: Set<String>, allowUserInfo: Boolean = false): String? {
            val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in schemes || uri.host.isNullOrBlank() || uri.fragment != null || (!allowUserInfo && uri.rawUserInfo != null)) return null
            if (uri.port != -1 && uri.port !in 1..65535) return null
            fun plain(value: String) = value.removePrefix("[").removeSuffix("]").lowercase()
            if (plain(uri.host) == plain(host)) return uri.toString()
            if (plain(uri.host) !in setOf("0.0.0.0", "::")) return null
            val target = if (':' in host && !host.startsWith("[")) "[$host]" else host
            return "${uri.scheme}://${uri.rawUserInfo?.let { "$it@" }.orEmpty()}$target${if (uri.port > 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
        }
        fun parse(xml: String): Document {
            require(xml.isNotBlank() && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }
        private fun utcDateTime(document: Document): Instant? = runCatching {
            val utc = document.elements("UTCDateTime").first()
            fun number(name: String) = utc.elements(name).first().textContent.trim().toInt()
            LocalDateTime.of(number("Year"), number("Month"), number("Day"), number("Hour"), number("Minute"), number("Second")).toInstant(ZoneOffset.UTC)
        }.getOrNull()
        private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        private fun Document.elements(name: String) = documentElement.elements(name)
        private fun Element.elements(name: String): List<Element> {
            val nodes = getElementsByTagNameNS("*", name)
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
    }
}
