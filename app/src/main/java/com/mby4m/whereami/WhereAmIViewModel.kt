package com.mby4m.whereami

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the one screen's state. Survives configuration changes so a fix isn't
 * thrown away on rotation.
 */
class WhereAmIViewModel(application: Application) : AndroidViewModel(application) {

    private val source = LocationSource(application)
    private val prefs =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var state by mutableStateOf<UiState>(UiState.NeedsPermission(explainAgain = false))
        private set

    private var inFlight: Job? = null

    /**
     * True once the runtime permission dialog has been shown at least once.
     * Needed to tell "never asked" apart from "denied with don't-ask-again",
     * which the framework does not distinguish on its own.
     */
    private var hasRequestedPermission: Boolean
        get() = prefs.getBoolean(KEY_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_REQUESTED, value).apply()

    fun onPermissionRequested() {
        hasRequestedPermission = true
    }

    /**
     * Re-derives the state from the live permission and location-services status,
     * fetching a fix when everything is in order.
     *
     * @param shouldShowRationale what the Activity reports for the location
     *   permissions right now — false *after* a request means the user chose
     *   "don't ask again", which only Settings can reverse.
     */
    fun refresh(shouldShowRationale: Boolean) {
        inFlight?.cancel()

        if (!source.hasAnyLocationPermission()) {
            state = if (hasRequestedPermission && !shouldShowRationale) {
                UiState.PermissionBlocked
            } else {
                UiState.NeedsPermission(explainAgain = hasRequestedPermission)
            }
            return
        }
        if (!source.locationServicesEnabled()) {
            state = UiState.LocationServicesOff
            return
        }

        // Keep any fix already on screen visible while the new one lands; only
        // fall back to the full-screen spinner when there is nothing to show.
        val previous = (state as? UiState.Located)?.fix
        state = if (previous != null) UiState.Located(previous, refreshing = true) else UiState.Locating

        inFlight = viewModelScope.launch {
            // Debug builds short-circuit to a fixed made-up reading for store
            // screenshots. In release this returns null and R8 removes the branch.
            DemoLocation.fix()?.let {
                state = UiState.Located(it)
                return@launch
            }

            val location = source.currentLocation()
            if (location == null) {
                // A failed refresh must not destroy a good fix — the stale
                // timestamp is a truer signal than an error screen here.
                state = if (previous != null) {
                    UiState.Located(previous, refreshing = false)
                } else {
                    UiState.Failed(
                        "Couldn't get a fix. Try again somewhere with a clearer view of the sky."
                    )
                }
                return@launch
            }

            // Show coordinates the moment we have them; the address can lag or never arrive.
            val fix = Fix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                takenAtMillis = System.currentTimeMillis(),
                coarseOnly = !source.hasFineLocation(),
                place = null,
                geocoding = true,
            )
            state = UiState.Located(fix)

            val place = source.reverseGeocode(location.latitude, location.longitude)
            // Only apply if nothing else has changed the state in the meantime.
            val current = state
            if (current is UiState.Located && current.fix.takenAtMillis == fix.takenAtMillis) {
                state = UiState.Located(fix.copy(place = place, geocoding = false))
            }
        }
    }

    /**
     * Called when the screen comes to the foreground, including the first time.
     * Always re-fetches: a location app showing where you were an hour ago is
     * worse than useless. The guard is only against re-entrancy — the resume
     * that follows onCreate, and the one that follows the permission dialog,
     * would otherwise cancel and restart a fetch that had only just begun.
     */
    fun onResume(shouldShowRationale: Boolean) {
        if (state is UiState.Locating) return
        if ((state as? UiState.Located)?.refreshing == true) return
        refresh(shouldShowRationale)
    }

    private companion object {
        const val PREFS_NAME = "whereami"
        const val KEY_REQUESTED = "permission_requested"
    }
}
