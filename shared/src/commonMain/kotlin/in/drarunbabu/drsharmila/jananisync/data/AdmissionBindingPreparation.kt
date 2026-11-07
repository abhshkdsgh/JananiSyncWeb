package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AdmissionColumnBinding(val columnId: String, val womanDocumentId: String, val admissionId: String)

internal fun pendingAdmissionBindings(women: List<Woman>, feed: LabourCareFeed): List<AdmissionColumnBinding> {
    val womenByAdmission = women.filter { it.firestoreDocumentId.isNotBlank() }.associateBy { it.admissionId }
    val admissionsByColumn = feed.observations.associate { it.columnId to it.sessionId }
    return feed.columnControls.filter { it.womanDocumentId.isBlank() }.mapNotNull { control ->
        val admission = admissionsByColumn[control.columnId] ?: return@mapNotNull null
        val woman = womenByAdmission[admission] ?: return@mapNotNull null
        AdmissionColumnBinding(control.columnId, woman.firestoreDocumentId, admission)
    }.sortedBy { it.columnId }
}

/** Live snapshots may repeat or arrive during a write; only explicit retry repeats a failed attempt. */
internal class AdmissionBindingPreparation {
    private val mutex = Mutex()
    private val attempted = mutableSetOf<AdmissionColumnBinding>()
    private val mutableFailures = MutableStateFlow<Set<AdmissionColumnBinding>>(emptySet())
    val failures = mutableFailures.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()

    suspend fun prepare(
        bindings: List<AdmissionColumnBinding>,
        retry: Boolean = false,
        bind: suspend (AdmissionColumnBinding) -> Unit
    ) = mutex.withLock {
        mutableRunning.value = true
        try {
            for (binding in bindings) {
                if (!retry && binding in attempted) continue
                attempted += binding
                try {
                    bind(binding)
                    mutableFailures.value -= binding
                } catch (cancelled: CancellationException) {
                    attempted -= binding
                    throw cancelled
                } catch (_: Exception) {
                    mutableFailures.value += binding
                }
            }
        } finally {
            mutableRunning.value = false
        }
    }
}
