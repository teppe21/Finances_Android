package com.teppe21.finances.native.receipt

/**
 * Pure orientation and sizing rules for the capture-to-OCR path, kept free of Android types
 * so they can be unit tested on the JVM.
 *
 * Rotation is applied exactly once per image:
 * - CameraX in-memory captures carry the needed rotation in `ImageInfo.rotationDegrees`
 *   (the JPEG bytes are not rotated); it is handed to ML Kit and the bitmap is left alone.
 * - Gallery images carry an EXIF orientation; it is baked into the decoded bitmap once and
 *   ML Kit is then told the rotation is 0.
 */
object ImageOrientation {

    /** Longest side the decoder aims for. OCR does not improve beyond this; memory use does. */
    const val MAX_DECODE_SIDE = 2560

    /** A rotation (clockwise, multiple of 90) plus an optional horizontal mirror applied first. */
    data class Transform(val rotationDegrees: Int, val mirrorHorizontally: Boolean) {
        val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrorHorizontally

        companion object {
            val IDENTITY = Transform(0, false)
        }
    }

    // android.media.ExifInterface orientation constants, duplicated so this file stays JVM-pure.
    private const val ORIENTATION_NORMAL = 1
    private const val ORIENTATION_FLIP_HORIZONTAL = 2
    private const val ORIENTATION_ROTATE_180 = 3
    private const val ORIENTATION_FLIP_VERTICAL = 4
    private const val ORIENTATION_TRANSPOSE = 5
    private const val ORIENTATION_ROTATE_90 = 6
    private const val ORIENTATION_TRANSVERSE = 7
    private const val ORIENTATION_ROTATE_270 = 8

    /** Maps an EXIF orientation tag to the transform that makes the image upright. */
    fun fromExif(orientation: Int): Transform = when (orientation) {
        ORIENTATION_FLIP_HORIZONTAL -> Transform(0, true)
        ORIENTATION_ROTATE_180 -> Transform(180, false)
        ORIENTATION_FLIP_VERTICAL -> Transform(180, true)
        ORIENTATION_TRANSPOSE -> Transform(270, true)
        ORIENTATION_ROTATE_90 -> Transform(90, false)
        ORIENTATION_TRANSVERSE -> Transform(90, true)
        ORIENTATION_ROTATE_270 -> Transform(270, false)
        ORIENTATION_NORMAL -> Transform.IDENTITY
        else -> Transform.IDENTITY
    }

    /**
     * Normalizes a rotation reported by CameraX into 0, 90, 180 or 270. Anything that is not a
     * multiple of 90 is treated as 0 rather than guessed.
     */
    fun normalizeRotation(degrees: Int): Int {
        val d = ((degrees % 360) + 360) % 360
        return if (d % 90 == 0) d else 0
    }

    /** True when the upright image has its width and height swapped relative to the decoded one. */
    fun swapsDimensions(rotationDegrees: Int): Boolean = normalizeRotation(rotationDegrees) % 180 != 0

    /** Smallest power-of-two `inSampleSize` that brings the longest side down to [maxSide] or less. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_DECODE_SIDE): Int {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > maxSide) sample *= 2
        return sample
    }
}
