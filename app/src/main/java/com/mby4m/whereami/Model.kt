package com.mby4m.whereami

/** A reverse-geocoded place. Every part is optional — geocoding is best-effort. */
data class Place(
    val street: String?,
    val locality: String?,
    val adminArea: String?,
    val country: String?,
) {
    /** "Yerevan, Yerevan, Armenia" — the line Areg actually wants to paste. */
    val adminLine: String?
        get() = listOfNotNull(locality, adminArea, country)
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
}

/** One location reading, plus whatever the geocoder could make of it. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy in metres; null on devices that don't report it. */
    val accuracyMeters: Float?,
    /** Wall-clock time the fix was taken, for the "as of" readout. */
    val takenAtMillis: Long,
    /** True when only ACCESS_COARSE_LOCATION was granted, so the fix is ~city-block. */
    val coarseOnly: Boolean,
    val place: Place?,
    /** Set while the geocoder is still working, so coordinates can show immediately. */
    val geocoding: Boolean = false,
) {
    val coordinates: String
        get() = "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)

    /** Everything, in the order that reads best when pasted into a chat. */
    fun copyAllText(): String = listOfNotNull(
        place?.street,
        place?.adminLine,
        coordinates,
    ).joinToString("\n")
}

/** What the single screen is showing right now. */
sealed interface UiState {
    /** Permission never requested, or requested and re-requestable. */
    data class NeedsPermission(val explainAgain: Boolean) : UiState

    /** Denied with "don't ask again" — only Settings can undo it. */
    data object PermissionBlocked : UiState

    /** Permission is fine but location services are off system-wide. */
    data object LocationServicesOff : UiState

    /** Waiting on a fix. */
    data object Locating : UiState

    /**
     * [refreshing] keeps the fix on screen while a newer one is being fetched,
     * rather than blanking back to the spinner every time the app is reopened.
     */
    data class Located(val fix: Fix, val refreshing: Boolean = false) : UiState

    /** Provider returned nothing, or threw. Always recoverable with Refresh. */
    data class Failed(val message: String) : UiState
}
