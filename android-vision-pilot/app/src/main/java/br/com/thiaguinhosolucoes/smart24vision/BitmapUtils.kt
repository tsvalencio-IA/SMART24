package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.media.Image

object BitmapUtils {
    fun isMostlyBlack(bitmap: Bitmap): Boolean {
        if (bitmap.width < 2 || bitmap.height < 2) return true
        var useful = 0
        for (y in 0 until 12) for (x in 0 until 12) {
            val pixel = bitmap.getPixel(x * (bitmap.width - 1) / 11, y * (bitmap.height - 1) / 11)
            if (((pixel shr 16) and 255) > 8 || ((pixel shr 8) and 255) > 8 || (pixel and 255) > 8) useful++
        }
        return useful < 4
    }

    fun fromRgbaImage(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }
}
