package br.com.thiaguinhosolucoes.smart24vision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var overlayButton: Button

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                setStatus("A análise da tela não foi autorizada.")
                return@registerForActivityResult
            }

            val service = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
            }

            ContextCompat.startForegroundService(this, service)
            startButton.isEnabled = false
            stopButton.isEnabled = true

            setStatus(
                "Teste iniciado. O Yoosee será aberto. Entre na câmera e deixe o vídeo ao vivo em tela cheia. " +
                    "O painel SMART24 deve aparecer por cima."
            )
            openYoosee()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        overlayButton = findViewById(R.id.overlayButton)

        val prefs = getSharedPreferences("smart24_pilot_clean", MODE_PRIVATE)
        findViewById<EditText>(R.id.emailInput).setText(prefs.getString("email", ""))
        findViewById<EditText>(R.id.storeInput).setText(prefs.getString("store", "loja-01"))
        findViewById<EditText>(R.id.cameraInput).setText(prefs.getString("camera", "CAM-01"))
        findViewById<EditText>(R.id.bridgeInput).setText(prefs.getString("bridge", "pilot-android-demo-01"))

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.openYooseeButton).setOnClickListener {
            openYoosee()
        }

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            login()
        }

        overlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        startButton.setOnClickListener {
            requestProjection()
        }

        stopButton.setOnClickListener {
            startService(
                Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_STOP
                }
            )
            startButton.isEnabled = PilotSession.authenticated
            stopButton.isEnabled = false
            setStatus("Teste encerrado.")
        }

        refreshOverlayState()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
    }

    private fun login() {
        val email = findViewById<EditText>(R.id.emailInput).text.toString().trim()
        val password = findViewById<EditText>(R.id.passwordInput).text.toString()
        val store = findViewById<EditText>(R.id.storeInput).text.toString().trim()
        val camera = findViewById<EditText>(R.id.cameraInput).text
            .toString()
            .trim()
            .uppercase(Locale.ROOT)
        val bridge = findViewById<EditText>(R.id.bridgeInput).text.toString().trim()

        if (
            email.isBlank() ||
            password.isBlank() ||
            store.isBlank() ||
            camera.isBlank() ||
            bridge.isBlank()
        ) {
            setStatus("Preencha e-mail, senha, loja, câmera e conector.")
            return
        }

        setStatus("Conectando ao Firebase…")

        lifecycleScope.launch {
            runCatching {
                val result = firebase.login(email, password)

                PilotSession.idToken = result.idToken
                PilotSession.uid = result.localId
                PilotSession.email = result.email
                PilotSession.storeId = store
                PilotSession.cameraId = camera
                PilotSession.bridgeId = bridge
                PilotSession.pilotId =
                    "$bridge-${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
                PilotSession.sessionId =
                    "DEMO-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"

                val role = firebase.getRole(result.localId)
                require(role in setOf("admin", "operator")) {
                    "O usuário precisa ter função admin ou operator no Firebase."
                }

                getSharedPreferences("smart24_pilot_clean", MODE_PRIVATE)
                    .edit()
                    .putString("email", email)
                    .putString("store", store)
                    .putString("camera", camera)
                    .putString("bridge", bridge)
                    .apply()

                role
            }.onSuccess { role ->
                findViewById<EditText>(R.id.passwordInput).text.clear()
                startButton.isEnabled = true
                setStatus(
                    "Firebase conectado como $role. Agora autorize o painel flutuante e inicie o teste."
                )
            }.onFailure { error ->
                setStatus("Falha no Firebase: ${friendly(error.message)}")
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            setStatus("Painel flutuante já está autorizado.")
            refreshOverlayState()
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestProjection() {
        if (!PilotSession.authenticated) {
            setStatus("Primeiro entre no Firebase.")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            setStatus("Primeiro autorize 'Exibir sobre outros apps' para o SMART24.")
            requestOverlayPermission()
            return
        }

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun refreshOverlayState() {
        overlayButton.text = if (Settings.canDrawOverlays(this)) {
            "Painel flutuante autorizado ✓"
        } else {
            "2. Autorizar painel flutuante"
        }
    }

    private fun openYoosee() {
        val packageName = "com.yoosee"
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            runCatching { startActivity(launchIntent) }
                .onSuccess {
                    setStatus(
                        "Yoosee aberto. Entre na câmera e confirme que o vídeo ao vivo funciona."
                    )
                }
                .onFailure { error ->
                    setStatus("Yoosee foi localizado, mas não abriu: ${friendly(error.message)}")
                }
            return
        }

        setStatus(
            "Yoosee não foi localizado automaticamente. Abra o aplicativo Yoosee manualmente."
        )
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun friendly(value: String?): String = when {
        value.isNullOrBlank() -> "erro desconhecido"
        value.contains("INVALID_LOGIN_CREDENTIALS") -> "e-mail ou senha inválidos"
        value.contains("TOO_MANY_ATTEMPTS") -> "muitas tentativas; aguarde"
        value.contains("PERMISSION_DENIED") -> "o Firebase recusou a gravação para este usuário"
        else -> value
    }
}
