package com.teppe21.finances.native.receipt

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

/**
 * Runs one asynchronous operation that borrows a resource and guarantees the resource is
 * released exactly once, and only after the operation has finished.
 *
 * [start] must begin the operation and call its argument exactly once when the operation ends
 * (extra calls are ignored). [release] runs after that, even if the awaiting coroutine was
 * cancelled in the meantime, so a recognizer is never closed while a task still uses it and is
 * never leaked by cancellation. If [start] throws, [release] runs immediately and the exception
 * is rethrown to the caller.
 */
internal suspend fun <T> awaitAndRelease(
    release: () -> Unit,
    start: (onFinished: (Result<T>) -> Unit) -> Unit
): T = suspendCancellableCoroutine { continuation ->
    val finished = AtomicBoolean(false)
    try {
        start { result ->
            if (finished.compareAndSet(false, true)) {
                try {
                    if (continuation.isActive) continuation.resumeWith(result)
                } finally {
                    release()
                }
            }
        }
    } catch (t: Throwable) {
        if (finished.compareAndSet(false, true)) {
            try {
                release()
            } finally {
                continuation.resumeWithException(t)
            }
        }
    }
}
