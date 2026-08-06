package br.com.thiaguinhosolucoes.smart24vision

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

class MainActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var calibrateButton: Button
    private lateinit var stopButton: Button
    private lateinit var yooseeShareInput: EditText
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastCaptureStatusUpdatedAt = 0L
    private var lastFrameSignature = ""
    private var lastFrameUsable = false

    private val captureStatusPoll = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                refreshCaptureUi()
                uiHandler.postDelayed(this, 700L)
            }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val qrImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        setStatus("Lendo o QR da imagem selecionada…")
        val image = runCatching { InputImage.fromFilePath(this, uri) }.getOrElse {
            setStatus("Não foi possível abrir a imagem: ${friendly(it.message)}")
            return@registerForActivityResult
        }
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
                if (value == null) {
                    setStatus("Nenhum QR legível foi encontrado nessa imagem.")
                } else {
                    yooseeShareInput.setText(value)
                    setStatus("QR lido da galeria. Toque em ‘Abrir convite no Yoosee’.")
                }
            }
            .addOnFailureListener { error ->
                setStatus("Falha ao ler o QR: ${friendly(error.message)}")
            }
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            setStatus("Captura de tela não autorizada.")
            return@registerForActivityResult
        }
        val service = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
        }
        CaptureStatusStore.reset(this, "Autorização recebida. Iniciando a captura da tela…")
        lastCaptureStatusUpdatedAt = 0L
        ContextCompat.startForegroundService(this, service)
        startButton.isEnabled = false
        calibrateButton.isEnabled = false
        calibrateButton.text = "3. Calibrar (aguardando imagem)"
        stopButton.isEnabled = true
        setStatus("Captura iniciada. Abrindo o Yoosee. Toque na câmera já cadastrada e deixe o vídeo ao vivo em tela cheia.")
        openYoosee()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        calibrateButton = findViewById(R.id.calibrateButton)
        stopButton = findViewById(R.id.stopButton)
        yooseeShareInput = findViewById(R.id.yooseeShareInput)

        val prefs = getSharedPreferences("smart24_pilot", MODE_PRIVATE)
        findViewById<EditText>(R.id.emailInput).setText(prefs.getString("email", ""))
        findViewById<EditText>(R.id.storeInput).setText(prefs.getString("store", "loja-01"))
        findViewById<EditText>(R.id.cameraInput).setText(prefs.getString("camera", "CAM-01"))
        findViewById<EditText>(R.id.bridgeInput).setText(prefs.getString("bridge", "pilot-android-01"))

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.openFullSmart24Button).setOnClickListener {
            startActivity(
                Intent(this, PortalActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<Button>(R.id.openExistingYooseeButton).setOnClickListener {
            setStatus("Abrindo o Yoosee. Use a conta que já mostra sua câmera; não é necessário ler QR novamente.")
            openYoosee()
        }
        findViewById<Button>(R.id.pasteYooseeLinkButton).setOnClickListener { pasteYooseeLink() }
        findViewById<Button>(R.id.chooseYooseeQrImageButton).setOnClickListener { qrImageLauncher.launch("image/*") }
        findViewById<Button>(R.id.openYooseeInviteButton).setOnClickListener { openYooseeInvite() }
        findViewById<Button>(R.id.loginButton).setOnClickListener { login() }
        findViewById<Button>(R.id.overlayPermissionButton).setOnClickListener { requestOverlayPermission() }
        startButton.setOnClickListener { prepareDemoAndRequestProjection() }
        calibrateButton.setOnClickListener {
            if (!hasUsableCapturedFrame()) {
                calibrateButton.isEnabled = false
                calibrateButton.text = "3. Calibrar (aguardando imagem)"
                setStatus("A calibração ainda está bloqueada porque o SMART24 não recebeu uma imagem válida. Toque em ‘2. Autorizar análise e abrir Yoosee’, abra o vídeo ao vivo e depois volte ao SMART24.")
                return@setOnClickListener
            }
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        stopButton.setOnClickListener {
            CaptureStatusStore.update(
                this,
                CaptureStatusStore.STATE_STOPPED,
                "Análise parada. Os dados já enviados permanecem no Firebase."
            )
            startService(Intent(this, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
            startButton.isEnabled = PilotSession.authenticated
            refreshCalibrationAvailability()
            stopButton.isEnabled = false
            setStatus("Análise parada. Os dados já enviados permanecem no Firebase.")
        }
        lastCaptureStatusUpdatedAt = CaptureStatusStore.snapshot(this).updatedAt
        refreshCalibrationAvailability()
    }

    override fun onStart() {
        super.onStart()
        uiHandler.removeCallbacks(captureStatusPoll)
        uiHandler.post(captureStatusPoll)
    }

    override fun onStop() {
        uiHandler.removeCallbacks(captureStatusPoll)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::calibrateButton.isInitialized) refreshCaptureUi()
    }

    private fun pasteYooseeLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (value.isBlank()) {
            setStatus("A área de transferência está vazia. Copie primeiro o link de compartilhamento do Yoosee.")
            return
        }
        yooseeShareInput.setText(value)
        setStatus("Link colado. Toque em ‘Abrir convite no Yoosee’.")
    }

    private fun openYooseeInvite() {
        val raw = yooseeShareInput.text.toString().trim()
        if (raw.isBlank()) {
            setStatus("Cole o link do convite ou escolha um print do QR primeiro.")
            return
        }
        val original = runCatching { Uri.parse(raw) }.getOrNull()
        val scheme = original?.scheme?.lowercase()
        if (original == null || scheme !in setOf("http", "https", "yoosee")) {
            setStatus("O conteúdo lido não é um link Yoosee válido.")
            return
        }
        val candidates = buildList {
            if (scheme == "yoosee") add(original)
            if (!original.encodedQuery.isNullOrBlank()) add(Uri.parse("yoosee://share?${original.encodedQuery}"))
            add(original)
        }.distinctBy(Uri::toString)

        for (uri in candidates) {
            val direct = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.yoosee")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (direct.resolveActivity(packageManager) != null && runCatching { startActivity(direct) }.isSuccess) {
                yooseeShareInput.text.clear()
                setStatus("Convite aberto no Yoosee. Conclua o compartilhamento e confirme que a câmera aparece na conta.")
                return
            }
        }

        val browser = Intent(Intent.ACTION_VIEW, original)
        if (browser.resolveActivity(packageManager) != null && runCatching { startActivity(browser) }.isSuccess) {
            yooseeShareInput.text.clear()
            setStatus("Convite aberto no navegador. Toque em abrir no Yoosee e conclua o compartilhamento.")
        } else {
            setStatus("Não foi possível abrir esse convite. Gere um novo link de compartilhamento no Yoosee.")
        }
    }

    private fun login() {
        val email = findViewById<EditText>(R.id.emailInput).text.toString().trim()
        val password = findViewById<EditText>(R.id.passwordInput).text.toString()
        val store = findViewById<EditText>(R.id.storeInput).text.toString().trim()
        val camera = findViewById<EditText>(R.id.cameraInput).text.toString().trim().uppercase()
        val bridge = findViewById<EditText>(R.id.bridgeInput).text.toString().trim()
        if (email.isBlank() || password.isBlank() || store.isBlank() || camera.isBlank() || bridge.isBlank()) {
            setStatus("Preencha e-mail, senha, loja, câmera e conector.")
            return
        }
        setStatus("Entrando no Firebase…")
        lifecycleScope.launch {
            runCatching {
                val result = firebase.login(email, password)
                PilotSession.idToken = result.idToken
                PilotSession.uid = result.localId
                PilotSession.email = result.email
                PilotSession.storeId = store
                PilotSession.cameraId = camera
                PilotSession.bridgeId = bridge
                PilotSession.pilotId = "${bridge}-${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
                PilotSession.sessionId = "PILOT-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"
                val role = firebase.getRole(result.localId)
                require(role in setOf("admin", "operator")) { "Este usuário precisa ser admin ou operator no Firebase." }
                getSharedPreferences("smart24_pilot", MODE_PRIVATE).edit()
                    .putString("email", email).putString("store", store).putString("camera", camera).putString("bridge", bridge).apply()
                role
            }.onSuccess { role ->
                findViewById<EditText>(R.id.passwordInput).text.clear()
                startButton.isEnabled = true
                refreshCalibrationAvailability()
                setStatus("Firebase conectado como $role. A senha foi descartada da tela e não foi salva.")
            }.onFailure { error -> setStatus("Falha no login: ${friendly(error.message)}") }
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            setStatus("Controle flutuante já está autorizado. Você poderá usar PEGOU, DEVOLVEU, ESCONDEU e ALERTA sobre o Yoosee.")
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onSuccess { setStatus("Autorize 'Exibir sobre outros apps' e volte ao SMART24.") }
            .onFailure { setStatus("Não foi possível abrir a permissão de sobreposição: ${friendly(it.message)}") }
    }

    private fun prepareDemoAndRequestProjection() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Antes da demonstração, autorize o controle flutuante para operar sobre o Yoosee.")
            requestOverlayPermission()
            return
        }
        PilotSession.demoProductName = findViewById<EditText>(R.id.demoProductNameInput).text.toString().trim()
            .ifBlank { "Produto de demonstração" }
        PilotSession.demoSku = findViewById<EditText>(R.id.demoSkuInput).text.toString().trim()
            .ifBlank { "DEMO-001" }
        PilotSession.demoZoneId = findViewById<EditText>(R.id.demoZoneInput).text.toString().trim()
            .ifBlank { "PRATELEIRA-DEMO" }
        requestProjection()
    }

    private fun requestProjection() {
        if (!PilotSession.authenticated) {
            setStatus("Entre no Firebase primeiro.")
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun openYoosee() {
        val packageName = "com.yoosee"
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { startActivity(launchIntent) }
                .onSuccess { setStatus("Yoosee aberto. Abra o vídeo ao vivo; o SMART24 continua capturando e liberará a calibração quando receber a primeira imagem.") }
                .onFailure { error -> setStatus("O Yoosee foi localizado, mas não abriu: ${friendly(error.message)}") }
            return
        }
        val fallback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val component = fallback.resolveActivity(packageManager)
        if (component != null) {
            fallback.component = component
            fallback.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { startActivity(fallback) }
                .onSuccess { setStatus("Yoosee aberto. Abra o vídeo ao vivo; o SMART24 continua capturando e liberará a calibração quando receber a primeira imagem.") }
                .onFailure { error -> setStatus("Não foi possível abrir o Yoosee: ${friendly(error.message)}") }
        } else {
            setStatus("Yoosee não foi localizado. Abra-o manualmente; a captura continuará ativa quando autorizada.")
        }
    }

    private fun refreshCalibrationAvailability() {
        val ready = PilotSession.authenticated && hasUsableCapturedFrame()
        calibrateButton.isEnabled = ready
        calibrateButton.text = if (ready) {
            "3. Calibrar prateleira na imagem"
        } else {
            "3. Calibrar (aguardando imagem)"
        }
    }

    private fun hasUsableCapturedFrame(): Boolean {
        val file = File(filesDir, "latest_frame.jpg")
        val signature = if (file.exists()) {
            "${file.length()}:${file.lastModified()}"
        } else {
            "missing"
        }
        if (signature == lastFrameSignature) return lastFrameUsable
        lastFrameSignature = signature
        lastFrameUsable = false
        if (!file.exists() || file.length() < 1024L) {
            return false
        }
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return false
        lastFrameUsable = try {
            bitmap.width >= 32 && bitmap.height >= 32 && !BitmapUtils.isMostlyBlack(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return lastFrameUsable
    }

    private fun refreshCaptureUi() {
        if (!::calibrateButton.isInitialized) return
        refreshCalibrationAvailability()
        val snapshot = CaptureStatusStore.snapshot(this)
        if (snapshot.updatedAt <= lastCaptureStatusUpdatedAt) return
        lastCaptureStatusUpdatedAt = snapshot.updatedAt

        when (snapshot.state) {
            CaptureStatusStore.STATE_STARTING,
            CaptureStatusStore.STATE_WAITING_VIDEO,
            CaptureStatusStore.STATE_NO_IMAGE,
            CaptureStatusStore.STATE_VIDEO_VISIBLE,
            CaptureStatusStore.STATE_DEGRADED -> {
                startButton.isEnabled = false
                stopButton.isEnabled = true
                if (snapshot.message.isNotBlank()) setStatus(snapshot.message)
            }

            CaptureStatusStore.STATE_ERROR -> {
                startButton.isEnabled = PilotSession.authenticated
                stopButton.isEnabled = false
                if (snapshot.message.isNotBlank()) setStatus(snapshot.message)
            }

            CaptureStatusStore.STATE_STOPPED -> {
                startButton.isEnabled = PilotSession.authenticated
                stopButton.isEnabled = false
                if (snapshot.message.isNotBlank()) setStatus(snapshot.message)
            }
        }
    }

    private fun setStatus(text: String) { statusText.text = text }
    private fun friendly(value: String?): String = when {
        value.isNullOrBlank() -> "erro desconhecido"
        value.contains("INVALID_LOGIN_CREDENTIALS") -> "e-mail ou senha inválidos"
        value.contains("TOO_MANY_ATTEMPTS") -> "muitas tentativas; aguarde"
        else -> value
    }
}
