package br.com.thiaguinhosolucoes.smart24vision.p2p

/**
 * Contrato do "usuário robô" SMART24.
 *
 * Regra: a camada de IA NÃO conhece detalhes do SDK Yoosee/Gwell.
 * Ela recebe frames por este contrato. Assim podemos trocar Android/PC/SDK
 * sem reescrever o motor de visão.
 */
interface YooseeGateway {
    suspend fun initialize(config: YooseeSdkConfig): GatewayResult
    suspend fun login(email: String, password: CharArray): GatewayResult
    suspend fun listDevices(): DeviceListResult

    /**
     * Abre o vídeo remoto de uma câmera autorizada para a conta SMART24.
     * O adaptador real do SDK deve converter o stream para frames.
     */
    suspend fun openLiveStream(
        device: YooseeDevice,
        frameConsumer: (VideoFrame) -> Unit
    ): GatewayResult

    suspend fun closeLiveStream(): GatewayResult
    suspend fun logout(): GatewayResult
}

data class YooseeSdkConfig(
    val appId: String,
    val appToken: String,
    val appVersion: String
) {
    val isComplete: Boolean
        get() = appId.isNotBlank() && appToken.isNotBlank() && appVersion.isNotBlank()
}

data class YooseeDevice(
    val deviceId: String,
    val name: String,
    val online: Boolean,
    val sharedWithAccount: Boolean,
    val rawType: String? = null
)

data class VideoFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val timestampMs: Long,
    val format: FrameFormat
)

enum class FrameFormat {
    NV21,
    I420,
    RGB24,
    JPEG,
    H264_ACCESS_UNIT,
    UNKNOWN
}

sealed class GatewayResult {
    data class Ok(val message: String) : GatewayResult()
    data class Blocked(val code: String, val message: String) : GatewayResult()
    data class Error(val code: String, val message: String, val cause: Throwable? = null) : GatewayResult()
}

data class DeviceListResult(
    val devices: List<YooseeDevice>,
    val result: GatewayResult
)
