package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context

/**
 * Mantém no aparelho a última área de vídeo confirmada pelo operador.
 *
 * O Firebase continua sendo a fonte compartilhada entre instalações, enquanto
 * esta cópia local permite que o serviço aplique o recorte imediatamente após
 * a calibração, sem esperar a próxima consulta de rede.
 */
object CameraViewportStore {
    private const val PREFS_NAME = "smart24_camera_viewports"

    fun save(context: Context, viewport: CameraViewport) {
        if (!viewport.valid) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(viewport.storeId, viewport.cameraId), serialize(viewport))
            .apply()
    }

    fun load(context: Context, storeId: String, cameraId: String): CameraViewport? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(storeId, cameraId), null)
            ?: return null
        val parts = raw.split('|')
        if (parts.size != 5) return null
        val viewport = CameraViewport(
            storeId = storeId,
            cameraId = cameraId,
            left = parts[0].toFloatOrNull() ?: return null,
            top = parts[1].toFloatOrNull() ?: return null,
            right = parts[2].toFloatOrNull() ?: return null,
            bottom = parts[3].toFloatOrNull() ?: return null,
            updatedAt = parts[4].toLongOrNull() ?: 0L
        )
        return viewport.takeIf { it.valid }
    }

    private fun key(storeId: String, cameraId: String): String =
        "${storeId.trim().lowercase()}__${cameraId.trim().uppercase()}"

    private fun serialize(viewport: CameraViewport): String = listOf(
        viewport.left,
        viewport.top,
        viewport.right,
        viewport.bottom,
        viewport.updatedAt
    ).joinToString("|")
}
