package br.com.thiaguinhosolucoes.smart24vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FirebaseRestClient {
    data class LoginResult(val idToken: String, val localId: String, val email: String, val refreshToken: String = "", val expiresIn: Long = 3600)

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${BuildConfig.FIREBASE_API_KEY}")
        val payload = JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        val json = requestAbsolute(url, "POST", payload.toString(), auth = false)
        LoginResult(json.getString("idToken"), json.getString("localId"), json.optString("email", email), json.optString("refreshToken"), json.optLong("expiresIn", 3600))
    }

    suspend fun getRole(uid: String): String = withContext(Dispatchers.IO) {
        val response = request("roles/$uid", "GET", null)
        response.optString("value")
    }

    suspend fun patch(path: String, values: Map<String, Any?>) = withContext(Dispatchers.IO) {
        request(path, "PATCH", JSONObject(values).toString())
    }

    suspend fun put(path: String, values: Map<String, Any?>) = withContext(Dispatchers.IO) {
        request(path, "PUT", JSONObject(values).toString())
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        request(path, "DELETE", null)
    }

    suspend fun post(path: String, values: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        request(path, "POST", JSONObject(values).toString()).optString("name")
    }

    suspend fun getZones(storeId: String, cameraId: String): List<Zone> = withContext(Dispatchers.IO) {
        val result = requestRaw("zones/$storeId/$cameraId", "GET", null)
        if (result.isBlank() || result == "null") return@withContext emptyList()
        val json = JSONObject(result)
        val list = mutableListOf<Zone>()
        json.keys().forEach { key ->
            val obj = json.optJSONObject(key) ?: return@forEach
            list += Zone(
                zoneId = obj.optString("zoneId", key),
                storeId = obj.optString("storeId", storeId),
                cameraId = obj.optString("cameraId", cameraId),
                left = obj.optDouble("left", 0.0).toFloat(),
                top = obj.optDouble("top", 0.0).toFloat(),
                right = obj.optDouble("right", 1.0).toFloat(),
                bottom = obj.optDouble("bottom", 1.0).toFloat(),
                productId = obj.optString("productId"),
                productName = obj.optString("productName"),
                sku = obj.optString("sku"),
                coordinateSpace = obj.optString("coordinateSpace", "LEGACY_SCREEN_V1")
            )
        }
        list
    }

    private fun request(path: String, method: String, body: String?): JSONObject {
        val raw = requestRaw(path, method, body)
        if (raw.isBlank() || raw == "null") return JSONObject().put("value", "")
        return try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("value", raw.trim('"')) }
    }

    private fun requestRaw(path: String, method: String, body: String?): String {
        check(PilotSession.idToken.isNotBlank()) { "Sessão Firebase não autenticada." }
        synchronized(TOKEN_LOCK) {
            if (PilotSession.refreshToken.isNotBlank() && System.currentTimeMillis() + 60000 >= PilotSession.tokenExpiresAt) {
                val payload = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(PilotSession.refreshToken, "UTF-8")}"
                val raw = requestText(URL("https://securetoken.googleapis.com/v1/token?key=${BuildConfig.FIREBASE_API_KEY}"), "POST", payload, "application/x-www-form-urlencoded")
                val json = JSONObject(raw)
                check(json.getString("user_id") == PilotSession.uid) { "Sessão Firebase inválida." }
                PilotSession.idToken = json.getString("id_token")
                PilotSession.refreshToken = json.getString("refresh_token")
                PilotSession.tokenExpiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            }
        }
        val clean = path.trim('/').split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val base = BuildConfig.FIREBASE_DATABASE_URL.trimEnd('/')
        val url = URL("$base/$clean.json?auth=${URLEncoder.encode(PilotSession.idToken, "UTF-8")}")
        return requestText(url, method, body)
    }

    private fun requestAbsolute(url: URL, method: String, body: String?, auth: Boolean): JSONObject {
        val raw = requestText(url, method, body)
        return JSONObject(raw)
    }

    private fun requestText(url: URL, method: String, body: String?, contentType: String = "application/json; charset=UTF-8"): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            doInput = true
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                val message = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException(message ?: "Erro HTTP $code")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object { private val TOKEN_LOCK = Any() }
}
