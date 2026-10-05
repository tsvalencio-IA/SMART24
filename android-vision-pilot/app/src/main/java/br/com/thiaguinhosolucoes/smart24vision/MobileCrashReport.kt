package br.com.thiaguinhosolucoes.smart24vision

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

class Smart24Application : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // No exception messages, URLs, credentials, frames or app input.
                val details = generateSequence(error) { it.cause }.take(4).joinToString("\n") { cause ->
                    cause.javaClass.name + "\n" + cause.stackTrace.take(24).joinToString("\n") { it.toString() }
                }
                File(filesDir,"last_failure.txt").writeText("Version ${BuildConfig.VERSION_NAME}\n$details")
            }
            previous?.uncaughtException(thread,error)
        }
    }
}
object MobileCrashReport {
    fun previous(context: Context): String? = runCatching {
        val javaFile = File(context.filesDir,"last_failure.txt")
        val java = if (javaFile.exists()) javaFile.readText().also { javaFile.delete() } else null
        var exit = ""
        if (Build.VERSION.SDK_INT >= 30) {
            val prefs = context.getSharedPreferences("smart24_crashes",Context.MODE_PRIVATE)
            val latest = context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName,0,5)
                .firstOrNull { it.reason in setOf(ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_ANR) }
            if (latest != null && latest.timestamp > prefs.getLong("reported",0)) {
                prefs.edit().putLong("reported",latest.timestamp).apply()
                exit = "Android exit reason=${latest.reason}; time=${latest.timestamp}"
            }
        }
        if (java == null && exit.isBlank()) null
        else "SMART24 ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}\n$exit\n${java.orEmpty()}"
    }.getOrNull()
}
