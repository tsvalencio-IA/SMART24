package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context

data class CaptureStatusSnapshot(
    val state: String,
    val message: String,
    val updatedAt: Long,
    val validFrameAt: Long
) {
    val hasValidFrame: Boolean
        get() = validFrameAt > 0L
}

/**
 * Estado local da captura de tela.
 *
 * O CaptureService continua trabalhando quando o Yoosee está em primeiro plano
 * ou quando os aplicativos estão em tela dividida. O SharedPreferences funciona
 * como uma ponte leve para a MainActivity atualizar os botões sem depender de
 * onResume(), que não é chamado continuamente no modo de tela dividida.
 */
object CaptureStatusStore {
    const val STATE_IDLE = "IDLE"
    const val STATE_STARTING = "STARTING"
    const val STATE_WAITING_VIDEO = "WAITING_VIDEO"
    const val STATE_WAITING_VIEWPORT = "WAITING_VIEWPORT"
    const val STATE_VIDEO_VISIBLE = "VIDEO_VISIBLE"
    const val STATE_NO_IMAGE = "NO_IMAGE"
    const val STATE_DEGRADED = "DEGRADED"
    const val STATE_ERROR = "ERROR"
    const val STATE_STOPPED = "STOPPED"

    private const val PREFS_NAME = "smart24_capture_status"
    private const val KEY_STATE = "state"
    private const val KEY_MESSAGE = "message"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_VALID_FRAME_AT = "valid_frame_at"

    fun reset(context: Context, message: String = "Preparando a captura da tela…") {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(KEY_STATE, STATE_STARTING)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_UPDATED_AT, now)
            .putLong(KEY_VALID_FRAME_AT, 0L)
            .apply()
    }

    fun update(
        context: Context,
        state: String,
        message: String,
        validFrame: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, state)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_UPDATED_AT, now)
        if (validFrame) editor.putLong(KEY_VALID_FRAME_AT, now)
        editor.apply()
    }

    fun snapshot(context: Context): CaptureStatusSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return CaptureStatusSnapshot(
            state = prefs.getString(KEY_STATE, STATE_IDLE) ?: STATE_IDLE,
            message = prefs.getString(KEY_MESSAGE, "") ?: "",
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
            validFrameAt = prefs.getLong(KEY_VALID_FRAME_AT, 0L)
        )
    }
}
