package br.com.thiaguinhosolucoes.smart24vision

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var calibrateButton: Button
    private lateinit var stopButton: Button

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
        ContextCompat.startForegroundService(this, service)
        startButton.isEnabled = false
        calibrateButton.isEnabled = true
        stopButton.isEnabled = true
        setStatus("Captura iniciada. Abrindo o Yoosee. Entre no vídeo ao vivo e deixe-o em tela cheia.")
        openYoosee()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        calibrateButton = findViewById(R.id.calibrateButton)
        stopButton = findViewById(R.id.stopButton)

        val prefs = getSharedPreferences("smart24_pilot", MODE_PRIVATE)
        findViewById<EditText>(R.id.emailInput).setText(prefs.getString("email", ""))
        findViewById<EditText>(R.id.storeInput).setText(prefs.getString("store", "loja-01"))
        findViewById<EditText>(R.id.cameraInput).setText(prefs.getString("camera", "CAM-01"))
        findViewById<EditText>(R.id.bridgeInput).setText(prefs.getString("bridge", "pilot-android-01"))

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.loginButton).setOnClickListener { login() }
        startButton.setOnClickListener { requestProjection() }
        calibrateButton.setOnClickListener { startActivity(Intent(this, CalibrationActivity::class.java)) }
        stopButton.setOnClickListener {
            startService(Intent(this, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
            startButton.isEnabled = PilotSession.authenticated
            calibrateButton.isEnabled = true
            stopButton.isEnabled = false
            setStatus("Análise parada. Os dados já enviados permanecem no Firebase.")
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
                calibrateButton.isEnabled = true
                setStatus("Firebase conectado como $role. A senha foi descartada da tela e não foi salva.")
            }.onFailure { error -> setStatus("Falha no login: ${friendly(error.message)}") }
        }
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
        val launch = packageManager.getLaunchIntentForPackage("com.yoosee")
        if (launch != null) startActivity(launch)
        else setStatus("Yoosee não encontrado. Instale ou abra o aplicativo Yoosee manualmente.")
    }

    private fun setStatus(text: String) { statusText.text = text }
    private fun friendly(value: String?): String = when {
        value.isNullOrBlank() -> "erro desconhecido"
        value.contains("INVALID_LOGIN_CREDENTIALS") -> "e-mail ou senha inválidos"
        value.contains("TOO_MANY_ATTEMPTS") -> "muitas tentativas; aguarde"
        else -> value
    }
}
