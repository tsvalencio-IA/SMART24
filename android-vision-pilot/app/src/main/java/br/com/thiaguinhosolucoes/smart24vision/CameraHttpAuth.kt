package br.com.thiaguinhosolucoes.smart24vision

import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Per-request HTTP/RTSP authorization; no global authenticator or stored password. */
object CameraHttpAuth {
    fun authorization(challenges: List<String>, user: String, password: String,
                      method: String, uri: String, cnonce: String = UUID.randomUUID().toString().replace("-", "")): String? {
        if (user.isBlank()) return null
        val challenge = challenges.firstOrNull { it.startsWith("Digest ", true) }
            ?: challenges.firstOrNull { it.startsWith("Basic ", true) } ?: return null
        if (challenge.startsWith("Basic ", true)) {
            return "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
        }
        val values = Regex("([\\w-]+)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,\\s]+))")
            .findAll(challenge.substringAfter(' ')).associate { match ->
                match.groupValues[1].lowercase() to (match.groupValues[2].ifEmpty { match.groupValues[3] }
                    .replace(Regex("\\\\(.)"), "$1"))
            }
        val realm = values["realm"] ?: return null
        val nonce = values["nonce"] ?: return null
        val algorithm = values["algorithm"] ?: "MD5"
        val baseAlgorithm = algorithm.replace(Regex("-sess$", RegexOption.IGNORE_CASE), "")
        if (baseAlgorithm.uppercase() !in setOf("MD5", "SHA-256")) return null
        fun hash(value: String) = MessageDigest.getInstance(baseAlgorithm.uppercase())
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "")}\""
        var ha1 = hash("$user:$realm:$password")
        if (algorithm.endsWith("-sess", true)) ha1 = hash("$ha1:$nonce:$cnonce")
        val ha2 = hash("$method:$uri")
        val qopValues = values["qop"]?.split(',')?.map { it.trim().lowercase() }
        if (qopValues != null && "auth" !in qopValues) return null
        val response = if (qopValues == null) hash("$ha1:$nonce:$ha2")
            else hash("$ha1:$nonce:00000001:$cnonce:auth:$ha2")
        val fields = mutableListOf("username=${quote(user)}", "realm=${quote(realm)}",
            "nonce=${quote(nonce)}", "uri=${quote(uri)}", "response=${quote(response)}", "algorithm=$algorithm")
        values["opaque"]?.let { fields += "opaque=${quote(it)}" }
        if (qopValues != null) fields += listOf("qop=auth", "nc=00000001", "cnonce=${quote(cnonce)}")
        else if (algorithm.endsWith("-sess", true)) fields += "cnonce=${quote(cnonce)}"
        return "Digest " + fields.joinToString(", ")
    }
}
