package com.mby4m.whereami

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

private val WhereAmIColors = darkColorScheme(
    primary = Color(0xFF7FD1FF),
    onPrimary = Color(0xFF002F42),
    background = Color(0xFF0E1720),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF16222E),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2A38),
    onSurfaceVariant = Color(0xFF9AAEC1),
    error = Color(0xFFFFB4A9),
)

@Composable
fun WhereAmITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WhereAmIColors, content = content)
}

@Composable
fun WhereAmIScreen(
    state: UiState,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // targetSdk 36 forces edge-to-edge, so without this the heading
                // draws underneath the status bar clock.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Where Am I",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            when (state) {
                is UiState.NeedsPermission -> Explainer(
                    title = "Location permission needed",
                    body = if (state.explainAgain) {
                        "Where Am I can't tell you anything without it. The location " +
                            "is read on demand, shown on this screen, and never leaves your phone."
                    } else {
                        "Where Am I reads your location on demand, shows it on this " +
                            "screen, and never sends it anywhere."
                    },
                    actionLabel = "Allow location",
                    onAction = onGrantPermission,
                )

                UiState.PermissionBlocked -> Explainer(
                    title = "Permission blocked",
                    body = "Location is set to \"Don't allow\" for this app. Turn it on " +
                        "in Settings and come back.",
                    actionLabel = "Open settings",
                    onAction = onOpenAppSettings,
                )

                UiState.LocationServicesOff -> Explainer(
                    title = "Location is off",
                    body = "Your phone's location services are switched off, so there's " +
                        "nothing to read.",
                    actionLabel = "Turn on location",
                    onAction = onOpenLocationSettings,
                )

                UiState.Locating -> Waiting()

                is UiState.Failed -> Explainer(
                    title = "No fix",
                    body = state.message,
                    actionLabel = "Try again",
                    onAction = onRefresh,
                )

                is UiState.Located -> Located(
                    fix = state.fix,
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
private fun Located(
    fix: Fix,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val place = fix.place

    // Ranked deliberately: the city/province/country line is what Areg actually
    // wants to paste, so it leads. The street address is the least reliable part
    // of a geocode and sits last.
    val adminLine = place?.adminLine
    if (adminLine != null) {
        FieldCard(
            label = "City / Province / Country",
            value = adminLine,
            primary = true,
            onCopy = { onCopy("Location", adminLine) },
        )
    } else if (fix.geocoding) {
        PendingCard("City / Province / Country")
    } else {
        UnavailableCard(
            label = "City / Province / Country",
            note = "No address available for this spot.",
        )
    }

    Spacer(Modifier.height(12.dp))

    FieldCard(
        label = "Coordinates",
        value = fix.coordinates,
        monospace = true,
        onCopy = { onCopy("Coordinates", fix.coordinates) },
    )

    Spacer(Modifier.height(12.dp))

    val street = place?.street
    if (street != null) {
        FieldCard(
            label = "Address (approximate)",
            value = street,
            onCopy = { onCopy("Address", street) },
        )
    } else if (fix.geocoding) {
        PendingCard("Address (approximate)")
    }

    Spacer(Modifier.height(20.dp))

    Text(
        text = if (refreshing) {
            "${fix.detailLine(LocalContext.current)} · updating…"
        } else {
            fix.detailLine(LocalContext.current)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onCopy("Location", fix.copyAllText()) },
            modifier = Modifier.weight(1f),
        ) {
            Text("Copy all")
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.weight(1f),
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (refreshing) "Updating" else "Refresh")
        }
    }
}

/** The "±8 m · approximate · 14:32" line under the fields. */
private fun Fix.detailLine(context: Context): String {
    val parts = mutableListOf<String>()
    accuracyMeters?.let { parts += "Accurate to ±${it.toInt()} m" }
    if (coarseOnly) parts += "approximate location only"
    parts += DateFormat.getTimeFormat(context).format(Date(takenAtMillis))
    return parts.joinToString(" · ")
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    primary: Boolean = false,
    monospace: Boolean = false,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = if (primary) 26.sp else 18.sp,
                lineHeight = if (primary) 32.sp else 24.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCopy) { Text("Copy") }
            }
        }
    }
}

@Composable
private fun PendingCard(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Looking up ${label.lowercase()}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnavailableCard(label: String, note: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Waiting() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Getting a fix…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Explainer(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
