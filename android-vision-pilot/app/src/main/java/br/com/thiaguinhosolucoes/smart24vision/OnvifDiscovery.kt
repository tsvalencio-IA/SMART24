package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

/** Descoberta WS-Discovery usada por dispositivos ONVIF na rede local. */
class OnvifDiscovery(context: Context) {
    private val appContext = context.applicationContext

    data class DiscoveredCamera(
        val host: String,
        val managementPort: Int?,
        val serviceUrls: List<String>
    )

    suspend fun discover(timeoutMs: Long = 4200L): List<DiscoveredCamera> = withContext(Dispatchers.IO) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("SMART24-ONVIF")
        multicastLock?.setReferenceCounted(false)
        multicastLock?.acquire()

        val responses = linkedSetOf<String>()
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = 450
            val destination = InetAddress.getByName(WS_DISCOVERY_ADDRESS)
            probeMessages().forEach { xml ->
                val data = xml.toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(data, data.size, destination, WS_DISCOVERY_PORT))
            }

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            val buffer = ByteArray(64 * 1024)
            while (SystemClock.elapsedRealtime() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(packet) }
                    .onSuccess {
                        val xml = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        extractXAddrs(xml).forEach(responses::add)
                    }
            }
        } finally {
            socket.close()
            runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
        }

        responses
            .groupBy { serviceUrl -> Uri.parse(serviceUrl).host.orEmpty() }
            .filterKeys(String::isNotBlank)
            .map { (host, urls) ->
                val ports = urls.mapNotNull { url -> Uri.parse(url).port.takeIf { it > 0 } }
                DiscoveredCamera(host, ports.firstOrNull(), urls.distinct())
            }
            .sortedBy { it.host }
    }

    private fun probeMessages(): List<String> = listOf(
        probe("<d:Types>dn:NetworkVideoTransmitter</d:Types>"),
        probe("")
    )

    private fun probe(types: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
            xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
          <e:Header>
            <w:MessageID>uuid:${UUID.randomUUID()}</w:MessageID>
            <w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
            <w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
          </e:Header>
          <e:Body><d:Probe>$types</d:Probe></e:Body>
        </e:Envelope>
    """.trimIndent()

    private fun extractXAddrs(xml: String): List<String> = XADDRS_REGEX
        .findAll(xml)
        .flatMap { match ->
            decodeXml(match.groupValues[1]).trim().split(Regex("\\s+")).asSequence()
        }
        .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
        .toList()

    private fun decodeXml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    companion object {
        private const val WS_DISCOVERY_ADDRESS = "239.255.255.250"
        private const val WS_DISCOVERY_PORT = 3702
        private val XADDRS_REGEX = Regex(
            "<(?:[A-Za-z0-9_-]+:)?XAddrs[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?XAddrs>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
