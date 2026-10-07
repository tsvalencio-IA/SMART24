package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonTrackerTest {
    @Test
    fun wristsSurviveTrackingAndShortStationaryGap() {
        val tracker = PersonTracker()
        val observation = PersonObservation(
            personId = "motion",
            box = RectF(0.20f, 0.10f, 0.60f, 0.90f),
            confidence = 0.72,
            source = "MOTION_VISION_SAFE",
            leftWrist = PointF(0.30f, 0.58f),
            rightWrist = PointF(0.50f, 0.58f)
        )

        val first = tracker.update(listOf(observation), 1000L)
        assertEquals(1, first.size)
        assertNotNull(first.first().leftWrist)
        assertNotNull(first.first().rightWrist)

        val gap = tracker.update(emptyList(), 1800L)
        assertEquals(1, gap.size)
        assertTrue(gap.first().source == "MOTION_VISION_SAFE")
        assertNotNull(gap.first().leftWrist)

        val expired = tracker.update(emptyList(), 2601L)
        assertTrue(expired.isEmpty())
    }
}
