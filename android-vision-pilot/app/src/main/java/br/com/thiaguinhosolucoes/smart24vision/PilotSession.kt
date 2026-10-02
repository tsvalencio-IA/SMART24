package br.com.thiaguinhosolucoes.smart24vision

object PilotSession {
    @Volatile var idToken: String = ""
    @Volatile var refreshToken: String = ""
    @Volatile var tokenExpiresAt: Long = Long.MAX_VALUE
    @Volatile var uid: String = ""
    @Volatile var email: String = ""
    @Volatile var storeId: String = "loja-01"
    @Volatile var cameraId: String = "CAM-01"
    @Volatile var bridgeId: String = "pilot-android-01"
    @Volatile var pilotId: String = ""
    @Volatile var sessionId: String = ""

    val authenticated: Boolean get() = idToken.isNotBlank() && uid.isNotBlank()

    fun clearAuthentication() {
        idToken = ""
        refreshToken = ""
        tokenExpiresAt = Long.MAX_VALUE
        uid = ""
        email = ""
    }
}
