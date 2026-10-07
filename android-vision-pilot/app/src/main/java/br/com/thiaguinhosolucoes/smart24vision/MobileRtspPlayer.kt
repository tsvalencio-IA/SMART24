package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.Executors

/** Every attempt owns its player, callbacks and deadline. Credentials stay in memory. */
class MobileRtspPlayer(context: Context, private val textureView: TextureView, private val listener: Listener) {
    interface Listener {
        fun onConnecting(candidateNumber: Int, total: Int)
        fun onConnected(maskedUrl: String)
        fun onFailed(message: String)
        fun onInterrupted(message: String)
    }
    private val handler = Handler(Looper.getMainLooper())
    // Native stop() can wait for network/decoder threads. Never make the UI wait for it.
    private val nativeWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SMART24-VLC").apply { isDaemon = true }
    }
    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--network-caching=450", "--verbose=0"))
    @Volatile private var player: MediaPlayer? = null
    private data class Playback(val url: String, val tcp: Boolean, val hardware: Boolean)
    private var candidates: List<Playback> = emptyList()
    private var index = -1
    @Volatile private var generation = 0
    private var connected = false
    @Volatile private var released = false
    @Volatile private var paused = false
    private var playing = false
    private var videoOutput = false
    private var frameTimestampAtStart = 0L
    private var failureMessage = "O fluxo respondeu, mas não entregou imagem. Confira o NVR/RTSP e a transmissão de vídeo da câmera."
    val isConnected: Boolean get() = connected && !paused

    fun connect(host: String, username: String, password: String, preferredPort: Int, explicitUrl: String) {
        val urls = runCatching { MobileRtspCandidates.build(host, username, password, preferredPort, explicitUrl) }
            .getOrElse { listener.onFailed(it.message ?: "Configuração RTSP inválida."); return }
        connectResolved(urls)
    }

    fun connectResolved(urls: List<String>, explanation: String = failureMessage) {
        disconnect()
        if (released) return
        failureMessage = explanation
        candidates = urls.distinct().take(3).flatMap { url ->
            listOf(Playback(url, tcp = true, hardware = true),
                Playback(url, tcp = true, hardware = false),
                Playback(url, tcp = false, hardware = false))
        }
        paused = false
        index = -1
        nextCandidate()
    }

    private fun nextCandidate() {
        if (released || paused) return
        handler.removeCallbacksAndMessages(null)
        disposePlayer()
        connected = false
        playing = false
        videoOutput = false
        val token = ++generation
        index++
        if (index !in candidates.indices) {
            listener.onFailed(failureMessage)
            return
        }
        listener.onConnecting(index + 1, candidates.size)
        val candidate = candidates[index]
        textureView.post {
            if (released || paused || token != generation) return@post
            val active = MediaPlayer(libVlc)
            player = active
            frameTimestampAtStart = textureView.surfaceTexture?.timestamp ?: 0L
            active.vlcVout.setVideoView(textureView)
            active.vlcVout.setWindowSize(textureView.width.coerceAtLeast(1), textureView.height.coerceAtLeast(1))
            active.vlcVout.attachViews()
            active.setEventListener { event ->
                handler.post {
                    if (released || token != generation || player !== active) return@post
                    when (event.type) {
                        MediaPlayer.Event.Playing -> playing = true
                        MediaPlayer.Event.Vout -> videoOutput = event.voutCount > 0
                        MediaPlayer.Event.EncounteredError, MediaPlayer.Event.EndReached -> {
                            if (connected) {
                                connected = false
                                listener.onInterrupted("Vídeo interrompido. Reconectando à mesma câmera…")
                                index--
                                handler.removeCallbacksAndMessages(null)
                                handler.postDelayed({ if (token == generation) nextCandidate() }, 2000L)
                            } else {
                                handler.removeCallbacksAndMessages(null)
                                handler.postDelayed({ if (token == generation) nextCandidate() }, 350L)
                            }
                        }
                        else -> Unit
                    }
                }
            }
            nativeWorker.execute {
                if (released || paused || token != generation || player !== active) return@execute
                try {
                    val media = Media(libVlc, Uri.parse(candidate.url)).apply {
                        setHWDecoderEnabled(candidate.hardware, false)
                        addOption(if (candidate.tcp) ":rtsp-tcp" else ":no-rtsp-tcp")
                        addOption(":network-caching=450")
                        addOption(":tcp-timeout=2500")
                        addOption(":no-audio")
                    }
                    try { active.media = media } finally { media.release() }
                    handler.post {
                        if (released || paused || token != generation) return@post
                        pollFirstFrame(token)
                        handler.postDelayed({ if (!connected && token == generation) nextCandidate() }, 8500L)
                    }
                    active.play()
                } catch (_: Exception) {
                    handler.post { if (!released && !paused && token == generation) nextCandidate() }
                }
            }
        }
    }

    private fun pollFirstFrame(token: Int) {
        if (released || paused || token != generation || connected) return
        val timestamp = textureView.surfaceTexture?.timestamp ?: 0L
        if (playing && videoOutput && textureView.isAvailable && timestamp > 0 && timestamp != frameTimestampAtStart) {
            val frame = textureView.getBitmap(160, 90)
            val useful = frame != null && !BitmapUtils.isMostlyBlack(frame)
            frame?.recycle()
            if (useful) {
                connected = true
                handler.removeCallbacksAndMessages(null)
                listener.onConnected(MobileRtspCandidates.mask(candidates[index].url))
                return
            }
        }
        handler.postDelayed({ pollFirstFrame(token) }, 250L)
    }

    fun captureFrame(maxWidth: Int = 960): Bitmap? {
        if (!isConnected || !textureView.isAvailable || textureView.width < 2 || textureView.height < 2) return null
        val width = minOf(maxWidth, textureView.width)
        val height = (textureView.height.toFloat() * width / textureView.width).toInt().coerceAtLeast(2)
        return runCatching { textureView.getBitmap(width, height) }.getOrNull()
    }

    fun pause() {
        if (released || paused) return
        paused = true
        generation++
        handler.removeCallbacksAndMessages(null)
        connected = false
        playing = false
        videoOutput = false
        // An RTSP camera is a live source, not a seekable movie. Release the old
        // surface/decoder before another Activity takes over and reopen on return.
        // Keep the selected candidate in memory, including its credentials.
        disposePlayer()
    }

    fun resume() {
        if (!paused || released) return
        paused = false
        if (index in candidates.indices) { index--; nextCandidate() }
    }

    /**
     * Reinicia a mesma câmera usando os candidatos RTSP que já estão somente
     * na memória do celular. Não persiste usuário, senha ou URL no Firebase.
     */
    fun reconnect(): Boolean {
        if (released || candidates.isEmpty()) return false
        paused = false
        generation++
        handler.removeCallbacksAndMessages(null)
        connected = false
        playing = false
        videoOutput = false
        disposePlayer()
        index = -1
        nextCandidate()
        return true
    }

    fun disconnect() {
        generation++
        handler.removeCallbacksAndMessages(null)
        connected = false
        candidates = emptyList()
        index = -1
        disposePlayer()
    }

    private fun disposePlayer() {
        val previous = player
        player = null
        runCatching { previous?.setEventListener(null) }
        runCatching { previous?.vlcVout?.detachViews() }
        if (previous != null) nativeWorker.execute {
            try { runCatching { previous.stop() } } finally { runCatching { previous.release() } }
        }
    }

    fun release() {
        if (released) return
        disconnect()
        released = true
        nativeWorker.execute { libVlc.release() }
        nativeWorker.shutdown()
    }
}
