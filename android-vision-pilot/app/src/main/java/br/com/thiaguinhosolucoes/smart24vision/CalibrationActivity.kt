package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        val file = File(filesDir, "latest_frame.jpg")
        if (file.exists()) {
            zoneView.bitmap = BitmapFactory.decodeFile(file.absolutePath)
            status.text = "Imagem real carregada. Marque os dois cantos da zona."
        } else {
            status.text = "Ainda não existe imagem. Inicie a captura, abra o vídeo no Yoosee e aguarde alguns segundos."
        }
        findViewById<Button>(R.id.resetZoneButton).setOnClickListener { zoneView.resetZone(); status.text = "Marcação apagada." }
        findViewById<Button>(R.id.saveZoneButton).setOnClickListener {
            val rect = zoneView.normalizedRect()
            val zoneId = findViewById<EditText>(R.id.zoneIdInput).text.toString().trim().uppercase()
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
