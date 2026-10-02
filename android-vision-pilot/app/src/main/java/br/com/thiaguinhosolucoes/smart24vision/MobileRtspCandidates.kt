package br.com.thiaguinhosolucoes.smart24vision

import java.net.URI
import java.net.URLEncoder

/** URL policy shared by the native player and JVM regression tests. */
object MobileRtspCandidates {
    fun build(host: String, username: String, password: String, preferredPort: Int, explicitUrl: String): List<String> {
        require(preferredPort in 1..65535) { "A porta RTSP precisa estar entre 1 e 65535." }
        val auth = when {
            username.isNotBlank() -> "${encode(username.trim())}:${encode(password)}@"
            else -> ""
        }
        if (explicitUrl.isNotBlank()) {
            val uri = runCatching { URI(explicitUrl.trim()) }.getOrNull()
            require(uri != null && uri.scheme.equals("rtsp", true) && !uri.host.isNullOrBlank() && uri.fragment == null) {
                "A URL precisa começar com rtsp:// e conter o endereço da câmera."
            }
            require(uri.port == -1 || uri.port in 1..65535) { "Porta inválida na URL RTSP." }
            val authority = if (uri.rawUserInfo == null) auth + uri.rawAuthority else uri.rawAuthority
            return listOf("rtsp://$authority${uri.rawPath.orEmpty()}${uri.rawQuery?.let { "?$it" }.orEmpty()}")
        }
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        require(cleanHost.isNotBlank() && !cleanHost.contains(Regex("[\\s/@?#]"))) { "Informe somente o IP ou nome local da câmera." }
        val authorityHost = if (':' in cleanHost) "[$cleanHost]" else cleanHost
        val probe = runCatching { URI("rtsp://$authorityHost:$preferredPort") }.getOrNull()
        require(!probe?.host.isNullOrBlank()) { "O endereço da câmera é inválido." }
        val paths = listOf("/onvif1", "/onvif2", "/live/ch00_0", "/live/ch00_1", "/Streaming/Channels/101", "/Streaming/Channels/102", "/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1", "")
        return linkedSetOf(preferredPort, 554, 5000, 8554).flatMap { port ->
            paths.map { "rtsp://$auth$authorityHost:$port$it" }
        }.distinct()
    }

    fun mask(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "rtsp://[endereço protegido]"
        val host = uri.host ?: return "rtsp://[endereço protegido]"
        val authorityHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return "rtsp://${if (uri.rawUserInfo != null) "***:***@" else ""}$authorityHost${if (uri.port > 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}${if (uri.rawQuery != null) "?[parâmetros protegidos]" else ""}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
