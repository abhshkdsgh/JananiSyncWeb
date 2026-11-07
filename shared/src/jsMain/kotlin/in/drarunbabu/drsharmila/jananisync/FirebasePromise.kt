@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * Firebase rejects its JS promises with native Error objects. Kotlin's Promise.await()
 * expects a Kotlin Throwable, so normalize the rejection before it crosses into coroutine
 * machinery (otherwise some Kotlin/JS versions fail with an opaque ClassCastException).
 */
private class FirebasePromiseResult(val value: dynamic, val error: String?)

private fun firebaseFailureMessage(reason: dynamic): String {
    // Preserve the SDK code without exposing server messages or user data to the UI.
    val code = if (reason != null && jsTypeOf(reason.code) == "string") {
        reason.code as String
    } else null
    // Auth uses "auth/..."; Firestore uses bare codes such as "failed-precondition".
    return if (code != null && code.matches(Regex("^(?:[a-z-]+/)?[a-z-]+$"))) {
        "Firebase operation failed ($code)."
    } else {
        "Firebase operation failed."
    }
}

internal fun Promise<Unit>.ignoringFirebaseFailure(): Promise<Unit> {
    val source: dynamic = this
    val normalized = source.then(
        { _: dynamic -> },
        { _: dynamic -> }
    )
    return normalized.unsafeCast<Promise<Unit>>()
}

internal suspend fun <T> Promise<T>.awaitFirebase(): T {
    // Promise.await() already handles coroutine cancellation. Wrapping it in
    // withContext(NonCancellable) adds another dispatched continuation and has caused
    // completed-continuation ClassCastExceptions on Kotlin/JS click paths.
    val result = settled { reason -> firebaseFailureMessage(reason) }.await()
    result.error?.let { throw IllegalStateException(it) }
    return result.value.unsafeCast<T>()
}

/** Web Crypto rejects with DOMException objects rather than Firebase error codes. */
internal suspend fun <T> Promise<T>.awaitCrypto(): T {
    val result = settled { _ -> "The browser cryptographic operation failed." }.await()
    result.error?.let { throw IllegalStateException(it) }
    return result.value.unsafeCast<T>()
}

internal suspend fun Promise<Unit>.awaitFirebaseUnit() {
    val result = settled { reason -> firebaseFailureMessage(reason) }.await()
    result.error?.let { throw IllegalStateException(it) }
}

private fun <T> Promise<T>.settled(failureMessage: (dynamic) -> String): Promise<FirebasePromiseResult> {
    val source: dynamic = this
    // Convert both fulfillment and rejection into a fulfilled Promise first. Then use
    // kotlinx.coroutines' supported JS Promise.await bridge instead of resuming a Kotlin
    // continuation from a hand-written JS callback.
    return source.then(
        { value: dynamic -> FirebasePromiseResult(value, null) },
        { reason: dynamic -> FirebasePromiseResult(null, failureMessage(reason)) }
    ).unsafeCast<Promise<FirebasePromiseResult>>()
}
