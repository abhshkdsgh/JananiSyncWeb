package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman

internal enum class WomanLabourStatus(val label: String) {
    YetToStart("Labour yet to start"), InLabour("In labour"), SecondStage("Second stage"),
    BirthGiven("Birth given"), Unknown("Status unavailable")
}

internal enum class WomanListFilter(val label: String, val status: WomanLabourStatus? = null) {
    All("All women"), YetToStart("Labour yet to start", WomanLabourStatus.YetToStart),
    InLabour("In labour", WomanLabourStatus.InLabour), SecondStage("Second stage", WomanLabourStatus.SecondStage),
    BirthGiven("Birth given", WomanLabourStatus.BirthGiven);

    fun matches(status: WomanLabourStatus): Boolean = when (this) {
        All -> true
        InLabour -> status == WomanLabourStatus.InLabour || status == WomanLabourStatus.SecondStage
        else -> status == this.status
    }
}

/** Choose from the whole ward, independently of any later search or manual filter selection. */
internal fun defaultWomanListFilter(statuses: Collection<WomanLabourStatus>): WomanListFilter = when {
    statuses.any(WomanListFilter.InLabour::matches) -> WomanListFilter.InLabour
    WomanLabourStatus.YetToStart in statuses -> WomanListFilter.YetToStart
    else -> WomanListFilter.All
}

internal fun admissionLabourStatus(
    admissionId: String, observations: List<LabourCareObservation>, available: Boolean
): WomanLabourStatus {
    if (!available || admissionId.isBlank()) return WomanLabourStatus.Unknown
    val records = observations.filter { it.sessionId == admissionId && it.cellKey != "skipped" }
    return when {
        records.any { it.birthConfirmed } -> WomanLabourStatus.BirthGiven
        records.any { it.fullDilationConfirmed } -> WomanLabourStatus.SecondStage
        records.any { it.labourOnset.isNotBlank() || it.activeLabourDiagnosis.isNotBlank() ||
            (!it.isGuideHeader && (it.laborProgress.cervicalDilationCm > 0 || it.pushingStarted)) } -> WomanLabourStatus.InLabour
        else -> WomanLabourStatus.YetToStart
    }
}

internal fun Woman.matchesWomanSearch(query: String): Boolean {
    val text = query.trim().replace(Regex("\\s+"), " ")
    if (text.isEmpty()) return true
    if (name.replace(Regex("\\s+"), " ").contains(text, ignoreCase = true) ||
        cRNumber.toString().contains(text) || iPNumber.toString().contains(text)) return true
    val digits = text.filter(Char::isDigit)
    return digits.isNotEmpty() && text.all { it.isDigit() || it in "+()- ." } &&
        listOf(contactNumber, alternateContactNumber).any { it.filter(Char::isDigit).contains(digits) }
}
