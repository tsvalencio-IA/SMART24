package br.com.thiaguinhosolucoes.smart24vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManifestTest {
    @Test fun parsesOfficialReleaseManifest() {
        val parsed = AppUpdateManifest.parse(
            """{
              "enabled": true,
              "versionCode": 12,
              "versionName": "3.3.2-auto-update",
              "apkUrl": "https://github.com/tsvalencio-IA/SMART24/releases/download/v3.3.2/SMART24.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }"""
        )
        assertTrue(parsed.enabled)
        assertEquals(12, parsed.versionCode)
        assertEquals("3.3.2-auto-update", parsed.versionName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsApkOutsideOfficialRepository() {
        AppUpdateManifest.parse(
            """{
              "enabled": true,
              "versionCode": 12,
              "versionName": "x",
              "apkUrl": "https://example.com/app.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }"""
        )
    }

    @Test fun disabledManifestStillParsesSafely() {
        val parsed = AppUpdateManifest.parse(
            """{
              "enabled": false,
              "versionCode": 11,
              "versionName": "3.3.1-sala-oficina",
              "apkUrl": "https://github.com/tsvalencio-IA/SMART24/releases/download/v3.3.1-build62/SMART24-Vigilante-Celular-v3.3.1-Sala-Oficina-build62.apk",
              "sha256": "2efac3421c46b35f881f19024f02dc4dff1650b4f4eac70df867c685ac0de8c9"
            }"""
        )
        assertFalse(parsed.enabled)
    }
}
