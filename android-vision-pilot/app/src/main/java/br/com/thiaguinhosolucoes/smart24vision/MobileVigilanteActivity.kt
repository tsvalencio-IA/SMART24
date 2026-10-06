package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.content.ActivityNotFoundException
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.TextureView
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class MobileVigilanteActivity : AppCompatActivity(), MobileRtspPlayer.Listener {
    private lateinit var textureView: TextureView
    private lateinit var overlay: MobileOverlayView
    private lateinit var status: TextView
    private lateinit var eventText: TextView
    private lateinit var syncText: TextView
    private lateinit var rtspPlayer: MobileRtspPlayer
    private lateinit var outbox: MobileEventOutbox
    private val visionDelegate = lazy { VisionEngine() }
    private val vision by visionDelegate
    private val itemEngine = MobileItemEngine()
    private val firebase = FirebaseRestClient()
    private val handler = Handler(Looper.getMainLooper())
    private val syncMutex = Mutex()
    private var zones: List<Zone> = emptyList()
    private var analyzing = false
    private var analysisBusy = false
    private var generation = 0
    private var resumed = false
    private var destroyed = false
    private var sessionId = "MOBILE-${UUID.randomUUID()}"
    private var connectionJob: Job? = null
    private var connectionGeneration = 0
    private var connectionReport = ""
    private var knownOnvifServices: List<String> = emptyList()

    private companion object {
        const val OFFICE_STORE = "OFICINA"
        const val OFFICE_CAMERA = "SALA"
        const val OFFICE_HOST = "192.168.15.5"
        const val OFFICE_RTSP_PORT = "554"
        const val OFFICE_DEFAULT_USER = ""
        const val OFFICE_DEVICE_ID = "5646988473"
        const val OFFICE_MAC = "38:7A:CC:3A:4D:8E"
    }

    private fun input(id: Int) = findViewById<EditText>(id)
    private fun storeId() = input(R.id.mobileStoreInput).text.toString().trim().ifBlank { "loja-01" }
    private fun cameraId() = input(R.id.mobileCameraInput).text.toString().trim().uppercase().ifBlank { "CAM-01" }
    private fun validKey(value: String) = value.isNotBlank() && !value.contains(Regex("[.#$\\[\\]/\\u0000-\\u001f]"))

    private val analysisLoop = object : Runnable {
        override fun run() {
            if (!analyzing || !resumed) return
            if (!analysisBusy) {
                val bitmap = rtspPlayer.captureFrame()
                if (bitmap != null) {
                    analysisBusy = true
                    val token = generation
                    lifecycleScope.launch {
                        try {
                            if (BitmapUtils.isMostlyBlack(bitmap)) {
                                status.text = "Quadro escuro ou sem imagem útil. Nenhuma retirada será inferida deste quadro."
                            } else {
                                val result = vision.analyze(bitmap)
                                if (token != generation || !analyzing || !resumed) return@launch
                                overlay.result = result
                                val events = itemEngine.update(bitmap, result, zones)
                                events.forEach { publishEvent(it) }
                                status.text = "Vigilante ativo • pessoas ${result.persons.size} • objetos ${result.objects.size} • zonas ${zones.size}"
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            status.text = "Falha na análise: ${error.message ?: error.javaClass.simpleName}"
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            analysisBusy = false
                            if (destroyed && visionDelegate.isInitialized()) vision.close()
                        }
                    }
                }
            }
            handler.postDelayed(this, 900L)
        }
    }
    private val syncLoop = object : Runnable {
        override fun run() {
            if (!resumed) return
            synchronizeEvents()
            handler.postDelayed(this, 15000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_vigilante)
        val root = findViewById<android.view.View>(R.id.mobileRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        textureView = findViewById(R.id.mobileVideoTexture)
        overlay = findViewById(R.id.mobileOverlay)
        status = findViewById(R.id.mobileStatus)
        eventText = findViewById(R.id.mobileEventText)
        syncText = findViewById(R.id.mobileSyncText)
        rtspPlayer = MobileRtspPlayer(this, textureView, this)
        outbox = MobileEventOutbox(this)
        val prefs = getSharedPreferences("smart24_mobile", MODE_PRIVATE)
        val savedStore = prefs.getString("store", null)
        val savedCamera = prefs.getString("camera", null)
        val savedUser = prefs.getString("user", null)
        val savedPort = prefs.getString("port", null)
        val savedHost = prefs.getString("host", null)
        val officeStore = if (savedStore.isNullOrBlank() || savedStore == "loja-01") OFFICE_STORE else savedStore
        val officeCamera = if (savedCamera.isNullOrBlank() || savedCamera == "CAM-01") OFFICE_CAMERA else savedCamera
        val officeUser = if (savedUser.isNullOrBlank()) OFFICE_DEFAULT_USER else savedUser
        val officePort = if (savedPort.isNullOrBlank()) OFFICE_RTSP_PORT else savedPort
        val officeHost = if (savedHost.isNullOrBlank()) OFFICE_HOST else savedHost
        input(R.id.mobileHostInput).setText(officeHost)
        input(R.id.mobileUserInput).setText(officeUser)
        input(R.id.mobilePortInput).setText(officePort)
        input(R.id.mobileFirebaseEmailInput).setText(prefs.getString("email", ""))
        input(R.id.mobileStoreInput).setText(officeStore)
        input(R.id.mobileCameraInput).setText(officeCamera)
        if (savedStore.isNullOrBlank() || savedStore == "loja-01" || savedCamera.isNullOrBlank() || savedCamera == "CAM-01") {
            prefs.edit()
                .putString("host", OFFICE_HOST)
                .putString("user", OFFICE_DEFAULT_USER)
                .putString("port", OFFICE_RTSP_PORT)
                .putString("store", OFFICE_STORE)
                .putString("camera", OFFICE_CAMERA)
                .apply()
        }
        findViewById<Button>(R.id.mobileConnectButton).setOnClickListener { connectCamera() }
        findViewById<Button>(R.id.mobileFirebaseLoginButton).setOnClickListener { loginFirebase() }
        findViewById<Button>(R.id.mobileCalibrateButton).setOnClickListener { captureAndCalibrate() }
        findViewById<Button>(R.id.mobileStartAiButton).setOnClickListener { startAi() }
        findViewById<Button>(R.id.mobileStopAiButton).setOnClickListener { stopAi(); updateGuide(); status.text = "Análise parada; o vídeo permanece aberto." }
        findViewById<Button>(R.id.mobileDisconnectButton).setOnClickListener {
            cancelConnectionCheck()
            stopAi(); rtspPlayer.disconnect(); cameraControls(false)
            input(R.id.mobileCameraPasswordInput).text.clear(); input(R.id.mobileRtspUrlInput).text.clear()
            input(R.id.mobileStoreInput).isEnabled = true
            input(R.id.mobileCameraInput).isEnabled = true
            findViewById<View>(R.id.mobileConnectionSettings).visibility = View.VISIBLE
            status.text = "Câmera desconectada. Credenciais descartadas."
        }
        findViewById<Button>(R.id.mobileDiscoverButton).setOnClickListener { discoverCameras() }
        toggleSection(R.id.mobileConnectionSettingsButton, R.id.mobileConnectionSettings)
        toggleSection(R.id.mobileAdvancedToggle, R.id.mobileAdvancedSection)
        toggleSection(R.id.mobileAccountToggle, R.id.mobileAccountSection)
        findViewById<Button>(R.id.mobilePanelButton).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Eventos no site")
                .setMessage("O vídeo ao vivo e a configuração dos produtos ficam neste aplicativo. O site mostra os eventos enviados pela sua conta; ele ainda não transmite o vídeo desta câmera. Ao sair, a análise pausa.")
                .setNegativeButton("Continuar na câmera", null)
                .setPositiveButton("Abrir eventos") { _, _ -> openEventSite() }.show()
        }
        cameraControls(false)
        status.text = "SMART24 3.3.1 • câmera Sala da oficina cadastrada: $OFFICE_HOST:$OFFICE_RTSP_PORT • ID $OFFICE_DEVICE_ID • MAC $OFFICE_MAC. Digite somente a senha NVR/RTSP; o usuário RTSP é tentado automaticamente. No 4G, o IP privado exige rota remota/P2P."
        showPreviousFailure()
        updateSyncText()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        rtspPlayer.resume()
        reloadZones()
        handler.removeCallbacks(syncLoop)
        handler.post(syncLoop)
    }
    override fun onPause() {
        resumed = false
        val hadConfirmedVideo = rtspPlayer.isConnected
        if (connectionJob?.isActive == true) {
            cancelConnectionCheck()
            status.text = "Verificação da câmera interrompida ao sair. Conecte novamente ao voltar."
        }
        stopAi()
        handler.removeCallbacks(syncLoop)
        rtspPlayer.pause()
        cameraControls(false)
        if (hadConfirmedVideo) {
            status.text = "Vídeo pausado ao sair. Reconectando à câmera ao voltar…"
        }
        super.onPause()
    }

    private fun connectCamera() {
        if (!validKey(storeId()) || !validKey(cameraId())) { status.text = "Informe identificações válidas de loja e câmera."; return }
        hideKeyboard()
        saveIdentity()
        reloadZones()
        stopAi()
        val host = input(R.id.mobileHostInput).text.toString().trim()
        val user = input(R.id.mobileUserInput).text.toString().trim()
        val password = input(R.id.mobileCameraPasswordInput).text.toString()
        val port = input(R.id.mobilePortInput).text.toString().toIntOrNull()
        val explicit = input(R.id.mobileRtspUrlInput).text.toString().trim()
        if (port == null || port !in 1..65535) { status.text = "Informe uma porta válida, entre 1 e 65535."; return }
        val urls = runCatching { MobileRtspCandidates.build(host, user, password, port, explicit) }
            .getOrElse { status.text = it.message; return }
        val connectHost = Uri.parse(urls.first()).host.orEmpty()
        cancelConnectionCheck()
        rtspPlayer.disconnect()
        getSharedPreferences("smart24_mobile", MODE_PRIVATE).edit()
            .putString("host", connectHost).putString("user", user).putString("port", port.toString()).apply()
        cameraControls(false)
        input(R.id.mobileStoreInput).isEnabled = false
        input(R.id.mobileCameraInput).isEnabled = false
        connectionReport = ""
        status.text = "Verificando a conexão da câmera…"
        val token = connectionGeneration
        val progressOpen = AtomicBoolean(true)
        connectionJob = lifecycleScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    val cancel = { context.ensureActive() }
                    val progress: (String) -> Unit = { message ->
                        handler.post {
                            if (progressOpen.get() && token == connectionGeneration && resumed && !destroyed) {
                                status.text = message
                            }
                        }
                    }
                    val connectivity = getSystemService(ConnectivityManager::class.java)
                    val activeNetwork = connectivity.activeNetwork
                    val capabilities = connectivity.getNetworkCapabilities(activeNetwork)
                    val vpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    val networkReport = when {
                        vpn -> "Rede: VPN ativa; diagnóstico e player usam a rota padrão do Android."
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Rede: Wi-Fi é a rede padrão do celular."
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Rede: dados móveis; a câmera precisa de endereço acessível por conexão remota."
                        else -> "Rede: conexão padrão do Android."
                    }
                    // Do not bind the probe to a physical Wi-Fi network: that bypasses
                    // VPN routes and disagrees with the network used by native LibVLC.
                    val socketFactory: (String, Int, Int) -> Socket = { address, tcpPort, timeout ->
                        Socket().also { socket ->
                            try { socket.connect(InetSocketAddress(address, tcpPort), timeout) }
                            catch (error: Exception) { socket.close(); throw error }
                        }
                    }
                    val httpFactory: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
                    val probe = CameraRtspProbe(socketFactory)
                    val services = if (explicit.isBlank()) {
                        progress("Localizando o serviço ONVIF da câmera…")
                        knownOnvifServices.filter { OnvifStreamResolver.sameCameraUrl(it, connectHost, setOf("http", "https")) != null }.ifEmpty {
                            (if (vpn) emptyList() else OnvifDiscovery(this@MobileVigilanteActivity).discover(2200L))
                                .firstOrNull { it.host.equals(connectHost, true) }?.serviceUrls.orEmpty()
                        }
                    } else emptyList()
                    val onvif = OnvifStreamResolver(httpFactory, { address, managementPort -> probe.portOpen(address, managementPort) }, cancel, progress)
                    val planner = CameraConnectionPlanner(probe, onvif, cancel, progress)
                    val userCandidates = if (user.isBlank() && password.isNotBlank() && explicit.isBlank()) {
                        listOf("administrator", "admin", "")
                    } else {
                        listOf(user)
                    }
                    var selectedPlan: CameraConnectionPlanner.Plan? = null
                    var selectedUser = user
                    for ((attemptIndex, candidateUser) in userCandidates.withIndex()) {
                        cancel()
                        if (userCandidates.size > 1) {
                            progress("Autenticação RTSP automática ${attemptIndex + 1}/${userCandidates.size}…")
                        }
                        val attempt = planner.resolve(host, candidateUser, password, port, explicit, services)
                        selectedPlan = attempt
                        if (attempt.urls.isNotEmpty()) {
                            selectedUser = candidateUser
                            break
                        }
                        if (!attempt.needsCredentials) break
                    }
                    val result = checkNotNull(selectedPlan)
                    if (result.urls.isNotEmpty() && selectedUser.isNotBlank()) {
                        getSharedPreferences("smart24_mobile", MODE_PRIVATE).edit().putString("user", selectedUser).apply()
                        handler.post {
                            if (token == connectionGeneration && resumed && !destroyed) {
                                input(R.id.mobileUserInput).setText(selectedUser)
                            }
                        }
                    }
                    result.copy(report = "$networkReport\n${result.report}")
                }
                progressOpen.set(false)
                if (token != connectionGeneration || !resumed || destroyed) return@launch
                connectionReport = plan.report
                if (plan.urls.isEmpty()) onFailed(plan.message)
                else rtspPlayer.connectResolved(plan.urls, plan.message)
            } catch (error: CancellationException) {
                progressOpen.set(false)
                throw error
            } catch (_: Exception) {
                progressOpen.set(false)
                if (token == connectionGeneration && resumed && !destroyed) onFailed("Não foi possível verificar a câmera. Confira o IP, a conexão local/VPN e a configuração NVR/RTSP.")
            } finally {
                progressOpen.set(false)
                if (token == connectionGeneration) connectionJob = null
            }
        }
        input(R.id.mobileCameraPasswordInput).text.clear()
        input(R.id.mobileRtspUrlInput).text.clear()
    }

    private fun loginFirebase() {
        stopAi()
        val email = input(R.id.mobileFirebaseEmailInput).text.toString().trim()
        val password = input(R.id.mobileFirebasePasswordInput).text.toString()
        if (email.isBlank() || password.isBlank()) { status.text = "Teste local disponível sem login. Para sincronizar, informe o usuário Firebase existente."; return }
        if (!validKey(storeId()) || !validKey(cameraId())) { status.text = "Loja ou câmera contém caracteres inválidos."; return }
        status.text = "Entrando no Firebase…"
        lifecycleScope.launch {
            try {
                PilotSession.clearAuthentication()
                val result = firebase.login(email, password)
                PilotSession.idToken = result.idToken
                PilotSession.refreshToken = result.refreshToken
                PilotSession.tokenExpiresAt = System.currentTimeMillis() + result.expiresIn * 1000
                PilotSession.uid = result.localId
                PilotSession.email = result.email
                val role = firebase.getRole(result.localId)
                require(role in setOf("admin", "operator")) { "Usuário precisa ser admin ou operator." }
                PilotSession.storeId = storeId(); PilotSession.cameraId = cameraId()
                val device = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                PilotSession.bridgeId = "mobile-$device"
                PilotSession.pilotId = "mobile-$device"
                PilotSession.sessionId = sessionId
                input(R.id.mobileFirebasePasswordInput).text.clear()
                saveIdentity(email)
                status.text = "Firebase conectado como $role. Eventos autenticados serão sincronizados."
                synchronizeEvents()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                PilotSession.clearAuthentication()
                status.text = "Falha no Firebase: ${error.message}. O teste local continua disponível."
            }
        }
    }

    private fun saveIdentity(email: String = input(R.id.mobileFirebaseEmailInput).text.toString().trim()) {
        getSharedPreferences("smart24_mobile", MODE_PRIVATE).edit()
            .putString("email", email).putString("store", storeId()).putString("camera", cameraId()).apply()
    }

    private fun captureAndCalibrate() {
        stopAi()
        hideKeyboard()
        val bitmap = rtspPlayer.captureFrame()
        if (bitmap == null || BitmapUtils.isMostlyBlack(bitmap)) {
            bitmap?.recycle(); status.text = "Aguarde uma imagem visível da câmera antes de configurar produtos."; return
        }
        if (!validKey(storeId()) || !validKey(cameraId())) { bitmap.recycle(); status.text = "Informe identificações válidas para loja e câmera."; return }
        val store = storeId()
        val camera = cameraId()
        findViewById<Button>(R.id.mobileCalibrateButton).isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(File(filesDir, "latest_mobile_frame.jpg")).use {
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
                    }
                }
                if (!resumed || destroyed) return@launch
                saveIdentity()
                startActivity(Intent(this@MobileVigilanteActivity, MobileCalibrationActivity::class.java)
                    .putExtra("storeId", store).putExtra("cameraId", camera))
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                status.text = "Não foi possível abrir a configuração. Confira o espaço livre do celular e tente novamente."
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                if (!destroyed) cameraControls(rtspPlayer.isConnected)
            }
        }
    }

    private fun startAi() {
        if (!rtspPlayer.isConnected) { status.text = "Conecte a câmera e aguarde a imagem primeiro."; return }
        reloadZones()
        if (zones.isEmpty()) { status.text = "Cadastre ao menos uma zona com produto e SKU."; return }
        if (analysisBusy) { status.text = "Aguarde a análise anterior terminar."; return }
        saveIdentity()
        sessionId = "MOBILE-${UUID.randomUUID()}"
        stopAi()
        analyzing = true
        findViewById<Button>(R.id.mobileStopAiButton).isEnabled = true
        handler.post(analysisLoop)
        updateGuide()
        status.text = "Vigilante iniciado. Mantenha a prateleira livre para registrar o estado inicial."
    }
    private fun stopAi() {
        generation++; analyzing = false; handler.removeCallbacks(analysisLoop); itemEngine.reset()
        findViewById<Button>(R.id.mobileStopAiButton)?.isEnabled = false
    }
    private fun reloadZones() {
        zones = MobileZoneStore.load(this, storeId(), cameraId()); overlay.zones = zones
        updateGuide()
    }
    private fun updateGuide() {
        findViewById<TextView>(R.id.mobileGuide).text = when {
            analyzing -> "Vigilante ativo • ${zones.size} áreas cadastradas. Mantenha este aplicativo aberto."
            !rtspPlayer.isConnected -> "1. Conecte a câmera para ver a imagem."
            zones.isEmpty() -> "2. Toque em Configurar produtos e marque a posição de cada produto na imagem."
            else -> "${zones.size} áreas cadastradas • revise os produtos ou toque em Iniciar vigilante."
        }
    }
    private fun toggleSection(button: Int, section: Int) {
        findViewById<Button>(button).setOnClickListener {
            val view = findViewById<View>(section)
            view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(textureView.windowToken, 0)
        currentFocus?.clearFocus()
    }
    internal fun openEventSite() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tsvalencio-ia.github.io/SMART24/"))) }
        catch (_: ActivityNotFoundException) { status.text = "Não há navegador disponível. O vídeo continua neste aplicativo." }
        catch (_: SecurityException) { status.text = "O celular bloqueou a abertura do navegador. O vídeo continua neste aplicativo." }
    }
    private fun showPreviousFailure() {
        val report = MobileCrashReport.previous(this) ?: return
        AlertDialog.Builder(this).setTitle("Fechamento anterior detectado")
            .setMessage("O celular registrou um fechamento do SMART24. Você pode copiar o relatório técnico, sem senhas ou imagens, para investigar a causa.")
            .setPositiveButton("Copiar relatório") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SMART24 erro", report))
            }.setNegativeButton("Continuar", null).show()
    }

    private suspend fun publishEvent(event: MobileItemEvent) {
        val labels = mapOf("ITEM_PICKED_PROBABLE" to "RETIRADA PROVÁVEL", "ITEM_RETURNED_PROBABLE" to "DEVOLUÇÃO PROVÁVEL", "SHELF_INTERACTION" to "INTERAÇÃO NA PRATELEIRA")
        val payload = mapOf<String, Any?>(
            "type" to event.type, "storeId" to event.zone.storeId, "cameraId" to event.zone.cameraId,
            "bridgeId" to PilotSession.bridgeId, "sessionId" to sessionId, "personId" to event.personId,
            "hand" to event.hand, "zoneId" to event.zone.zoneId, "sku" to event.zone.sku,
            "productId" to event.zone.productId.ifBlank { event.zone.sku }, "productName" to event.zone.productName,
            "quantity" to 1, "confidence" to event.confidence, "confidenceKind" to "HEURISTIC_SCORE",
            "evidence" to event.evidence, "source" to "SMART24_MOBILE_RTSP", "requiresReview" to true,
            "createdAt" to event.createdAt, "createdBy" to PilotSession.uid
        )
        withContext(Dispatchers.IO) { outbox.add(payload, if (PilotSession.authenticated) PilotSession.uid else "") }
        eventText.text = "${labels[event.type]} • ${event.zone.productName} • ${event.personId}\n${event.evidence}"
        updateSyncText()
        synchronizeEvents()
    }

    private fun synchronizeEvents() {
        if (!PilotSession.authenticated || !resumed || syncMutex.isLocked) { updateSyncText(); return }
        lifecycleScope.launch {
            syncMutex.withLock {
                val uid = PilotSession.uid
                try {
                    val rows = withContext(Dispatchers.IO) { outbox.pending(uid) }
                    for (row in rows) {
                        if (PilotSession.uid != uid || !resumed) break
                        val payload = row.getJSONObject("payload")
                        val values = payload.keys().asSequence().associateWith { payload.get(it) }
                        firebase.put("events/${row.getString("id")}", values)
                        withContext(Dispatchers.IO) { outbox.uploaded(row.getString("id")) }
                    }
                    if (PilotSession.uid == uid && rtspPlayer.isConnected) {
                        val now = System.currentTimeMillis()
                        firebase.put("visionPilots/${PilotSession.pilotId}", mapOf("pilotId" to PilotSession.pilotId, "storeId" to storeId(), "cameraId" to cameraId(), "status" to if (analyzing) "VIGILANTE_ACTIVE" else "VIDEO_DIRECT_VISIBLE", "lastSeenAt" to now, "source" to "SMART24_MOBILE_RTSP", "sessionId" to sessionId))
                    }
                    updateSyncText()
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    syncText.text = "Eventos preservados no celular • pendentes ${outbox.count()} • sincronização: ${error.message}"
                }
            }
        }
    }
    private fun updateSyncText() {
        if (!::outbox.isInitialized) return
        syncText.text = "Fila local: ${outbox.count()} • teste sem login: ${outbox.localOnlyCount()}\n${if (PilotSession.authenticated) "Firebase autenticado; tentativas automáticas a cada 15 s." else "Sem login: eventos ficam neste celular."}"
    }

    private fun discoverCameras() {
        status.text = "Procurando câmeras ONVIF na rede Wi-Fi…"
        lifecycleScope.launch {
            try {
                val cameras = OnvifDiscovery(this@MobileVigilanteActivity).discover()
                if (cameras.isEmpty()) { status.text = "Nenhuma resposta ONVIF. Você ainda pode informar o IP/URL RTSP manualmente."; return@launch }
                AlertDialog.Builder(this@MobileVigilanteActivity).setTitle("Câmeras ONVIF encontradas")
                    .setItems(cameras.map { it.host }.toTypedArray()) { _, selected ->
                        input(R.id.mobileHostInput).setText(cameras[selected].host)
                        knownOnvifServices = cameras[selected].serviceUrls
                        status.text = "IP preenchido. Informe as credenciais NVR/RTSP e conecte."
                    }.setNegativeButton("Fechar", null).show()
            } catch (error: Exception) { status.text = "Descoberta indisponível. Confira o Wi-Fi ou informe o IP manualmente." }
        }
    }
    private fun cameraControls(enabled: Boolean) {
        findViewById<Button>(R.id.mobileCalibrateButton).isEnabled = enabled
        findViewById<Button>(R.id.mobileStartAiButton).isEnabled = enabled
        if (!analyzing) findViewById<Button>(R.id.mobileStopAiButton).isEnabled = false
        updateGuide()
    }
    private fun cancelConnectionCheck() {
        connectionGeneration++
        connectionJob?.cancel()
        connectionJob = null
    }
    override fun onConnecting(candidateNumber: Int, total: Int) { status.text = "Testando RTSP $candidateNumber/$total • aguardando imagem real…" }
    override fun onConnected(maskedUrl: String) {
        cameraControls(true)
        findViewById<View>(R.id.mobileConnectionSettings).visibility = View.GONE
        input(R.id.mobileStoreInput).isEnabled = false
        input(R.id.mobileCameraInput).isEnabled = false
        status.text = "VÍDEO CONFIRMADO • ${storeId()} / ${cameraId()}\n$maskedUrl"
    }
    override fun onFailed(message: String) {
        stopAi(); cameraControls(false); status.text = message
        input(R.id.mobileStoreInput).isEnabled = true
        input(R.id.mobileCameraInput).isEnabled = true
        findViewById<View>(R.id.mobileConnectionSettings).visibility = View.VISIBLE
        if (connectionReport.isNotBlank() && resumed && !destroyed) {
            AlertDialog.Builder(this).setTitle("Diagnóstico da câmera")
                .setMessage("$message\n\n${connectionReport.lineSequence().take(7).joinToString("\n")}")
                .setPositiveButton("Copiar diagnóstico") { _, _ ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SMART24 câmera", "$message\n\n$connectionReport"))
                }.setNegativeButton("Fechar", null).show()
        }
    }
    override fun onInterrupted(message: String) { stopAi(); cameraControls(false); status.text = message }
    override fun onDestroy() {
        destroyed = true
        cancelConnectionCheck()
        stopAi(); handler.removeCallbacksAndMessages(null)
        if (!analysisBusy && visionDelegate.isInitialized()) vision.close()
        rtspPlayer.release()
        super.onDestroy()
    }
}
