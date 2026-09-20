package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.ui.theme.SevenZipPurple
import com.example.ui.theme.SevenZipPurpleContainer
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.ZipOrange
import com.example.ui.theme.ZipOrangeContainer
import com.example.util.FileFormatter
import java.io.File

@Composable
fun ArchiveDetailsDialog(
    archiveItem: FileItem,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onBrowseContents: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onTestArchive: () -> Unit,
    onOpenWith: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    val isZip = archiveItem.isZip
    val iconBg = if (isZip) ZipOrangeContainer else SevenZipPurpleContainer
    val iconTint = if (isZip) ZipOrange else SevenZipPurple

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "Archive",
                        tint = iconTint,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = archiveItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = FileFormatter.formatFileSize(archiveItem.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar: Extract, Browse, Share, Delete, More
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ArchiveActionButton(
                        icon = Icons.Default.Unarchive,
                        label = "Extract",
                        tint = TechBluePrimary,
                        onClick = onExtract
                    )
                    ArchiveActionButton(
                        icon = Icons.Default.FolderOpen,
                        label = "Browse",
                        tint = TechBluePrimary,
                        onClick = onBrowseContents
                    )
                    ArchiveActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onShare
                    )
                    ArchiveActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDelete
                    )
                    Box {
                        ArchiveActionButton(
                            icon = Icons.Default.MoreHoriz,
                            label = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { showMoreMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showMoreMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Properties") },
                                onClick = {
                                    showMoreMenu = false
                                    onProperties()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Test Archive") },
                                onClick = {
                                    showMoreMenu = false
                                    onTestArchive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open With...") },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenWith()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Metadata Rows
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    ArchiveInfoRow("Type:", archiveItem.archiveType?.displayName ?: "Archive")
                    ArchiveInfoRow("Location:", File(archiveItem.path).parent ?: "Internal Storage")
                    ArchiveInfoRow(
                        "Added to Device:",
                        if (archiveItem.addedDate != null) FileFormatter.formatDateTime(archiveItem.addedDate) else "Added date unavailable"
                    )
                    ArchiveInfoRow(
                        "Modified:",
                        FileFormatter.formatDateTime(archiveItem.lastModified)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExtract,
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("EXTRACT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun ArchiveActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArchiveInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}
