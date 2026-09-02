package br.com.thiaguinhosolucoes.smart24vision.p2p

import br.com.thiaguinhosolucoes.smart24vision.BuildConfig

/**
 * Verifica somente o que podemos provar dentro do APK.
 * NÃO afirma que o SDK funciona; apenas se credenciais/classes conhecidas existem.
 */
object YooseeSdkProbe {
    data class Report(
        val appIdConfigured: Boolean,
        val appTokenConfigured: Boolean,
        val appVersionConfigured: Boolean,
        val legacyP2pCorePresent: Boolean,
        val readyForRealAdapter: Boolean,
        val summary: String
    )

    fun inspect(): Report {
        val appId = BuildConfig.YOOSEE_APP_ID.trim()
        val appToken = BuildConfig.YOOSEE_APP_TOKEN.trim()
        val appVersion = BuildConfig.YOOSEE_APP_VERSION.trim()

        val legacyPresent = classExists("com.p2p.core.P2PSpecial")

        val credentialsOk = appId.isNotBlank() && appToken.isNotBlank() && appVersion.isNotBlank()
        val ready = credentialsOk && legacyPresent

        val summary = buildString {
            appendLine("SMART24 — diagnóstico P2P Yoosee")
            appendLine("AppId configurado: ${yesNo(appId.isNotBlank())}")
            appendLine("AppToken configurado: ${yesNo(appToken.isNotBlank())}")
            appendLine("AppVersion configurada: ${yesNo(appVersion.isNotBlank())}")
            appendLine("P2P-Core legado presente no APK: ${yesNo(legacyPresent)}")
            appendLine()
            if (!credentialsOk) {
                appendLine("BLOQUEIO: faltam credenciais de desenvolvedor fornecidas pela Yoosee/Gwell.")
            }
            if (!legacyPresent) {
                appendLine("BLOQUEIO: nenhum SDK P2P compatível foi incorporado ao APK.")
            }
            if (ready) {
                appendLine("Pré-requisitos básicos detectados. Ainda é obrigatório validar login, lista de dispositivos compartilhados e vídeo remoto em aparelho real.")
            }
        }

        return Report(
            appIdConfigured = appId.isNotBlank(),
            appTokenConfigured = appToken.isNotBlank(),
            appVersionConfigured = appVersion.isNotBlank(),
            legacyP2pCorePresent = legacyPresent,
            readyForRealAdapter = ready,
            summary = summary.trim()
        )
    }

    private fun classExists(name: String): Boolean =
        runCatching { Class.forName(name) }.isSuccess

    private fun yesNo(value: Boolean) = if (value) "SIM" else "NÃO"
}
