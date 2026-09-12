package dev.codebridge.app.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Feeds camera frames to ZXing; invokes [onCode] with each decoded QR string. */
class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val buffer = image.planes.first().buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val rotation = image.imageInfo.rotationDegrees

            val (width, height, luminance) = if (rotation == 90 || rotation == 270) {
                Triple(image.height, image.width, rotate90(data, image.width, image.height))
            } else {
                Triple(image.width, image.height, data)
            }

            val source = PlanarYUVLuminanceSource(
                luminance, width, height, 0, 0, width, height, false
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            onCode(result.text)
        } catch (_: Exception) {
            // Frame without a readable QR code — normal, just skip it.
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun rotate90(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - 1 - y)] = data[y * width + x]
            }
        }
        return rotated
    }
}
