package pt.avus.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Like [kotlinx.coroutines.flow.update], but [transform] may suspend (e.g. do heavy work on another
 * dispatcher). If the state changed while it ran, it runs again on the new state, so concurrent
 * changes are never overwritten. Returns the value that was set.
 */
suspend fun <T> MutableStateFlow<T>.updateSuspending(transform: suspend (T) -> T): T {
    while (true) {
        val current = value
        val next = transform(current)
        if (compareAndSet(current, next)) return next
    }
}
