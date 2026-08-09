package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory

/** Reprodução e análise do stream entregue diretamente pela câmera. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class DirectCameraActivity : AppCompatActivity() {
    private data class Candidate(val uri: Uri, val label: String)

    private lateinit var playerView: PlayerView
    private lateinit var overlayView: VisionOverlayView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var calibrateButton: Button
    private lateinit var processor: DirectVisionProcessor
    private val manualButtons = mutableListOf<Button>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisBusy = AtomicBoolean(false)
    private var player: ExoPlayer? = null
    private var compatibilityProxy: RtspCompatibilityProxy? = null
    private var candidates: List<Candidate> = emptyList()
    private var candidateIndex = 0
    private var streamReady = false
    private var firstFrameAnalyzed = false
    private var firmwareCompatibilityActive = false
    private var stopping = false
    private var lastAnalysisErrorAt = 0L
    private var connectionGeneration = 0

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && streamReady && !stopping) captureDecodedFrame()
            mainHandler.postDelayed(this, ANALYSIS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_camera)
        playerView = findViewById(R.id.directPlayerView)
        overlayView = findViewById(R.id.visionOverlayView)
        statusText = findViewById(R.id.directStatusText)
        progress = findViewById(R.id.directProgress)
        calibrateButton = findViewById(R.id.directCalibrateButton)
        processor = DirectVisionProcessor(this)

        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, 554)
        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val streamPath = intent.getStringExtra(EXTRA_STREAM_PATH).orEmpty().trim('/')
        intent.removeExtra(EXTRA_PASSWORD)

        if (!PilotSession.authenticated || host.isBlank() || username.isBlank() || password.isBlank() || streamPath.isBlank()) {
            setStatus("A configuração da conexão direta está incompleta. Volte e confira Firebase, IP, usuário e senha NVR.")
            progress.visibility = View.GONE
            return
        }

        candidates = buildCandidates(host, port, username, password, streamPath)
        bindActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish()
        })

        lifecycleScope.launch {
            runCatching { processor.start() }
        }
        startCandidate(0)
    }

    override fun onStart() {
        super.onStart()
        if (streamReady) {
            player?.play()
            scheduleFrameLoop()
        }
    }

    override fun onStop() {
        mainHandler.removeCallbacks(frameLoop)
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        stopping = true
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        overlayView.clear()
        processor.close()
        candidates = emptyList()
        super.onDestroy()
    }

    private fun bindActions() {
        val pickup = findViewById<Button>(R.id.directPickupButton)
        val returned = findViewById<Button>(R.id.directReturnButton)
        val conceal = findViewById<Button>(R.id.directConcealButton)
        val alert = findViewById<Button>(R.id.directAlertButton)
        val finishTrack = findViewById<Button>(R.id.directFinishButton)
        manualButtons += listOf(pickup, returned, conceal, alert, finishTrack)

        pickup.setOnClickListener { runManualAction { processor.markPickup() } }
        returned.setOnClickListener { runManualAction { processor.markReturn() } }
        conceal.setOnClickListener { runManualAction { processor.markConcealment() } }
        alert.setOnClickListener { runManualAction { processor.sendManualAlert() } }
        finishTrack.setOnClickListener { runManualAction { processor.finish() } }

        calibrateButton.setOnClickListener {
            if (!firstFrameAnalyzed) {
                setStatus("Aguarde o primeiro quadro real da câmera antes de calibrar a zona.")
                return@setOnClickListener
            }
            startActivity(
                android.content.Intent(this, CalibrationActivity::class.java)
                    .putExtra(CalibrationActivity.EXTRA_MODE, CalibrationActivity.MODE_ZONE)
                    .putExtra(
                        CalibrationActivity.EXTRA_COORDINATE_SPACE,
                        CoordinateSpaces.DIRECT_CAMERA_FRAME_V1
                    )
            )
        }
        findViewById<Button>(R.id.directRetryButton).setOnClickListener {
            firstFrameAnalyzed = false
            setManualControlsEnabled(false)
            calibrateButton.isEnabled = false
            overlayView.clear()
            startCandidate(0)
        }
        findViewById<Button>(R.id.directStopButton).setOnClickListener { stopAndFinish() }
    }

    private fun startCandidate(index: Int) {
        if (stopping || candidates.isEmpty()) return
        if (index !in candidates.indices) {
            streamReady = false
            progress.visibility = View.GONE
            val message =
                "A câmera não aceitou as combinações RTSP compatíveis. Confirme o mesmo Wi‑Fi, o IP local e a senha criada em Conexão NVR."
            setStatus(message)
            lifecycleScope.launch { runCatching { processor.publishFailure(message) } }
            return
        }

        candidateIndex = index
        val generation = ++connectionGeneration
        streamReady = false
        firmwareCompatibilityActive = false
        mainHandler.removeCallbacks(frameLoop)
        releasePlayer()
        progress.visibility = View.VISIBLE
        setStatus("Conectando diretamente à câmera • ${candidates[index].label}…")

        val upstreamSocketFactory = wifiSocketFactory()
        if (upstreamSocketFactory == null) {
            progress.visibility = View.GONE
            setStatus("O celular não está conectado ao Wi-Fi. Conecte-o à mesma rede local da câmera e tente novamente.")
            return
        }
        val candidate = candidates[index]
        val cameraHost = candidate.uri.host
        val cameraPort = candidate.uri.port.takeIf { it > 0 } ?: 554
        if (cameraHost.isNullOrBlank()) {
            progress.visibility = View.GONE
            setStatus("O endereço local da câmera é inválido. Volte e confira o IP informado.")
            return
        }
        val proxy = runCatching {
            RtspCompatibilityProxy(
                upstreamHost = cameraHost,
                upstreamPort = cameraPort,
                upstreamSocketFactory = upstreamSocketFactory,
                onCSeqRepair = {
                    mainHandler.post {
                        if (!stopping && generation == connectionGeneration) {
                            firmwareCompatibilityActive = true
                            setStatus("Câmera respondeu. Compatibilidade com o firmware RTSP antigo ativada…")
                        }
                    }
                }
            )
        }.getOrElse {
            progress.visibility = View.GONE
            setStatus("Não foi possível preparar a conexão RTSP protegida. Tente novamente.")
            return
        }
        compatibilityProxy = proxy

        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            .setSocketFactory(proxy.media3SocketFactory)
            .setDebugLoggingEnabled(false)
            .createMediaSource(MediaItem.fromUri(candidate.uri))

        val newPlayer = ExoPlayer.Builder(this).build()
        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== newPlayer || stopping) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        progress.visibility = View.VISIBLE
                        setStatus("Câmera localizada; recebendo o fluxo RTSP direto…")
                    }
                    Player.STATE_READY -> {
                        streamReady = true
                        progress.visibility = View.GONE
                        val compatibility = if (firmwareCompatibilityActive) {
                            " • firmware antigo normalizado"
                        } else {
                            ""
                        }
                        setStatus("Câmera conectada diretamente$compatibility. Aguardando o primeiro quadro para iniciar a visão…")
                        scheduleFrameLoop()
                    }
                    Player.STATE_ENDED -> reconnectCurrentCandidate()
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player !== newPlayer || stopping) return
                streamReady = false
                mainHandler.removeCallbacks(frameLoop)
                if (firstFrameAnalyzed) {
                    setStatus("A conexão direta foi interrompida; tentando reconectar à mesma câmera…")
                    mainHandler.postDelayed({ startCandidate(candidateIndex) }, RECONNECT_DELAY_MS)
                } else {
                    val next = candidateIndex + 1
                    setStatus("Esta combinação não abriu o vídeo (${error.errorCodeName}). Tentando a próxima configuração compatível…")
                    mainHandler.postDelayed({ startCandidate(next) }, NEXT_CANDIDATE_DELAY_MS)
                }
            }
        })
        player = newPlayer
        playerView.player = newPlayer
        newPlayer.setMediaSource(mediaSource)
        newPlayer.playWhenReady = true
        newPlayer.prepare()
    }

    private fun scheduleFrameLoop() {
        mainHandler.removeCallbacks(frameLoop)
        mainHandler.post(frameLoop)
    }

    private fun captureDecodedFrame() {
        if (!analysisBusy.compareAndSet(false, true)) return
        val texture = playerView.videoSurfaceView as? TextureView
        if (texture == null || !texture.isAvailable) {
            analysisBusy.set(false)
            return
        }

        val videoSize = player?.videoSize
        val sourceWidth = videoSize?.width?.takeIf { it > 0 } ?: 1280
        val sourceHeight = videoSize?.height?.takeIf { it > 0 } ?: 720
        val targetWidth = minOf(ANALYSIS_MAX_WIDTH, sourceWidth).coerceAtLeast(320)
        val targetHeight = (sourceHeight * (targetWidth.toFloat() / sourceWidth.toFloat()))
            .toInt()
            .coerceAtLeast(180)
        val bitmap = runCatching { texture.getBitmap(targetWidth, targetHeight) }.getOrNull()
        if (bitmap == null) {
            analysisBusy.set(false)
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val outcome = runCatching { processor.process(bitmap) }
            withContext(Dispatchers.Main) {
                outcome.onSuccess { processed ->
                    firstFrameAnalyzed = true
                    setManualControlsEnabled(true)
                    calibrateButton.isEnabled = true
                    overlayView.update(processed.result, processed.zones, processed.assisted)
                    setStatus(
                        "Câmera direta ativa • ${processed.statusLine} • eventos sincronizados com o Firebase."
                    )
                }.onFailure { error ->
                    val now = System.currentTimeMillis()
                    if (now - lastAnalysisErrorAt > 3000L) {
                        setStatus("Vídeo direto conectado, mas a análise deste quadro falhou: ${safeAnalysisError(error)}")
                        lastAnalysisErrorAt = now
                    }
                }
                if (!bitmap.isRecycled) bitmap.recycle()
                analysisBusy.set(false)
            }
        }
    }

    private fun runManualAction(action: suspend () -> AssistedDemoEngine.ActionResult) {
        if (!firstFrameAnalyzed) {
            setStatus("Aguarde a visão analisar o primeiro quadro da câmera.")
            return
        }
        setManualControlsEnabled(false)
        setStatus("Registrando ação confirmada pelo operador…")
        lifecycleScope.launch {
            val result = runCatching { action() }
                .getOrElse { AssistedDemoEngine.ActionResult(false, it.message ?: "Falha ao registrar ação.") }
            setStatus(result.message)
            setManualControlsEnabled(firstFrameAnalyzed && streamReady)
        }
    }

    private fun reconnectCurrentCandidate() {
        if (stopping) return
        streamReady = false
        mainHandler.removeCallbacks(frameLoop)
        setStatus("O fluxo RTSP terminou; reconectando automaticamente…")
        mainHandler.postDelayed({ startCandidate(candidateIndex) }, RECONNECT_DELAY_MS)
    }

    private fun stopAndFinish() {
        if (stopping) return
        stopping = true
        setManualControlsEnabled(false)
        mainHandler.removeCallbacksAndMessages(null)
        lifecycleScope.launch {
            runCatching { processor.stop() }
            finish()
        }
    }

    private fun releasePlayer() {
        val active = player
        val activeProxy = compatibilityProxy
        player = null
        compatibilityProxy = null
        playerView.player = null
        runCatching { active?.release() }
        runCatching { activeProxy?.close() }
    }

    /** Garante que o tráfego local continue no Wi-Fi mesmo quando o 4G estiver ligado. */
    private fun wifiSocketFactory(): SocketFactory? {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val wifiNetwork = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return wifiNetwork?.socketFactory
    }

    private fun setManualControlsEnabled(enabled: Boolean) {
        manualButtons.forEach { it.isEnabled = enabled }
    }

    private fun setStatus(value: String) {
        statusText.text = value
    }

    private fun safeAnalysisError(error: Throwable): String = when {
        error.message.isNullOrBlank() -> "erro de processamento"
        error.message.orEmpty().contains("quadro preto", ignoreCase = true) ->
            "o quadro ainda está preto; aguarde a câmera estabilizar"
        error.message.orEmpty().contains("Firebase", ignoreCase = true) ->
            "falha temporária de sincronização com o Firebase"
        else -> "${error::class.java.simpleName}"
    }

    private fun buildCandidates(
        host: String,
        port: Int,
        requestedUsername: String,
        password: String,
        requestedPath: String
    ): List<Candidate> {
        val usernames = linkedSetOf(requestedUsername)
        when (requestedUsername.lowercase()) {
            "admin" -> usernames += "administrator"
            "administrator" -> usernames += "admin"
        }
        val paths = linkedSetOf(requestedPath.trim('/'))
        when (requestedPath.trim('/').lowercase()) {
            "onvif1" -> paths += "onvif2"
            "onvif2" -> paths += "onvif1"
        }

        return buildList {
            usernames.forEachIndexed { userIndex, username ->
                paths.forEach { path ->
                    val profile = if (path.lowercase() == "onvif2") "fluxo secundário" else "fluxo principal"
                    val account = if (userIndex == 0) "usuário informado" else "usuário NVR alternativo"
                    add(Candidate(buildRtspUri(host, port, username, password, path), "$profile • $account"))
                }
            }
        }.distinctBy { it.uri.toString() }
    }

    private fun buildRtspUri(host: String, port: Int, username: String, password: String, path: String): Uri {
        val authority = "${Uri.encode(username)}:${Uri.encode(password)}@$host:$port"
        val builder = Uri.Builder().scheme("rtsp").encodedAuthority(authority)
        path.split('/').filter(String::isNotBlank).forEach(builder::appendPath)
        return builder.build()
    }

    companion object {
        const val EXTRA_HOST = "direct_camera_host"
        const val EXTRA_PORT = "direct_camera_port"
        const val EXTRA_USERNAME = "direct_camera_username"
        const val EXTRA_PASSWORD = "direct_camera_password"
        const val EXTRA_STREAM_PATH = "direct_camera_stream_path"

        private const val ANALYSIS_INTERVAL_MS = 900L
        private const val ANALYSIS_MAX_WIDTH = 960
        private const val RTSP_TIMEOUT_MS = 10000L
        private const val NEXT_CANDIDATE_DELAY_MS = 900L
        private const val RECONNECT_DELAY_MS = 2500L
    }
}
