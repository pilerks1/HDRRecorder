package com.pilerks1.hdrrecorder.ui.camera.settings

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles
import kotlinx.coroutines.launch

@Composable
fun StorageSettingsSection(
    storageUri: String?,
    onStorageUriSelected: (String) -> Unit
) {
    // Activity Result Launcher to open the Android Directory Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onStorageUriSelected(it.toString()) }
    }

    val displayPath = getReadablePath(storageUri)

    SectionTitle("Storage")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = SettingsDimensions.sectionSpacing)) {
            Text(
                text = "Storage Location",
                fontSize = UiTextStyles.Settings.rowTitleSize,
                color = Color.White
            )
            // Displays the full folder path nicely below the main text
            Text(
                text = displayPath,
                fontSize = UiTextStyles.Settings.descriptionSize,
                lineHeight = UiTextStyles.Settings.descriptionLineHeight,
                color = Color.Gray,
                maxLines = 2
            )
        }
        Button(
            onClick = { launcher.launch(null) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(text = "Select")
        }
    }
}

/**
 * Shows the tree's document ID, which the provider already returns decoded
 * (e.g. content://.../tree/primary%3AMovies -> primary:Movies).
 */
private fun getReadablePath(uriString: String?): String {
    if (uriString.isNullOrEmpty()) return "Default (DCIM/HDR-Recorder)"
    return try {
        DocumentsContract.getTreeDocumentId(uriString.toUri())
    } catch (e: IllegalArgumentException) {
        "Custom Directory"
    }
}
