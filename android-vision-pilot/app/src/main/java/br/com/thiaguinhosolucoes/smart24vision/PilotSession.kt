package br.com.thiaguinhosolucoes.smart24vision

object PilotSession {
    @Volatile var idToken: String = ""
    @Volatile var uid: String = ""
    @Volatile var email: String = ""
    @Volatile var storeId: String = "loja-01"
    @Volatile var cameraId: String = "CAM-01"
    @Volatile var bridgeId: String = "pilot-android-01"
    @Volatile var pilotId: String = ""
    @Volatile var sessionId: String = ""

    // Dados escolhidos pelo operador para a demonstração assistida.
    // Eles não são reconhecimento automático do SKU.
    @Volatile var demoProductName: String = "Produto de demonstração"
    @Volatile var demoSku: String = "DEMO-001"
    @Volatile var demoZoneId: String = "PRATELEIRA-DEMO"

    val authenticated: Boolean get() = idToken.isNotBlank() && uid.isNotBlank()
}
