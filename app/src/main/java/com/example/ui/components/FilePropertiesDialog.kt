package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileItem
import com.example.ui.theme.TechBluePrimary
import com.example.util.FileFormatter
import java.io.File

@Composable
fun FilePropertiesDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val file = File(item.path)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIcon(item = item, size = 36.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                PropertyField("Type:", if (item.isDirectory) "Folder" else item.archiveType?.displayName ?: "${item.extension.uppercase()} File")
                PropertyField("Size:", if (item.isDirectory) "${FileFormatter.formatFileSize(item.size)} (${item.itemCount} items)" else FileFormatter.formatFileSize(item.size))

                // Arrival & timestamps
                PropertyField(
                    "Added to Device:",
                    if (item.addedDate != null) FileFormatter.formatDateTime(item.addedDate) else "Added date unavailable"
                )
                PropertyField(
                    "Modified:",
                    FileFormatter.formatDateTime(item.lastModified)
                )
                if (item.createdDate != null) {
                    PropertyField(
                        "Created:",
                        FileFormatter.formatDateTime(item.createdDate)
                    )
                }

                PropertyField("Location:", file.parent ?: "Internal Storage")
                PropertyField("Full Path:", item.path)

                if (item.isDirectory) {
                    val subFiles = file.listFiles()
                    val filesCount = subFiles?.count { it.isFile } ?: 0
                    val foldersCount = subFiles?.count { it.isDirectory } ?: 0
                    PropertyField("Contains:", "$filesCount files, $foldersCount folders")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("File Path", item.path)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("copy_path_button")
            ) {
                Text("COPY PATH")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare) {
                    Text("SHARE")
                }
                TextButton(onClick = onDismiss) {
                    Text("CLOSE")
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PropertyField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
