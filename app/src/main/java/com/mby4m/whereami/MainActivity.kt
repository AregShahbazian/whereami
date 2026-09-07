package com.mby4m.whereami

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {

    private val viewModel: WhereAmIViewModel by viewModels()

    /**
     * Both permissions at once. On Android 12+ the user may grant coarse only —
     * that is a supported outcome, not a failure, so the result is handled the
     * same way either way: re-derive the state and carry on.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refresh(shouldShowRationale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhereAmITheme {
                WhereAmIScreen(
                    state = viewModel.state,
                    onGrantPermission = ::requestLocationPermission,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenLocationSettings = ::openLocationSettings,
                    onRefresh = { viewModel.refresh(shouldShowRationale()) },
                    onCopy = ::copyToClipboard,
                )
            }
        }
        // No refresh here: onResume runs immediately after onCreate and does it,
        // and it also picks up a permission or location toggle changed in Settings
        // while we were away.
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume(shouldShowRationale())
    }

    private fun requestLocationPermission() {
        viewModel.onPermissionRequested()
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    /** False after a request means the user picked "don't ask again". */
    private fun shouldShowRationale(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own copy confirmation; a Toast on top would double it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }
}
