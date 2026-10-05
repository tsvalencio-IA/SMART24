package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MobileCalibrationActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()
    private lateinit var zoneView: ZoneView
    private lateinit var status: TextView
    private var store = ""
    private var camera = ""
    private var editingId: String? = null
    private var savedDraft = false
    private fun input(id: Int) = findViewById<EditText>(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_calibration)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mobileCalibrationRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        store = intent.getStringExtra("storeId").orEmpty().ifBlank { "loja-01" }
        camera = intent.getStringExtra("cameraId").orEmpty().ifBlank { "CAM-01" }
        zoneView = findViewById(R.id.mobileZoneView)
        status = findViewById(R.id.mobileCalibrationStatus)
        findViewById<TextView>(R.id.mobileCalibrationIdentity).text = "$store / $camera • foto capturada da câmera"
        zoneView.bitmap = BitmapFactory.decodeFile(File(filesDir,"latest_mobile_frame.jpg").absolutePath)
        zoneView.onSelectionChanged = { count ->
            savedDraft = false
            status.text = when (count) {
                0 -> "1. Marque na imagem o espaço ocupado por um produto."
                1 -> "Agora toque no canto oposto para fechar a área."
                else -> "Área marcada. 2. Informe o produto e toque em Salvar."
            }
        }
        editingId = savedInstanceState?.getString("editingId")
        zoneView.setNormalizedRect(savedInstanceState?.getFloatArray("rect"))
        savedDraft = savedInstanceState?.getBoolean("savedDraft") ?: false
        if (zoneView.bitmap == null) {
            status.text = "A foto não está disponível. Volte ao vídeo e toque em Configurar produtos novamente."
            findViewById<Button>(R.id.mobileSaveZoneButton).isEnabled = false
        }
        findViewById<Button>(R.id.mobileResetZoneButton).setOnClickListener { zoneView.resetZone() }
        findViewById<Button>(R.id.mobileNewZoneButton).setOnClickListener { confirmDiscard { newDraft() } }
        findViewById<Button>(R.id.mobileSaveZoneButton).setOnClickListener { saveZone() }
        findViewById<Button>(R.id.mobileFinishZonesButton).setOnClickListener { confirmDiscard { finish() } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmDiscard { finish() } }
        })
        findViewById<Button>(R.id.mobileClearZonesButton).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Apagar áreas locais?")
                .setMessage("Apagar as áreas desta câmera neste celular? Cópias já enviadas ao Firebase permanecem registradas.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Apagar") { _, _ ->
                    MobileZoneStore.clear(this,store,camera); newDraft(); refreshList()
                }.show()
        }
        listOf(R.id.mobileProductNameInput,R.id.mobileSkuInput,R.id.mobileLocationInput).forEach { id ->
            input(id).doAfterTextChanged { savedDraft = false }
        }
        refreshList()
    }
    private fun confirmDiscard(action: () -> Unit) {
        if (savedDraft || (zoneView.normalizedRect() == null && input(R.id.mobileProductNameInput).text.isBlank())) { action(); return }
        AlertDialog.Builder(this).setTitle("Alterações ainda não salvas")
            .setMessage("Deseja descartar esta marcação? As áreas já salvas serão mantidas.")
            .setNegativeButton("Continuar editando", null).setPositiveButton("Descartar") { _, _ -> action() }.show()
    }
    private fun newDraft() {
        editingId = null; savedDraft = false
        input(R.id.mobileProductNameInput).text.clear(); input(R.id.mobileSkuInput).text.clear()
        zoneView.resetZone()
        findViewById<Button>(R.id.mobileSaveZoneButton).text = "SALVAR PRODUTO NESTA ÁREA"
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(zoneView.windowToken,0)
        currentFocus?.clearFocus()
        findViewById<ScrollView>(R.id.mobileCalibrationRoot).smoothScrollTo(0,0)
    }
    private fun saveZone() {
        val rect = zoneView.normalizedRect()
        val name = input(R.id.mobileProductNameInput).text.toString().trim()
        if (rect == null) { status.text = "Marque os dois cantos da área na imagem."; return }
        if (name.isBlank()) { input(R.id.mobileProductNameInput).error = "Informe o nome do produto"; return }
        val existing = MobileZoneStore.load(this,store,camera).firstOrNull { it.zoneId == editingId }
        val sku = input(R.id.mobileSkuInput).text.toString().trim().uppercase()
            .ifBlank { existing?.sku ?: "LOCAL-${UUID.randomUUID().toString().take(8).uppercase()}" }
        val zone = Zone(editingId ?: "Z-${UUID.randomUUID()}",store,camera,rect[0],rect[1],rect[2],rect[3],
            existing?.takeIf { it.sku == sku }?.productId ?: sku,name,sku,
            locationName = input(R.id.mobileLocationInput).text.toString().trim())
        MobileZoneStore.upsert(this,zone)
        editingId = zone.zoneId
        input(R.id.mobileSkuInput).setText(sku)
        savedDraft = true
        findViewById<Button>(R.id.mobileSaveZoneButton).text = "SALVAR ALTERAÇÕES DESTE PRODUTO"
        currentFocus?.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(zoneView.windowToken,0)
        refreshList()
        status.text = "$name salvo neste celular. Cadastre outro produto ou conclua para voltar ao vídeo."
        if (PilotSession.authenticated) lifecycleScope.launch {
            try {
                firebase.put("zones/$store/$camera/${zone.zoneId}", mapOf(
                    "zoneId" to zone.zoneId,"storeId" to store,"cameraId" to camera,
                    "left" to zone.left,"top" to zone.top,"right" to zone.right,"bottom" to zone.bottom,
                    "productId" to zone.productId,"productName" to name,"sku" to sku,"locationName" to zone.locationName,
                    "coordinateSpace" to zone.coordinateSpace,"updatedAt" to System.currentTimeMillis(),"source" to "SMART24_MOBILE_RTSP"))
                if (editingId == zone.zoneId) status.text = "$name salvo no celular e sincronizado. Cadastre outro ou conclua."
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                if (editingId == zone.zoneId) status.text = "$name salvo no celular. Não foi sincronizado; toque em Salvar alterações para tentar novamente."
            }
        }
    }
    private fun refreshList() {
        val zones = MobileZoneStore.load(this,store,camera)
        zoneView.savedZones = zones
        findViewById<TextView>(R.id.mobileSavedZonesTitle).text = "Áreas salvas (${zones.size}) — toque para editar"
        val list = findViewById<LinearLayout>(R.id.mobileSavedZonesList); list.removeAllViews()
        zones.forEachIndexed { index, zone ->
            val row = Button(this).apply {
                text = "${index+1}. ${zone.productName}\n${zone.locationName.ifBlank { "Área ${index+1}" }} • ${zone.sku}"
                isAllCaps = false
                setOnClickListener { confirmDiscard { editZone(zone) } }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MobileCalibrationActivity).setTitle("Remover ${zone.productName}?")
                        .setMessage("Remover apenas esta área do celular? A cópia já enviada ao Firebase permanece.")
                        .setNegativeButton("Cancelar",null).setPositiveButton("Remover") { _, _ ->
                            MobileZoneStore.remove(this@MobileCalibrationActivity,store,camera,zone.zoneId)
                            if (editingId == zone.zoneId) newDraft()
                            refreshList()
                        }.show(); true
                }
            }
            list.addView(row)
        }
    }
    private fun editZone(zone: Zone) {
        editingId = zone.zoneId
        input(R.id.mobileProductNameInput).setText(zone.productName)
        input(R.id.mobileSkuInput).setText(zone.sku)
        input(R.id.mobileLocationInput).setText(zone.locationName)
        zoneView.setNormalizedRect(floatArrayOf(zone.left,zone.top,zone.right,zone.bottom))
        savedDraft = true
        findViewById<Button>(R.id.mobileSaveZoneButton).text = "SALVAR ALTERAÇÕES DESTE PRODUTO"
        status.text = "Editando ${zone.productName}. Ajuste a área ou os dados e salve."
        findViewById<ScrollView>(R.id.mobileCalibrationRoot).smoothScrollTo(0,0)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("editingId",editingId); outState.putFloatArray("rect",zoneView.normalizedRect()); outState.putBoolean("savedDraft",savedDraft)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        val bitmap = zoneView.bitmap; zoneView.bitmap = null; bitmap?.recycle()
        super.onDestroy()
    }
}
