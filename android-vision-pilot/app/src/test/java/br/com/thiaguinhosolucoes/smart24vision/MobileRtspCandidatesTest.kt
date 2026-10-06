package br.com.thiaguinhosolucoes.smart24vision

import org.junit.Assert.*
import org.junit.Test

class MobileRtspCandidatesTest {
    @Test fun credentialsWithReservedCharactersRemainInsideUserInfo() {
        val urls = MobileRtspCandidates.build("192.168.15.5", "u@ser", "p:a ss?#/", 554, "")
        assertEquals("rtsp://u%40ser:p%3Aa%20ss%3F%23%2F@192.168.15.5:554/onvif1", urls.first())
        assertEquals(urls.size, urls.distinct().size)
        assertTrue(urls.any { it.contains(":5000/onvif1") })
        assertTrue(urls.any { it.contains(":8554/onvif2") })
        assertTrue(urls.any { it.contains(":554/live/ch0") })
        assertTrue(urls.any { it.contains(":554/live/ch1") })
        assertTrue(urls.any { it.contains(":554/11") })
    }
    @Test fun explicitCameraUrlNeverFallsBackToAnotherHost() {
        val urls = MobileRtspCandidates.build("192.168.15.5", "admin", "test-password", 554, "RTSP://192.168.15.9:8554/cam/realmonitor?channel=1&subtype=1")
        assertEquals(1, urls.size)
        assertEquals("rtsp://admin:test-password@192.168.15.9:8554/cam/realmonitor?channel=1&subtype=1", urls.single())
    }
    @Test fun existingUrlCredentialsAreNotOverwritten() {
        val urls = MobileRtspCandidates.build("", "ignored", "ignored", 554, "rtsp://alice:secret@192.168.15.5:554/live")
        assertEquals("rtsp://alice:secret@192.168.15.5:554/live", urls.single())
    }
    @Test fun maskHidesCredentialsAndQueryTokensForUppercaseScheme() {
        val masked = MobileRtspCandidates.mask("RTSP://alice:secret@192.168.15.5:554/live?token=private-token")
        assertFalse(masked.contains("alice")); assertFalse(masked.contains("secret")); assertFalse(masked.contains("private-token"))
        assertTrue(masked.contains("192.168.15.5:554/live"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidPort() {
        MobileRtspCandidates.build("192.168.15.5", "", "", 70000, "")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidScheme() {
        MobileRtspCandidates.build("192.168.15.5", "", "", 554, "https://example.com/video")
    }
}
