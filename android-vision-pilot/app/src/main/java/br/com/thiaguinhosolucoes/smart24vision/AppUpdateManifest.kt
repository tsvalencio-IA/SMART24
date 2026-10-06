package br.com.thiaguinhosolucoes.smart24vision

import org.json.JSONObject

data class AppUpdateInfo(
    val enabled: Boolean,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String
)

object AppUpdateManifest {
    private const val RELEASE_PREFIX = "https://github.com/tsvalencio-IA/SMART24/releases/download/"

    fun parse(json: String): AppUpdateInfo {
        val root = JSONObject(json)
        val enabled = root.optBoolean("enabled", false)
        val versionCode = root.getInt("versionCode")
        val versionName = root.getString("versionName").trim()
        val apkUrl = root.getString("apkUrl").trim()
        val sha256 = root.getString("sha256").trim().lowercase()

        require(versionCode > 0) { "versionCode inválido" }
        require(versionName.isNotBlank()) { "versionName vazio" }
        require(apkUrl.startsWith(RELEASE_PREFIX)) { "Origem do APK não permitida" }
        require(Regex("^[0-9a-f]{64}$").matches(sha256)) { "SHA-256 inválido" }

        return AppUpdateInfo(enabled, versionCode, versionName, apkUrl, sha256)
    }
}
