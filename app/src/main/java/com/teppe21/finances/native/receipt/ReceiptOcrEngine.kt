@file:Suppress("ExifInterface") // framework ExifInterface(InputStream) is available from API 24; minSdk is 26

package com.teppe21.finances.native.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.teppe21.finances.domain.model.ReceiptScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * On-device receipt OCR.
 *
 * Each call decodes at most one down-sampled bitmap off the main thread, runs one recognizer
 * and releases both the recognizer and the bitmap once ML Kit has finished with them (see
 * [awaitAndRelease]), including when the calling coroutine is cancelled. Neither the image
 * nor the recognized text is stored; only the parsed [ReceiptScan] is returned.
 */
class ReceiptOcrEngine(
    context: Context,
    private val recognizerFactory: () -> TextRecognizer = {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
) {
    private val contentResolver = context.applicationContext.contentResolver

    /**
     * A JPEG from CameraX's in-memory capture. [rotationDegrees] is `ImageInfo.rotationDegrees`:
     * the clockwise rotation that makes the image upright. It is applied once, by ML Kit.
     */
    suspend fun recognizeCapture(jpeg: ByteArray, rotationDegrees: Int): ReceiptScan =
        withContext(Dispatchers.Default) {
            val bitmap = decodeSampled(
                bounds = { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, it) },
                decode = { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, it) }
            )
            recognize(bitmap, ImageOrientation.normalizeRotation(rotationDegrees))
        }

    /** An image picked from the gallery; its EXIF orientation is applied once to the bitmap. */
    suspend fun recognizeFromUri(uri: Uri): ReceiptScan = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(
            bounds = { options -> openStream(uri).use { BitmapFactory.decodeStream(it, null, options) } },
            decode = { options -> openStream(uri).use { BitmapFactory.decodeStream(it, null, options) } }
        )
        val upright = try {
            val transform = openStream(uri).use { ImageOrientation.fromExif(readExifOrientation(it)) }
            applyTransform(bitmap, transform)
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
        recognize(upright, 0)
    }

    /** Takes ownership of [bitmap]: it is recycled when recognition is finished. */
    private suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): ReceiptScan {
        val recognizer = try {
            recognizerFactory()
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
        val text = awaitAndRelease<String>(
            release = {
                recognizer.close()
                bitmap.recycle()
            }
        ) { onFinished ->
            recognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees))
                .addOnCompleteListener { task ->
                    onFinished(
                        if (task.isSuccessful) Result.success(task.result.text)
                        else Result.failure(task.exception ?: IOException("Text recognition failed"))
                    )
                }
        }
        return ReceiptParser.parse(text)
    }

    private fun openStream(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw IOException("Cannot open image")

    // The framework ExifInterface reads from a stream on API 24+ (minSdk is 26); no extra dependency needed.
    private fun readExifOrientation(stream: InputStream): Int =
        try {
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private inline fun decodeSampled(
        bounds: (BitmapFactory.Options) -> Bitmap?,
        decode: (BitmapFactory.Options) -> Bitmap?
    ): Bitmap {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        bounds(probe)
        if (probe.outWidth <= 0 || probe.outHeight <= 0) throw IOException("Not a readable image")
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageOrientation.sampleSize(probe.outWidth, probe.outHeight)
        }
        return try {
            decode(options) ?: throw IOException("Image could not be decoded")
        } catch (e: OutOfMemoryError) {
            throw IOException("Image is too large to read", e)
        }
    }

    private fun applyTransform(bitmap: Bitmap, transform: ImageOrientation.Transform): Bitmap {
        if (transform.isIdentity) return bitmap
        val matrix = Matrix().apply {
            if (transform.mirrorHorizontally) postScale(-1f, 1f)
            postRotate(transform.rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
