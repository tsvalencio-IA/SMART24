package br.com.thiaguinhosolucoes.smart24vision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * Configura a conexão direta da câmera com o SMART24.
 *
 * A senha NVR/RTSP nunca é gravada nas preferências, enviada ao Firebase ou
 * incluída no GitHub. Ela existe somente em memória durante a tela ao vivo.
 */
class MainActivity : AppCompatActivity() {
    private val firebase = FirebaseRestClient()
    private lateinit var statusText: TextView
    private lateinit var connectDirectButton: Button
    private lateinit var cameraIpInput: EditText
    private lateinit var cameraPasswordInput: EditText

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        connectDirectButton = findViewById(R.id.connectDirectButton)
        cameraIpInput = findViewById(R.id.cameraIpInput)
        cameraPasswordInput = findViewById(R.id.cameraPasswordInput)

        restoreNonSensitiveFields()

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.openFullSmart24Button).setOnClickListener {
            startActivity(
                Intent(this, PortalActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        findViewById<Button>(R.id.loginButton).setOnClickListener { login() }
        findViewById<Button>(R.id.discoverOnvifButton).setOnClickListener { discoverOnvifCamera() }
        connectDirectButton.setOnClickListener { openDirectCamera() }
        findViewById<Button>(R.id.openLegacyCaptureButton).setOnClickListener {
            startActivity(Intent(this, LegacyCaptureActivity::class.java))
        }

        connectDirectButton.isEnabled = PilotSession.authenticated
        if (PilotSession.authenticated) {
            setStatus("Firebase já conectado nesta execução. Informe a senha NVR/RTSP e conecte diretamente.")
        }
    }

    private fun restoreNonSensitiveFields() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        findViewById<EditText>(R.id.emailInput).setText(prefs.getString("email", ""))
        findViewById<EditText>(R.id.storeInput).setText(prefs.getString("store", "loja-01"))
        findViewById<EditText>(R.id.cameraInput).setText(prefs.getString("camera", "CAM-01"))
        findViewById<EditText>(R.id.bridgeInput).setText(prefs.getString("bridge", "pilot-android-01"))
        cameraIpInput.setText(prefs.getString("direct_camera_ip", "192.168.15.5"))
        findViewById<EditText>(R.id.cameraPortInput).setText(prefs.getString("direct_camera_port", "554"))
        findViewById<EditText>(R.id.cameraUserInput).setText(prefs.getString("direct_camera_user", "admin"))
        findViewById<EditText>(R.id.cameraPathInput).setText(prefs.getString("direct_camera_path", "onvif1"))
        findViewById<EditText>(R.id.demoProductNameInput).setText(
            prefs.getString("demo_product_name", "Produto de demonstração")
        )
        findViewById<EditText>(R.id.demoSkuInput).setText(prefs.getString("demo_sku", "DEMO-001"))
        findViewById<EditText>(R.id.demoZoneInput).setText(prefs.getString("demo_zone", "PRATELEIRA-DEMO"))
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

        findViewById<Button>(R.id.loginButton).isEnabled = false
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
                PilotSession.pilotId = "$bridge-${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
                PilotSession.sessionId = "PILOT-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"
                val role = firebase.getRole(result.localId)
                require(role in setOf("admin", "operator")) {
                    "Este usuário precisa ser admin ou operator no Firebase."
                }
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString("email", email)
                    .putString("store", store)
                    .putString("camera", camera)
                    .putString("bridge", bridge)
                    .apply()
                role
            }.onSuccess { role ->
                findViewById<EditText>(R.id.passwordInput).text.clear()
                connectDirectButton.isEnabled = true
                setStatus("Firebase conectado como $role. Agora informe a senha NVR/RTSP e toque em conectar diretamente.")
            }.onFailure { error ->
                connectDirectButton.isEnabled = false
                setStatus("Falha no login: ${friendly(error.message)}")
            }
            findViewById<Button>(R.id.loginButton).isEnabled = true
        }
    }

    private fun discoverOnvifCamera() {
        val button = findViewById<Button>(R.id.discoverOnvifButton)
        button.isEnabled = false
        setStatus("Procurando câmeras ONVIF na mesma rede Wi‑Fi…")
        lifecycleScope.launch {
            runCatching { OnvifDiscovery(this@MainActivity).discover() }
                .onSuccess { cameras ->
                    val selected = cameras.firstOrNull { it.host == cameraIpInput.text.toString().trim() }
                        ?: cameras.firstOrNull()
                    if (selected == null) {
                        setStatus(
                            "Nenhuma câmera ONVIF respondeu. Confirme que o celular está no mesmo Wi‑Fi da câmera; você ainda pode usar o IP informado manualmente."
                        )
                    } else {
                        cameraIpInput.setText(selected.host)
                        setStatus(
                            "Câmera ONVIF localizada em ${selected.host}${selected.managementPort?.let { ":$it" } ?: ""}. O vídeo será aberto diretamente por RTSP."
                        )
                    }
                }
                .onFailure { error ->
                    setStatus("A busca ONVIF não terminou: ${friendly(error.message)}. Use o IP local exibido no Yoosee.")
                }
            button.isEnabled = true
        }
    }

    private fun openDirectCamera() {
        if (!PilotSession.authenticated) {
            setStatus("Entre no Firebase antes de iniciar a análise.")
            return
        }

        val host = cameraIpInput.text.toString().trim()
        val portText = findViewById<EditText>(R.id.cameraPortInput).text.toString().trim()
        val username = findViewById<EditText>(R.id.cameraUserInput).text.toString().trim()
        val cameraPassword = cameraPasswordInput.text.toString()
        val path = findViewById<EditText>(R.id.cameraPathInput).text.toString().trim().trim('/')
        val port = portText.toIntOrNull()

        when {
            !validHost(host) -> {
                setStatus("Informe somente o IP local da câmera, por exemplo 192.168.15.5.")
                return
            }
            port == null || port !in 1..65535 -> {
                setStatus("A porta RTSP é inválida. Para esta câmera, mantenha 554.")
                return
            }
            username.isBlank() -> {
                setStatus("Informe o usuário NVR/RTSP. Para esta câmera, comece com admin.")
                return
            }
            cameraPassword.isBlank() -> {
                setStatus("Digite a senha criada em Yoosee → Conexão NVR.")
                return
            }
            path.isBlank() || path.any { it.isWhitespace() || it == '@' } -> {
                setStatus("O caminho RTSP é inválido. Para o vídeo principal, mantenha onvif1.")
                return
            }
        }

        PilotSession.demoProductName = findViewById<EditText>(R.id.demoProductNameInput).text.toString().trim()
            .ifBlank { "Produto de demonstração" }
        PilotSession.demoSku = findViewById<EditText>(R.id.demoSkuInput).text.toString().trim()
            .ifBlank { "DEMO-001" }
        PilotSession.demoZoneId = findViewById<EditText>(R.id.demoZoneInput).text.toString().trim()
            .ifBlank { "PRATELEIRA-DEMO" }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString("direct_camera_ip", host)
            .putString("direct_camera_port", port.toString())
            .putString("direct_camera_user", username)
            .putString("direct_camera_path", path)
            .putString("demo_product_name", PilotSession.demoProductName)
            .putString("demo_sku", PilotSession.demoSku)
            .putString("demo_zone", PilotSession.demoZoneId)
            .apply()

        val intent = Intent(this, DirectCameraActivity::class.java).apply {
            putExtra(DirectCameraActivity.EXTRA_HOST, host)
            putExtra(DirectCameraActivity.EXTRA_PORT, port)
            putExtra(DirectCameraActivity.EXTRA_USERNAME, username)
            putExtra(DirectCameraActivity.EXTRA_PASSWORD, cameraPassword)
            putExtra(DirectCameraActivity.EXTRA_STREAM_PATH, path)
        }
        cameraPasswordInput.text.clear()
        startActivity(intent)
        setStatus("Abrindo a câmera diretamente. A senha NVR foi removida desta tela e não foi salva.")
    }

    private fun validHost(value: String): Boolean =
        value.isNotBlank() && value.length <= 253 && value.none { it.isWhitespace() || it in "/@?#" }

    private fun setStatus(value: String) {
        statusText.text = value
    }

    private fun friendly(value: String?): String = when {
        value.isNullOrBlank() -> "erro desconhecido"
        value.contains("INVALID_LOGIN_CREDENTIALS") -> "e-mail ou senha inválidos"
        value.contains("TOO_MANY_ATTEMPTS") -> "muitas tentativas; aguarde"
        else -> value
    }

    companion object {
        private const val PREFS_NAME = "smart24_pilot"
    }
}
