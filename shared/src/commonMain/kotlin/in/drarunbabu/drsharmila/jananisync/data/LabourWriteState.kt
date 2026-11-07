package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps successful local writes authoritative while independent listeners catch up. */
internal class LabourWriteState {
    enum class Source { Observations, Columns, Corrections }

    private val mutationMutex = Mutex()
    private val receivedSources = mutableSetOf<Source>()
    private val sourceErrors = linkedMapOf<Source, String>()
    private val listenerMessages = mutableSetOf<String>()
    private val acknowledgedControls = mutableMapOf<String, LabourColumnControl>()
    private val acknowledgedObservations = mutableMapOf<String, LabourCareObservation>()
    private var observing = false

    var generation: Int = 0
        private set

    val ready: Boolean
        get() = observing && Source.Observations in receivedSources && Source.Columns in receivedSources &&
            Source.Observations !in sourceErrors && Source.Columns !in sourceErrors

    fun reset(observing: Boolean) {
        generation++
        this.observing = observing
        receivedSources.clear()
        sourceErrors.clear()
        listenerMessages.clear()
        acknowledgedControls.clear()
        acknowledgedObservations.clear()
    }

    fun received(source: Source) {
        receivedSources += source
        sourceErrors.remove(source)
    }

    fun failed(source: Source, message: String) {
        sourceErrors[source] = message
        listenerMessages += message
    }

    fun retainedMessage(message: String?): String? = message?.takeUnless { it in listenerMessages }

    fun requireCurrent(token: Int) {
        require(token == generation) { "Your session changed. Reopen the admission before editing." }
    }

    suspend fun <T> serialize(action: suspend (Int) -> T): T {
        val token = generation
        return mutationMutex.withLock {
            requireCurrent(token)
            action(token)
        }
    }

    fun acknowledge(token: Int, control: LabourColumnControl, observation: LabourCareObservation) {
        requireCurrent(token)
        require(control.columnId == observation.columnId && control.revision == observation.revision)
        val previous = acknowledgedControls[control.columnId]
        if (previous == null || control.revision >= previous.revision) {
            acknowledgedControls[control.columnId] = control
            acknowledgedObservations[control.columnId] = observation
        }
    }

    fun overlay(feed: LabourCareFeed, includeAcknowledgements: Boolean = true): LabourCareFeed {
        val controls = feed.columnControls.associateBy { it.columnId }.toMutableMap()
        if (includeAcknowledgements) acknowledgedControls.forEach { (id, acknowledged) ->
            val loaded = controls[id]
            controls[id] = when {
                loaded == null || loaded.revision < acknowledged.revision -> acknowledged
                loaded.confirmed && acknowledged.confirmed && loaded.confirmerAccessLevel == 0 &&
                    loaded.confirmedBy == acknowledged.confirmedBy ->
                    loaded.copy(confirmerAccessLevel = acknowledged.confirmerAccessLevel)
                else -> loaded
            }
        }
        val observations = feed.observations.filter { it.columnId.isNotBlank() }
            .associateBy { it.columnId }.toMutableMap()
        if (includeAcknowledgements) acknowledgedObservations.forEach { (id, acknowledged) ->
            val loaded = observations[id]
            if (loaded == null || loaded.revision < acknowledged.revision) observations[id] = acknowledged
        }
        return feed.copy(
            observations = (feed.observations.filter { it.columnId.isBlank() } + observations.values).sortedBy { it.timestamp },
            columnControls = controls.values.toList(),
            isLoading = if (observing) !ready else feed.isLoading,
            message = sourceErrors.values.firstOrNull() ?: feed.message
        )
    }
}
