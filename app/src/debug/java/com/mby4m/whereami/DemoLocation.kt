package com.mby4m.whereami

/**
 * Debug builds show this fixed, made-up reading instead of the device's real
 * position, so Play Store screenshots can be taken without publishing where the
 * developer actually lives.
 *
 * The release source set returns null here, so none of this reaches Play.
 * Everything before this point — the permission flow, the location-services
 * check — still runs for real, so the screenshots show the genuine UI.
 */
object DemoLocation {
    fun fix(): Fix = Fix(
        latitude = 52.375100,
        longitude = 4.884400,
        accuracyMeters = 8f,
        takenAtMillis = System.currentTimeMillis(),
        coarseOnly = false,
        place = Place(
            street = "174 Keizersgracht",
            locality = "Amsterdam",
            adminArea = "North Holland",
            country = "Netherlands",
        ),
    )
}
