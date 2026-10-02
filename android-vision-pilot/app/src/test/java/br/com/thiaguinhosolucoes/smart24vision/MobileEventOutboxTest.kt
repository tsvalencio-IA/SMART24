package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MobileEventOutboxTest {
    private lateinit var context: Context
    @Before fun clean() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("smart24_mobile_events", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun queueSurvivesRecreationAndAcknowledgementUsesSameId() {
        val original = MobileEventOutbox(context)
        val id = original.add(mapOf("type" to "SHELF_INTERACTION", "storeId" to "loja-01", "createdAt" to 123), "user-A")
        val recreated = MobileEventOutbox(context)
        assertEquals(id, recreated.pending("user-A").single().getString("id"))
        recreated.uploaded(id)
        assertEquals(0, original.count())
    }
    @Test fun anotherAccountCannotUploadPreviousAccountOrLocalTestEvents() {
        val outbox = MobileEventOutbox(context)
        outbox.add(mapOf("type" to "SHELF_INTERACTION"), "user-A")
        outbox.add(mapOf("type" to "SHELF_INTERACTION"), "")
        assertTrue(outbox.pending("user-B").isEmpty())
        assertTrue(outbox.pending("").isEmpty())
        assertEquals(1, outbox.localOnlyCount())
    }
}
