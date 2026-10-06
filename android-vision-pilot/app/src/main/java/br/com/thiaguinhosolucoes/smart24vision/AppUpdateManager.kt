package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateManager(
    private val activity: AppCompatActivity,
    private val report: (String) -> Unit
) {
    companion object {
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/tsvalencio-IA/SMART24/main/smart24-update.json"
    }

    private var pendingApk: File? = null
    private var pendingInfo: AppUpdateInfo? = null
    private var checkStarted = false

    suspend fun checkOnLaunch() {
        if (BuildConfig.DEBUG || checkStarted) return
        checkStarted = true
        try {
            val info = withContext(Dispatchers.IO) { fetchManifest() }
            if (!info.enabled || info.versionCode <= BuildConfig.VERSION_CODE) return
            withContext(Dispatchers.Main) {
                report("SMART24 ${info.versionName} disponível. Baixando atualização oficial…")
            }
            val apk = withContext(Dispatchers.IO) { downloadAndVerify(info) }
            pendingApk = apk
            pendingInfo = info
            withContext(Dispatchers.Main) {
                requestInstallPermissionOrInstall()
            }
        } catch (error: Exception) {
            withContext(Dispatchers.Main) {
                report("Não foi possível atualizar automaticamente: ${error.message ?: error.javaClass.simpleName}. O SMART24 atual continua funcionando.")
            }
        }
    }

    fun resumePendingInstall() {
        if (BuildConfig.DEBUG || pendingApk == null) return
        if (canInstallPackages()) launchInstaller()
    }

    private fun fetchManifest(): AppUpdateInfo {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SMART24/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        return try {
            require(connection.responseCode in 200..299) { "manifesto HTTP ${connection.responseCode}" }
            AppUpdateManifest.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAndVerify(info: AppUpdateInfo): File {
        val base = requireNotNull(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
            "armazenamento externo indisponível"
        }
        val dir = File(base, "smart24-updates").apply { mkdirs() }
        val target = File(dir, "SMART24-${info.versionCode}.apk")
        if (target.isFile && sha256(target) == info.sha256) return target

        val temp = File(dir, "SMART24-${info.versionCode}.download")
        if (temp.exists()) temp.delete()

        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 45000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("User-Agent", "SMART24/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            require(connection.responseCode in 200..299) { "APK HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, 1024 * 1024)
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        require(sha256(temp) == info.sha256) {
            temp.delete()
            "integridade do APK não confere"
        }
        if (target.exists()) target.delete()
        require(temp.renameTo(target)) { "não foi possível finalizar o APK baixado" }
        return target
    }

    private fun requestInstallPermissionOrInstall() {
        if (canInstallPackages()) {
            launchInstaller()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            launchInstaller()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Permitir atualização do SMART24")
            .setMessage(
                "O SMART24 já baixou e verificou a nova versão. Na primeira vez, o Android precisa autorizar este aplicativo a instalar suas próprias atualizações. Depois disso, as próximas versões serão baixadas automaticamente e você apenas confirma “Instalar”."
            )
            .setNegativeButton("Agora não", null)
            .setPositiveButton("Permitir") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            }
            .show()
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()

    private fun launchInstaller() {
        val apk = pendingApk ?: return
        val info = pendingInfo ?: return
        if (!apk.isFile) return
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.updatefiles",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        report("Atualização ${info.versionName} verificada. Confirme “Instalar” na tela do Android.")
        activity.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
