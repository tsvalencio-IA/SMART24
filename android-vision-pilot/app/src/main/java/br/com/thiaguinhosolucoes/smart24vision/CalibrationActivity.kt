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
    companion object {
        const val EXTRA_MODE = "calibration_mode"
        const val MODE_VIEWPORT = "VIEWPORT"
        const val MODE_ZONE = "ZONE"
    }

    private val firebase = FirebaseRestClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        val zoneView = findViewById<ZoneView>(R.id.zoneView)
        val title = findViewById<TextView>(R.id.calibrationTitle)
        val instructions = findViewById<TextView>(R.id.calibrationInstructions)
        val status = findViewById<TextView>(R.id.calibrationStatus)
        val zoneIdInput = findViewById<EditText>(R.id.zoneIdInput)
        val resetButton = findViewById<Button>(R.id.resetZoneButton)
        val saveButton = findViewById<Button>(R.id.saveZoneButton)
        val actionRow = findViewById<LinearLayout>(R.id.calibrationActionRow)
        val returnButton = findViewById<Button>(R.id.returnToCaptureButton)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ZONE
        val viewportMode = mode == MODE_VIEWPORT
        val file = File(filesDir, if (viewportMode) "latest_screen_frame.jpg" else "latest_frame.jpg")

        if (viewportMode) {
            title.text = "Delimitar o vídeo real da câmera"
            instructions.text = "Toque no canto superior esquerdo e depois no canto inferior direito somente da imagem ao vivo. Não inclua menus do Yoosee, botões, teclado ou o SMART24."
            zoneIdInput.visibility = View.GONE
            resetButton.text = "REFAZER ÁREA"
            saveButton.text = "SALVAR ÁREA DO VÍDEO"
        } else {
            title.text = "Calibrar zona da prateleira"
            instructions.text = "A imagem abaixo já deve conter somente a câmera. Toque no canto superior esquerdo e depois no canto inferior direito da prateleira/geladeira."
            resetButton.text = "REFAZER ZONA"
            saveButton.text = "SALVAR ZONA"
        }
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
            zoneIdInput.visibility = if (viewportMode) View.GONE else View.VISIBLE
            actionRow.visibility = View.VISIBLE
            returnButton.visibility = View.GONE
            status.text = if (viewportMode) {
                "Tela capturada carregada. Marque somente os limites da imagem ao vivo da câmera."
            } else {
                "Imagem recortada da câmera carregada. Marque os dois cantos da zona."
            }
        } else {
            capturedFrame?.recycle()
            zoneView.visibility = View.GONE
            zoneIdInput.visibility = View.GONE
            actionRow.visibility = View.GONE
            returnButton.visibility = View.VISIBLE
            status.text = if (viewportMode) {
                "SEM CAPTURA DA TELA. Volte ao SMART24, autorize a análise, abra o vídeo ao vivo no Yoosee e aguarde alguns segundos."
            } else {
                "SEM RECORTE DA CÂMERA. Primeiro delimite a área do vídeo; depois deixe o Yoosee visível por alguns segundos para o SMART24 gerar a imagem recortada."
            }
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
            if (rect == null || (!viewportMode && zoneId.isBlank())) {
                status.text = if (viewportMode) {
                    "Marque os dois cantos da imagem real da câmera."
                } else {
                    "Marque os dois cantos e informe o ID da zona."
                }
                return@setOnClickListener
            }
            if (!PilotSession.authenticated) {
                status.text = "Sessão Firebase expirada. Volte e entre novamente."
                return@setOnClickListener
            }
            lifecycleScope.launch {
                status.text = if (viewportMode) "Salvando área do vídeo…" else "Salvando zona…"
                runCatching {
                    val now = System.currentTimeMillis()
                    if (viewportMode) {
                        val viewport = CameraViewport(
                            storeId = PilotSession.storeId,
                            cameraId = PilotSession.cameraId,
                            left = rect[0],
                            top = rect[1],
                            right = rect[2],
                            bottom = rect[3],
                            updatedAt = now
                        )
                        require(viewport.valid) { "A área marcada é pequena ou inválida. Marque todo o vídeo ao vivo." }
                        firebase.put(
                            "cameraViewports/${PilotSession.storeId}/${PilotSession.cameraId}",
                            mapOf(
                                "storeId" to viewport.storeId,
                                "cameraId" to viewport.cameraId,
                                "left" to viewport.left,
                                "top" to viewport.top,
                                "right" to viewport.right,
                                "bottom" to viewport.bottom,
                                "coordinateSpace" to CoordinateSpaces.CAMERA_VIEWPORT_V1,
                                "updatedAt" to now,
                                "updatedBy" to PilotSession.uid,
                                "source" to "ANDROID_SCREEN_CAPTURE_PILOT"
                            )
                        )
                        CameraViewportStore.save(this@CalibrationActivity, viewport)
                        runCatching { File(filesDir, "latest_frame.jpg").delete() }
                    } else {
                        firebase.put("zones/${PilotSession.storeId}/${PilotSession.cameraId}/$zoneId", mapOf(
                            "zoneId" to zoneId,
                            "storeId" to PilotSession.storeId,
                            "cameraId" to PilotSession.cameraId,
                            "left" to rect[0],
                            "top" to rect[1],
                            "right" to rect[2],
                            "bottom" to rect[3],
                            "coordinateSpace" to CoordinateSpaces.CAMERA_VIEWPORT_V1,
                            "updatedAt" to now,
                            "updatedBy" to PilotSession.uid,
                            "source" to "ANDROID_SCREEN_CAPTURE_PILOT"
                        ))
                    }
                }.onSuccess {
                    status.text = if (viewportMode) {
                        "Área do vídeo salva. Volte ao Yoosee e aguarde alguns segundos; depois calibre a prateleira sobre a imagem já recortada."
                    } else {
                        "Zona $zoneId salva no Firebase. O piloto passará a usá-la em até 10 segundos."
                    }
                }.onFailure { status.text = "Falha ao salvar: ${it.message}" }
            }
        }
    }
}
