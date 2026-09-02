package br.com.thiaguinhosolucoes.smart24vision.p2p

import br.com.thiaguinhosolucoes.smart24vision.BuildConfig

class YooseeUserBotController(
    private val gateway: YooseeGateway = YooseeSdkGateway()
) {
    suspend fun preflight(): GatewayResult {
        val config = YooseeSdkConfig(
            appId = BuildConfig.YOOSEE_APP_ID,
            appToken = BuildConfig.YOOSEE_APP_TOKEN,
            appVersion = BuildConfig.YOOSEE_APP_VERSION
        )
        return gateway.initialize(config)
    }

    suspend fun login(email: String, password: CharArray): GatewayResult =
        gateway.login(email, password)

    suspend fun devices(): DeviceListResult = gateway.listDevices()

    suspend fun logout(): GatewayResult = gateway.logout()
}
