package com.teppe21.finances

import com.teppe21.finances.native.notification.RebindPolicy
import com.teppe21.finances.native.notification.SerialWorkQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialWorkQueueTest {

    @Test
    fun `items are processed one at a time in arrival order`() = runTest {
        val log = mutableListOf<String>()
        var running = 0
        var maxRunning = 0
        val queue = SerialWorkQueue<Int>(
            scope = this,
            process = { n ->
                running++
                maxRunning = maxOf(maxRunning, running)
                log += "start$n"
                delay(100)
                log += "end$n"
                running--
            }
        )
        repeat(3) { assertTrue(queue.offer(it)) }
        queue.close() // lets the worker finish so the test scope can complete
        advanceUntilIdle()

        assertEquals(listOf("start0", "end0", "start1", "end1", "start2", "end2"), log)
        assertEquals(1, maxRunning)
    }

    @Test
    fun `a timed out item is reported as a timeout and the next item still runs`() = runTest {
        val processed = mutableListOf<Int>()
        val failures = mutableListOf<Pair<Int, SerialWorkQueue.Failure>>()
        val queue = SerialWorkQueue<Int>(
            scope = this,
            timeoutMillis = 1_000,
            process = { n ->
                if (n == 0) delay(10_000)
                processed += n
            },
            onFailure = { item, failure -> failures += item to failure }
        )
        queue.offer(0)
        queue.offer(1)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(0 to SerialWorkQueue.Failure.TIMED_OUT), failures)
        queue.close() // lets the worker finish so the test scope can complete
        advanceUntilIdle()

        assertEquals("the stuck item never completes", listOf(1), processed)
        assertEquals(1, failures.size)
    }

    @Test
    fun `an exception is reported as an error, never as success, and the queue survives`() = runTest {
        val processed = mutableListOf<Int>()
        val failures = mutableListOf<Pair<Int, SerialWorkQueue.Failure>>()
        val queue = SerialWorkQueue<Int>(
            scope = this,
            process = { n ->
                if (n == 1) throw IllegalStateException("database unavailable")
                processed += n
            },
            onFailure = { item, failure -> failures += item to failure }
        )
        listOf(0, 1, 2).forEach { queue.offer(it) }
        queue.close() // lets the worker finish so the test scope can complete
        advanceUntilIdle()

        assertEquals(listOf(0, 2), processed)
        assertEquals(listOf(1 to SerialWorkQueue.Failure.ERROR), failures)
    }

    @Test
    fun `a failing failure report does not stop the worker`() = runTest {
        val processed = mutableListOf<Int>()
        val queue = SerialWorkQueue<Int>(
            scope = this,
            process = { n ->
                if (n == 0) throw IllegalStateException("x")
                processed += n
            },
            onFailure = { _, _ -> throw IllegalStateException("cannot record failure") }
        )
        queue.offer(0)
        queue.offer(1)
        queue.close() // lets the worker finish so the test scope can complete
        advanceUntilIdle()
        assertEquals(listOf(1), processed)
    }

    @Test
    fun `a full backlog drops new items instead of queueing unbounded work`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var droppedTotal = 0
        val processed = mutableListOf<Int>()
        val queue = SerialWorkQueue<Int>(
            scope = this,
            capacity = 2,
            timeoutMillis = 60_000,
            process = { n ->
                gate.await()
                processed += n
            },
            onDropped = { droppedTotal += it }
        )
        assertTrue(queue.offer(0))
        runCurrent() // item 0 is now being processed and waits on the gate
        assertTrue(queue.offer(1))
        assertTrue(queue.offer(2))
        assertFalse("backlog of 2 is full", queue.offer(3))
        assertFalse(queue.offer(4))

        gate.complete(Unit)
        queue.close() // lets the worker finish so the test scope can complete
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2), processed)
        assertEquals(2, droppedTotal)
    }

    @Test
    fun `closing the queue lets queued items finish and rejects new ones`() = runTest {
        val processed = mutableListOf<Int>()
        val queue = SerialWorkQueue<Int>(scope = this, process = { processed += it })
        queue.offer(0)
        queue.close()
        assertFalse(queue.offer(1))
        advanceUntilIdle()
        assertEquals(listOf(0), processed)
    }
}

class RebindPolicyTest {

    private class Clock(var now: Long = 0L) {
        fun advance(ms: Long) { now += ms }
    }

    private fun policy(clock: Clock) = RebindPolicy(
        maxAttempts = 3,
        windowMillis = 10 * 60_000L,
        baseDelayMillis = 1_000L,
        maxDelayMillis = 8_000L,
        stableMillis = 60_000L,
        clock = { clock.now }
    )

    @Test
    fun `delays double and then the budget runs out`() {
        val clock = Clock()
        val p = policy(clock)
        assertEquals(1_000L, p.nextDelayMillis())
        assertEquals(2_000L, p.nextDelayMillis())
        assertEquals(4_000L, p.nextDelayMillis())
        assertNull("no unbounded reconnect loop", p.nextDelayMillis())
        assertNull(p.nextDelayMillis())
    }

    @Test
    fun `budget returns after the window has passed`() {
        val clock = Clock()
        val p = policy(clock)
        repeat(3) { p.nextDelayMillis() }
        assertNull(p.nextDelayMillis())
        clock.advance(10 * 60_000L + 1)
        assertEquals(1_000L, p.nextDelayMillis())
    }

    @Test
    fun `a stable connection resets the history`() {
        val clock = Clock()
        val p = policy(clock)
        repeat(3) { p.nextDelayMillis() }
        p.onConnected()
        clock.advance(61_000L)
        assertEquals(1_000L, p.nextDelayMillis())
    }

    @Test
    fun `a connect and immediate disconnect loop does not reset the budget`() {
        val clock = Clock()
        val p = policy(clock)
        repeat(3) {
            p.onConnected()
            clock.advance(500L)
            assertTrue(p.nextDelayMillis() != null)
        }
        p.onConnected()
        clock.advance(500L)
        assertNull(p.nextDelayMillis())
    }
}
