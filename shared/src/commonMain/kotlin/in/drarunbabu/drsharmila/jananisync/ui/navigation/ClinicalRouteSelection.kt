package `in`.drarunbabu.drsharmila.jananisync.ui.navigation

import `in`.drarunbabu.drsharmila.jananisync.data.RosterState
import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman

internal data class ClinicalRouteSelection(val ward: Ward?, val woman: Woman?)

internal fun SignedInRoute.resolveSelection(roster: RosterState): ClinicalRouteSelection {
    if (!isWellFormed() || roster.isLoading || roster.message != null) return ClinicalRouteSelection(null, null)
    val ward = roster.wards.firstOrNull { it.wardId == wardId }
        ?: return ClinicalRouteSelection(null, null)
    val woman = roster.women.firstOrNull {
        it.admissionId == admissionId && it.admittedWard.wardId == ward.wardId
    }
    return ClinicalRouteSelection(ward, woman)
}

/** Decide only after a complete, successfully decrypted roster has loaded. */
internal fun RosterState.needsInitialWardSetup(
    role: String, wardId: String?, adminConsole: Boolean
): Boolean = role == "admin" && !adminConsole && wardId == null &&
    !isLoading && (wardCount == 0 || (wardCount == null && message == null && wards.isEmpty()))
