package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZoneEditingTest {
    @Test fun markingKeepsItsPositionWhenKeyboardResizesPreview() {
        val view = ZoneView(ApplicationProvider.getApplicationContext())
        view.bitmap = Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888)
        view.layout(0,0,640,480)
        view.draw(Canvas(Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)))
        fun tap(x: Float,y: Float) {
            view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0))
            view.onTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0))
        }
        tap(64f,96f); tap(320f,240f)
        val before = view.normalizedRect()!!
        assertArrayEquals(floatArrayOf(.1f,.1f,.5f,.5f),before,.001f)
        view.layout(0,0,320,160)
        view.draw(Canvas(Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888)))
        assertArrayEquals(before,view.normalizedRect(),.00001f)
    }
    @Test fun editingOneAreaKeepsOtherProductsAndCameraScopes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MobileZoneStore.clear(context,"test","CAM-01")
        MobileZoneStore.clear(context,"test","CAM-02")
        val a = Zone("A","test","CAM-01",.1f,.2f,.4f,.6f,"P1","Produto 1","SKU1")
        val b = a.copy(zoneId="B",sku="SKU2",productId="P2")
        val other = a.copy(cameraId="CAM-02")
        MobileZoneStore.upsert(context,a); MobileZoneStore.upsert(context,b); MobileZoneStore.upsert(context,other)
        MobileZoneStore.upsert(context,a.copy(locationName="Armário 2",productName="Produto editado"))
        val saved = MobileZoneStore.load(context,"test","CAM-01")
        assertEquals(2,saved.size)
        assertEquals("P1",saved.single { it.zoneId=="A" }.productId)
        assertEquals("Armário 2",saved.single { it.zoneId=="A" }.locationName)
        MobileZoneStore.remove(context,"test","CAM-01","A")
        assertEquals("B",MobileZoneStore.load(context,"test","CAM-01").single().zoneId)
        assertEquals("A",MobileZoneStore.load(context,"test","CAM-02").single().zoneId)
    }
}
