package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.Image
import kotlin.math.ceil
import kotlin.math.floor

object BitmapUtils {
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

    /**
     * Detecta quando a captura recebida é praticamente toda preta.
     *
     * Alguns aplicativos de câmera protegem a superfície de vídeo e o Android
     * entrega um quadro preto ao MediaProjection. Esse quadro não pode ser
     * tratado como imagem válida para calibração ou análise.
     */
    fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var dark = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                if ((red + green + blue) / 3 < 12) dark++
                total++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && dark.toDouble() / total.toDouble() > 0.96
    }

    fun cropViewport(bitmap: Bitmap, viewport: CameraViewport): Bitmap {
        require(viewport.valid) { "Área do vídeo inválida." }
        return cropNormalized(
            bitmap,
            RectF(viewport.left, viewport.top, viewport.right, viewport.bottom)
        )
    }

    fun cropNormalized(bitmap: Bitmap, rect: RectF, padding: Float = 0f): Bitmap {
        val left = (rect.left - padding).coerceIn(0f, 1f)
        val top = (rect.top - padding).coerceIn(0f, 1f)
        val right = (rect.right + padding).coerceIn(0f, 1f)
        val bottom = (rect.bottom + padding).coerceIn(0f, 1f)
        require(right > left && bottom > top) { "Recorte normalizado inválido." }

        val x = floor(left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = floor(top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val endX = ceil(right * bitmap.width).toInt().coerceIn(x + 1, bitmap.width)
        val endY = ceil(bottom * bitmap.height).toInt().coerceIn(y + 1, bitmap.height)
        val width = endX - x
        val height = endY - y
        require(width >= 16 && height >= 16) { "Recorte pequeno demais para análise." }
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}
