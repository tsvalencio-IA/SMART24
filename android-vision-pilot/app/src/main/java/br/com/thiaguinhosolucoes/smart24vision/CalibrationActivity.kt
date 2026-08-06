package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class CalibrationActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        val zoneView = findViewById<ZoneView>(R.id.zoneView)
        val status = findViewById<TextView>(R.id.calibrationStatus)
        val zoneIdInput = findViewById<EditText>(R.id.zoneIdInput)
        val resetButton = findViewById<Button>(R.id.resetZoneButton)
        val saveButton = findViewById<Button>(R.id.saveZoneButton)
        val actionRow = findViewById<LinearLayout>(R.id.calibrationActionRow)
        val returnButton = findViewById<Button>(R.id.returnToCaptureButton)
        val file = File(filesDir, "latest_frame.jpg")
        val capturedFrame = if (file.exists() && file.length() >= 1024L) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        } else {
            null
        }
        val usableFrame = capturedFrame != null &&
            capturedFrame.width >= 32 &&
            capturedFrame.height >= 32 &&
            !BitmapUtils.isMostlyBlack(capturedFrame)

        if (usableFrame && capturedFrame != null) {
            zoneView.bitmap = capturedFrame
            zoneView.visibility = View.VISIBLE
            zoneIdInput.visibility = View.VISIBLE
            actionRow.visibility = View.VISIBLE
            returnButton.visibility = View.GONE
            status.text = "Imagem real carregada. Marque os dois cantos da zona."
        } else {
            capturedFrame?.recycle()
            zoneView.visibility = View.GONE
            zoneIdInput.visibility = View.GONE
            actionRow.visibility = View.GONE
            returnButton.visibility = View.VISIBLE
            status.text = "SEM IMAGEM DA CÂMERA. Volte ao SMART24, toque em ‘2. Autorizar análise e abrir Yoosee’, abra o vídeo ao vivo da câmera e aguarde alguns segundos. A calibração só será liberada depois que uma imagem real for recebida."
        }

        returnButton.setOnClickListener { finish() }
        resetButton.setOnClickListener { zoneView.resetZone(); status.text = "Marcação apagada." }
        saveButton.setOnClickListener {
            if (zoneView.bitmap == null) {
                status.text = "Não é possível salvar: o SMART24 ainda não recebeu uma imagem válida da câmera."
                return@setOnClickListener
            }
            val rect = zoneView.normalizedRect()
            val zoneId = zoneIdInput.text.toString().trim().uppercase()
            if (rect == null || zoneId.isBlank()) {
                status.text = "Marque os dois cantos e informe o ID da zona."
                return@setOnClickListener
            }
            if (!PilotSession.authenticated) {
                status.text = "Sessão Firebase expirada. Volte e entre novamente."
                return@setOnClickListener
            }
            lifecycleScope.launch {
                status.text = "Salvando zona…"
                runCatching {
                    firebase.put("zones/${PilotSession.storeId}/${PilotSession.cameraId}/$zoneId", mapOf(
                        "zoneId" to zoneId,
                        "storeId" to PilotSession.storeId,
                        "cameraId" to PilotSession.cameraId,
                        "left" to rect[0],
                        "top" to rect[1],
                        "right" to rect[2],
                        "bottom" to rect[3],
                        "updatedAt" to System.currentTimeMillis(),
                        "updatedBy" to PilotSession.uid,
                        "source" to "ANDROID_SCREEN_CAPTURE_PILOT"
                    ))
                }.onSuccess {
                    status.text = "Zona $zoneId salva no Firebase. O piloto passará a usá-la em até 10 segundos."
                }.onFailure { status.text = "Falha ao salvar: ${it.message}" }
            }
        }
    }
}
