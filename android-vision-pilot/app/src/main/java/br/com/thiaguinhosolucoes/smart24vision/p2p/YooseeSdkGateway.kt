package br.com.thiaguinhosolucoes.smart24vision.p2p

/**
 * Adaptador propositalmente conservador.
 *
 * Neste pacote ainda NÃO existe o binário/API oficial atual da Yoosee/Gwell.
 * Portanto nenhuma chamada de login/dispositivo/vídeo é simulada.
 *
 * Quando recebermos o SDK + AppId/AppToken válidos, este arquivo será
 * substituído pelo adaptador real, preservando o restante do SMART24.
 */
class YooseeSdkGateway : YooseeGateway {
    private var initialized = false

    override suspend fun initialize(config: YooseeSdkConfig): GatewayResult {
        if (!config.isComplete) {
            return GatewayResult.Blocked(
                code = "SDK_CREDENTIALS_MISSING",
                message = "Faltam AppId/AppToken/AppVersion do SDK Yoosee/Gwell."
            )
        }

        val report = YooseeSdkProbe.inspect()
        if (!report.legacyP2pCorePresent) {
            return GatewayResult.Blocked(
                code = "SDK_BINARY_MISSING",
                message = "Credenciais podem estar configuradas, mas o SDK P2P ainda não está incorporado ao APK."
            )
        }

        initialized = true
        return GatewayResult.Ok(
            "SDK detectado. Próximo passo: implementar e validar o adaptador para a versão fornecida pela Yoosee/Gwell."
        )
    }

    override suspend fun login(email: String, password: CharArray): GatewayResult {
        try {
            if (!initialized) {
                return GatewayResult.Blocked("SDK_NOT_INITIALIZED", "Inicialize o gateway antes do login.")
            }
            if (email.isBlank() || password.isEmpty()) {
                return GatewayResult.Blocked("ACCOUNT_REQUIRED", "Informe a conta Yoosee do SMART24.")
            }

            return GatewayResult.Blocked(
                "REAL_LOGIN_ADAPTER_PENDING",
                "O login não será simulado. Falta ligar este contrato à API oficial do SDK Yoosee/Gwell."
            )
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun listDevices(): DeviceListResult =
        DeviceListResult(
            emptyList(),
            GatewayResult.Blocked(
                "REAL_DEVICE_LIST_ADAPTER_PENDING",
                "A listagem de câmeras compartilhadas depende da API oficial do SDK."
            )
        )

    override suspend fun openLiveStream(
        device: YooseeDevice,
        frameConsumer: (VideoFrame) -> Unit
    ): GatewayResult =
        GatewayResult.Blocked(
            "REAL_STREAM_ADAPTER_PENDING",
            "O vídeo remoto não será falsificado. Falta o adaptador oficial P2P."
        )

    override suspend fun closeLiveStream(): GatewayResult =
        GatewayResult.Ok("Nenhum stream P2P real estava aberto.")

    override suspend fun logout(): GatewayResult =
        GatewayResult.Ok("Sessão local encerrada.")
}
