package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class MobileCalibrationActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_calibration)

        val storeId = intent.getStringExtra("storeId").orEmpty().ifBlank { "loja-01" }
        val cameraId = intent.getStringExtra("cameraId").orEmpty().ifBlank { "CAM-01" }

        val zoneView = findViewById<ZoneView>(R.id.mobileZoneView)
        val status = findViewById<TextView>(R.id.mobileCalibrationStatus)
        val file = File(filesDir, "latest_mobile_frame.jpg")

        if (file.exists()) {
            zoneView.bitmap = BitmapFactory.decodeFile(file.absolutePath)
            status.text = "Toque em dois cantos para marcar a área ocupada por UM SKU."
        } else {
            status.text = "Nenhum quadro capturado ainda."
        }

        findViewById<Button>(R.id.mobileResetZoneButton).setOnClickListener {
            zoneView.resetZone()
            status.text = "Marcação apagada."
        }

        findViewById<Button>(R.id.mobileClearZonesButton).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Apagar zonas locais?")
                .setMessage("Apagar as marcações salvas neste celular para $storeId / $cameraId?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar") { _, _ ->
                    MobileZoneStore.clear(this, storeId, cameraId)
                    status.text = "Zonas locais apagadas. As cópias no Firebase permanecem registradas."
                }.show()
        }

        findViewById<Button>(R.id.mobileSaveZoneButton).setOnClickListener {
            val rect = zoneView.normalizedRect()
            val zoneId = findViewById<EditText>(R.id.mobileZoneIdInput).text.toString().trim().uppercase()
            val productName = findViewById<EditText>(R.id.mobileProductNameInput).text.toString().trim()
            val sku = findViewById<EditText>(R.id.mobileSkuInput).text.toString().trim().uppercase()

            if (rect == null || zoneId.isBlank() || productName.isBlank() || sku.isBlank()) {
                status.text = "Marque a zona e informe ID, produto e SKU."
                return@setOnClickListener
            }

            val zone = Zone(
                zoneId = zoneId,
                storeId = storeId,
                cameraId = cameraId,
                left = rect[0], top = rect[1], right = rect[2], bottom = rect[3],
                productId = sku,
                productName = productName,
                sku = sku
            )
            MobileZoneStore.upsert(this, zone)

            if (PilotSession.authenticated) {
                lifecycleScope.launch {
                    runCatching {
                        firebase.put(
                            "zones/$storeId/$cameraId/$zoneId",
                            mapOf(
                                "zoneId" to zoneId,
                                "storeId" to storeId,
                                "cameraId" to cameraId,
                                "left" to rect[0],
                                "top" to rect[1],
                                "right" to rect[2],
                                "bottom" to rect[3],
                                "productId" to sku,
                                "productName" to productName,
                                "sku" to sku,
                                "coordinateSpace" to "MOBILE_TEXTURE_V1",
                                "updatedAt" to System.currentTimeMillis(),
                                "source" to "SMART24_MOBILE_RTSP"
                            )
                        )
                    }.onSuccess {
                        status.text = "Zona $zoneId → $sku salva no celular e sincronizada com o Firebase."
                    }.onFailure {
                        status.text = "Zona salva no celular. Firebase não sincronizou: ${it.message}. Toque em Salvar SKU novamente para tentar."
                    }
                }
            }

            status.text = "Zona $zoneId → $sku salva. Você pode marcar outra zona."
            zoneView.resetZone()
        }
    }
}
