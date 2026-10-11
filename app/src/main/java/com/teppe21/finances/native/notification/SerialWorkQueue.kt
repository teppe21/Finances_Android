package com.teppe21.finances.native.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * Processes items strictly one at a time, in arrival order, with a bounded backlog.
 *
 * - One worker coroutine drains a fixed-capacity channel, so a burst can never create an
 *   unbounded number of suspended coroutines. When the backlog is full [offer] returns false
 *   and the item is dropped (and counted) instead of waiting.
 * - Each item gets [timeoutMillis]. A timed-out item is cancelled cooperatively, which rolls
 *   back any open database transaction, and is reported through [onFailure] as
 *   [Failure.TIMED_OUT]; it is never reported as a success.
 * - An exception from [process] is reported as [Failure.ERROR] and the queue keeps running.
 * - No lock is held while an item is processed: ordering comes from the single worker.
 */
class SerialWorkQueue<T : Any>(
    scope: CoroutineScope,
    capacity: Int = DEFAULT_CAPACITY,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val process: suspend (T) -> Unit,
    private val onFailure: suspend (T, Failure) -> Unit = { _, _ -> },
    private val onDropped: (count: Int) -> Unit = {}
) {
    enum class Failure { TIMED_OUT, ERROR }

    private val channel = Channel<T>(capacity)
    private val dropped = AtomicInteger(0)

    init {
        scope.launch {
            for (item in channel) {
                try {
                    withTimeout(timeoutMillis) { process(item) }
                } catch (e: TimeoutCancellationException) {
                    report(item, Failure.TIMED_OUT)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    report(item, Failure.ERROR)
                }
                flushDropped()
            }
        }
    }

    /** Non-blocking. Returns false when the backlog is full and the item was dropped. */
    fun offer(item: T): Boolean {
        if (channel.trySend(item).isSuccess) return true
        dropped.incrementAndGet()
        return false
    }

    /** Stops accepting items; the worker finishes what is already queued. */
    fun close() {
        channel.close()
    }

    private suspend fun report(item: T, failure: Failure) {
        try {
            onFailure(item, failure)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The failure report itself failed (for example the database is unavailable).
        }
    }

    private fun flushDropped() {
        val count = dropped.getAndSet(0)
        if (count > 0) onDropped(count)
    }

    companion object {
        const val DEFAULT_CAPACITY = 64
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}
