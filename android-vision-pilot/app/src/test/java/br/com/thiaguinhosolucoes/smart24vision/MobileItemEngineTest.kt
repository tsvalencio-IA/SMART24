package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MobileItemEngineTest {
    private val zone = Zone("Z1", "loja-01", "CAM-01", 0f, 0f, 0.5f, 0.5f, "P1", "Produto teste", "SKU1")
    private fun frame(inverted: Boolean = false): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until 96) for (x in 0 until 96) setPixel(x, y, if ((x < 24) xor inverted) Color.WHITE else Color.BLACK)
    }
    private fun result(time: Long, inside: Boolean = false, persons: Boolean = true) = VisionResult(96, 96,
        if (persons) listOf(PersonObservation("P1", RectF(0.65f, 0.65f, 0.95f, 0.95f), 0.9, "TEST", leftWrist = if (inside) PointF(.2f, .2f) else PointF(.8f, .8f))) else emptyList(), emptyList(), emptyList(), time)
    @Test fun unchangedShelfCreatesOnlyInteractionAfterHandLeaves() {
        val engine = MobileItemEngine(); val base = frame()
        assertTrue(engine.update(base, result(10000), listOf(zone)).isEmpty())
        engine.update(base, result(11000, true), listOf(zone))
        assertTrue(engine.update(base, result(12000), listOf(zone)).isEmpty())
        assertEquals("SHELF_INTERACTION", engine.update(base, result(13000), listOf(zone)).single().type)
        assertFalse(base.isRecycled)
    }
    @Test fun shelfChangeCreatesProbablePickupAndRestorationCreatesProbableReturn() {
        val engine = MobileItemEngine(); val base = frame(); val changed = frame(true)
        engine.update(base, result(10000), listOf(zone))
        engine.update(base, result(11000, true), listOf(zone))
        engine.update(changed, result(12000), listOf(zone))
        assertEquals("ITEM_PICKED_PROBABLE", engine.update(changed, result(13000), listOf(zone)).single().type)
        engine.update(changed, result(15000, true), listOf(zone))
        engine.update(base, result(16000), listOf(zone))
        assertEquals("ITEM_RETURNED_PROBABLE", engine.update(base, result(17000), listOf(zone)).single().type)
    }
    @Test fun trackingLossNeverBecomesAProductPickup() {
        val engine = MobileItemEngine(); val base = frame(); val changed = frame(true)
        engine.update(base, result(10000), listOf(zone))
        engine.update(base, result(11000, true), listOf(zone))
        assertTrue(engine.update(changed, result(13000, persons = false), listOf(zone)).isEmpty())
        assertTrue(engine.update(changed, result(14000), listOf(zone)).isEmpty())
    }
    @Test fun fullFrameZoneDoesNotRecycleCallerBitmap() {
        val bitmap = frame()
        MobileItemEngine().update(bitmap, result(10000, persons = false), listOf(zone.copy(right = 1f, bottom = 1f)))
        assertFalse(bitmap.isRecycled)
    }
}
