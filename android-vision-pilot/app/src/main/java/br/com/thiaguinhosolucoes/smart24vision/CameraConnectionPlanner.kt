package br.com.thiaguinhosolucoes.smart24vision

import java.net.URI

/** Runs off the UI thread; only validated stream addresses reach LibVLC. */
class CameraConnectionPlanner(
    private val probe: CameraRtspProbe = CameraRtspProbe(),
    private val onvif: OnvifStreamResolver? = null,
    private val checkCancelled: () -> Unit = {},
    private val progress: (String) -> Unit = {}
) {
    data class Plan(val urls: List<String>, val message: String, val report: String, val needsCredentials: Boolean = false)

    fun resolve(host: String, user: String, password: String, port: Int, explicit: String, serviceUrls: List<String> = emptyList()): Plan {
        val manual = MobileRtspCandidates.build(host, user, password, port, explicit)
        val cameraHost = URI(manual.first()).host.removePrefix("[").removeSuffix("]")
        val reports = mutableListOf("SMART24 3.1 • câmera $cameraHost")
        val urls = linkedSetOf<String>()
        val resolvedUrls = linkedSetOf<String>()
        var onvifDenied = false
        if (explicit.isBlank()) {
            val result = onvif?.resolve(cameraHost, user, password, serviceUrls)
            if (result != null) {
                reports += result.report
                onvifDenied = result.authRequired
                result.urls.forEach { url -> resolvedUrls += MobileRtspCandidates.build(cameraHost, user, password, port, url).single() }
                urls += resolvedUrls
            }
        }
        val cameraPorts = manual.map { URI(it).port.takeIf { value -> value > 0 } ?: 554 }.distinct()
        val open = mutableSetOf<Int>()
        cameraPorts.forEach { candidatePort ->
            checkCancelled()
            progress("Verificando acesso à câmera $cameraHost:$candidatePort…")
            val available = probe.portOpen(cameraHost, candidatePort)
            reports += "Porta $candidatePort: ${if (available) "acessível" else "sem conexão TCP"}."
            if (available) open += candidatePort
        }
        manual.filter { (URI(it).port.takeIf { value -> value > 0 } ?: 554) in open }.forEach(urls::add)
        val valid = linkedSetOf<String>()
        var wrongPath = false
        var responded = false
        var announcedWithoutVideo = false
        for ((index, url) in urls.withIndex()) {
            if (valid.isNotEmpty() && url !in resolvedUrls) break
            checkCancelled()
            progress("Conferindo o endereço do vídeo ${index + 1}/${urls.size}…")
            val result = probe.describe(url)
            val masked = MobileRtspCandidates.mask(url)
            reports += "$masked: ${result.status}${result.code?.let { " ($it)" }.orEmpty()}."
            if (result.code != null) responded = true
            when (result.status) {
                CameraRtspProbe.Status.VIDEO, CameraRtspProbe.Status.SDP_RESPONSE, CameraRtspProbe.Status.AUTH_UNSUPPORTED -> valid += url
                CameraRtspProbe.Status.AUTH_REQUIRED -> return Plan(emptyList(),
                    "A câmera respondeu e exige usuário/senha NVR/RTSP. Preencha os campos da seção CÂMERA IP e conecte novamente.", reports.joinToString("\n"), true)
                CameraRtspProbe.Status.AUTH_REJECTED -> return Plan(emptyList(),
                    "A câmera recusou o usuário/senha NVR/RTSP. Confira as credenciais configuradas no equipamento.", reports.joinToString("\n"), true)
                CameraRtspProbe.Status.PATH_NOT_FOUND -> wrongPath = true
                CameraRtspProbe.Status.NO_VIDEO -> announcedWithoutVideo = true
                else -> Unit
            }
            if (valid.size >= 2) break
            if (valid.isNotEmpty() && url !in resolvedUrls) break
        }
        val message = when {
            valid.isNotEmpty() -> "O RTSP respondeu, mas não entregou quadros de vídeo. Foram testados TCP, decodificação por software e UDP.\nConfira se a câmera está transmitindo vídeo e se a rede permite o fluxo."
            open.isEmpty() && urls.isEmpty() -> "Sem acesso RTSP à câmera $cameraHost nas portas ${cameraPorts.joinToString()}. Confira o IP, o NVR/RTSP e se celular/câmera estão no mesmo Wi-Fi, sem isolamento de dispositivos."
            onvifDenied -> "O serviço ONVIF recusou o acesso e nenhum vídeo RTSP foi localizado. Confira o usuário/senha ONVIF/NVR da câmera."
            wrongPath -> "A câmera respondeu ao RTSP, mas os caminhos testados não localizaram o vídeo. Use a URL RTSP fornecida pela câmera ou confira o serviço ONVIF."
            announcedWithoutVideo -> "A câmera respondeu, mas não anunciou um fluxo de vídeo utilizável. Confira a configuração NVR/RTSP."
            responded -> "O serviço RTSP respondeu, mas não autorizou um fluxo de vídeo utilizável. Veja o diagnóstico da conexão."
            else -> "A porta de rede abriu, mas não houve resposta RTSP utilizável. Confira se a porta informada é a de vídeo NVR/RTSP."
        }
        return Plan(valid.toList(), message, reports.joinToString("\n"), onvifDenied && valid.isEmpty())
    }
}
