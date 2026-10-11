package com.teppe21.finances

import com.teppe21.finances.native.receipt.ImageOrientation
import com.teppe21.finances.native.receipt.awaitAndRelease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImageOrientationTest {

    @Test
    fun `exif orientations map to one rotation and optional mirror`() {
        val expected = mapOf(
            1 to ImageOrientation.Transform(0, false),
            2 to ImageOrientation.Transform(0, true),
            3 to ImageOrientation.Transform(180, false),
            4 to ImageOrientation.Transform(180, true),
            5 to ImageOrientation.Transform(270, true),
            6 to ImageOrientation.Transform(90, false),
            7 to ImageOrientation.Transform(90, true),
            8 to ImageOrientation.Transform(270, false)
        )
        for ((exif, transform) in expected) {
            assertEquals("exif $exif", transform, ImageOrientation.fromExif(exif))
        }
    }

    @Test
    fun `unknown or undefined exif values leave the image alone`() {
        assertTrue(ImageOrientation.fromExif(0).isIdentity)
        assertTrue(ImageOrientation.fromExif(9).isIdentity)
        assertTrue(ImageOrientation.fromExif(-1).isIdentity)
        assertTrue(ImageOrientation.fromExif(1).isIdentity)
    }

    @Test
    fun `camera rotation is normalized to a quarter turn or ignored`() {
        assertEquals(0, ImageOrientation.normalizeRotation(0))
        assertEquals(90, ImageOrientation.normalizeRotation(90))
        assertEquals(270, ImageOrientation.normalizeRotation(-90))
        assertEquals(0, ImageOrientation.normalizeRotation(360))
        assertEquals(90, ImageOrientation.normalizeRotation(450))
        // Not a quarter turn: do not guess.
        assertEquals(0, ImageOrientation.normalizeRotation(45))
    }

    @Test
    fun `only quarter turns swap width and height`() {
        assertFalse(ImageOrientation.swapsDimensions(0))
        assertTrue(ImageOrientation.swapsDimensions(90))
        assertFalse(ImageOrientation.swapsDimensions(180))
        assertTrue(ImageOrientation.swapsDimensions(270))
    }

    @Test
    fun `sample size is the smallest power of two that fits the limit`() {
        assertEquals(1, ImageOrientation.sampleSize(1000, 800))
        assertEquals(1, ImageOrientation.sampleSize(2560, 1920))
        assertEquals(2, ImageOrientation.sampleSize(4000, 3000))
        assertEquals(4, ImageOrientation.sampleSize(8000, 6000))
        assertEquals(4, ImageOrientation.sampleSize(3000, 10000))
        assertEquals(1, ImageOrientation.sampleSize(0, 0))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceLeaseTest {

    private class Counter {
        var released = 0
        val release: () -> Unit = { released++ }
    }

    @Test
    fun `resource is released once after the operation succeeds`() = runTest {
        val counter = Counter()
        val result = awaitAndRelease<String>(counter.release) { done -> done(Result.success("text")) }
        assertEquals("text", result)
        assertEquals(1, counter.released)
    }

    @Test
    fun `resource is released after the operation fails and the error reaches the caller`() = runTest {
        val counter = Counter()
        try {
            awaitAndRelease<String>(counter.release) { done -> done(Result.failure(IllegalStateException("boom"))) }
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals(1, counter.released)
    }

    @Test
    fun `resource is released immediately when starting throws`() = runTest {
        val counter = Counter()
        try {
            awaitAndRelease<String>(counter.release) { throw IllegalArgumentException("bad image") }
            fail("expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("bad image", e.message)
        }
        assertEquals(1, counter.released)
    }

    @Test
    fun `resource stays open while the task runs and closes only after cancellation lets it finish`() = runTest {
        val counter = Counter()
        var finish: ((Result<String>) -> Unit)? = null
        val job = launch {
            awaitAndRelease<String>(counter.release) { done -> finish = done }
        }
        advanceUntilIdle()
        assertEquals("not released while the task is running", 0, counter.released)

        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertEquals("cancellation alone must not close a resource still in use", 0, counter.released)

        finish!!.invoke(Result.success("late result"))
        assertEquals("released once the task has finished", 1, counter.released)
    }

    @Test
    fun `a duplicate completion callback cannot release twice`() = runTest {
        val counter = Counter()
        val callbacks = CompletableDeferred<(Result<String>) -> Unit>()
        val job = launch { awaitAndRelease<String>(counter.release) { done -> callbacks.complete(done) } }
        val done = callbacks.await()
        done(Result.success("first"))
        done(Result.success("second"))
        job.join()
        assertEquals(1, counter.released)
    }
}
