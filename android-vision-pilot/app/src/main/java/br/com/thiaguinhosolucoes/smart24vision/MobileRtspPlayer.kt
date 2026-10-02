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

/** Every attempt owns its player, callbacks and deadline. Credentials stay in memory. */
class MobileRtspPlayer(context: Context, private val textureView: TextureView, private val listener: Listener) {
    interface Listener {
        fun onConnecting(candidateNumber: Int, total: Int)
        fun onConnected(maskedUrl: String)
        fun onFailed(message: String)
        fun onInterrupted(message: String)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--network-caching=450", "--verbose=0"))
    private var player: MediaPlayer? = null
    private var candidates: List<String> = emptyList()
    private var index = -1
    private var generation = 0
    private var connected = false
    private var released = false
    private var paused = false
    private var playing = false
    private var videoOutput = false
    val isConnected: Boolean get() = connected && !paused

    fun connect(host: String, username: String, password: String, preferredPort: Int, explicitUrl: String) {
        disconnect()
        if (released) return
        candidates = runCatching { MobileRtspCandidates.build(host, username, password, preferredPort, explicitUrl) }
            .getOrElse { listener.onFailed(it.message ?: "Configuração RTSP inválida."); return }
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
            listener.onFailed("Nenhum fluxo entregou imagem. Confira o IP, a senha NVR/RTSP e a rede Wi-Fi. A câmera precisa disponibilizar RTSP; ONVIF sozinho não garante vídeo.")
            return
        }
        listener.onConnecting(index + 1, candidates.size)
        textureView.post {
            if (released || paused || token != generation) return@post
            val active = MediaPlayer(libVlc)
            player = active
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
            val media = Media(libVlc, Uri.parse(candidates[index])).apply {
                setHWDecoderEnabled(true, false)
                addOption(":rtsp-tcp")
                addOption(":network-caching=450")
                addOption(":no-audio")
            }
            active.media = media
            media.release()
            active.play()
            pollFirstFrame(token)
            handler.postDelayed({ if (!connected && token == generation) nextCandidate() }, 8500L)
        }
    }

    private fun pollFirstFrame(token: Int) {
        if (released || paused || token != generation || connected) return
        if (playing && videoOutput && textureView.isAvailable) {
            val frame = textureView.getBitmap(160, 90)
            val useful = frame != null && !BitmapUtils.isMostlyBlack(frame)
            frame?.recycle()
            if (useful) {
                connected = true
                handler.removeCallbacksAndMessages(null)
                listener.onConnected(MobileRtspCandidates.mask(candidates[index]))
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
        paused = true
        handler.removeCallbacksAndMessages(null)
        player?.pause()
    }

    fun resume() {
        if (!paused || released) return
        paused = false
        if (connected) player?.play() else if (index in candidates.indices) { index--; nextCandidate() }
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
        previous?.setEventListener(null)
        runCatching { previous?.stop() }
        runCatching { previous?.vlcVout?.detachViews() }
        runCatching { previous?.release() }
    }

    fun release() {
        if (released) return
        disconnect()
        released = true
        libVlc.release()
    }
}
