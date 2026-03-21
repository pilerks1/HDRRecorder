package com.pilerks1.hdrrecorder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLDecoder

@Composable
fun StorageSection(
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

    Text(
        text = "Storage",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = "Storage Location",
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = displayPath,
                fontSize = 12.sp,
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
 * Converts a raw Document Tree URI into a readable path for the user.
 * (e.g. content://.../tree/primary%3AMovies -> primary:Movies)
 */
private fun getReadablePath(uriString: String?): String {
    if (uriString.isNullOrEmpty()) return "Default (Movies/HDRRecorder)"
    return try {
        val uri = Uri.parse(uriString)
        val path = uri.lastPathSegment ?: uri.toString()
        URLDecoder.decode(path, "UTF-8")
    } catch (e: Exception) {
        "Custom Directory"
    }
}