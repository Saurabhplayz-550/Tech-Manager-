package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ArchiveType
import com.example.model.FileItem
import com.example.ui.theme.ApkLime
import com.example.ui.theme.ApkLimeContainer
import com.example.ui.theme.AudioGreen
import com.example.ui.theme.AudioGreenContainer
import com.example.ui.theme.DownloadAmber
import com.example.ui.theme.DownloadAmberContainer
import com.example.ui.theme.FolderBlue
import com.example.ui.theme.FolderBlueContainer
import com.example.ui.theme.ImageRose
import com.example.ui.theme.ImageRoseContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.SevenZipPurple
import com.example.ui.theme.SevenZipPurpleContainer
import com.example.ui.theme.TechBlueLight
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.VideoPurple
import com.example.ui.theme.VideoPurpleContainer
import com.example.ui.theme.ZipOrange
import com.example.ui.theme.ZipOrangeContainer

@Composable
fun FileIcon(
    item: FileItem,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor) = when {
        item.isDirectory -> Pair(FolderBlueContainer, FolderBlue)
        item.isZip -> Pair(ZipOrangeContainer, ZipOrange)
        item.is7z -> Pair(SevenZipPurpleContainer, SevenZipPurple)
        item.isArchive -> Pair(ZipOrangeContainer, ZipOrange)
        item.isPdf -> Pair(PdfRedContainer, PdfRed)
        item.isApk -> Pair(ApkLimeContainer, ApkLime)
        item.isImage -> Pair(ImageRoseContainer, ImageRose)
        item.isVideo -> Pair(VideoPurpleContainer, VideoPurple)
        item.isAudio -> Pair(AudioGreenContainer, AudioGreen)
        item.isDocument -> Pair(TechBlueLight, TechBluePrimary)
        else -> Pair(TechBlueLight, TechBluePrimary)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            item.isDirectory -> {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.58f)
                )
            }
            item.isZip -> {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "ZIP Archive",
                        tint = iconColor,
                        modifier = Modifier.size(size * 0.65f)
                    )
                }
            }
            item.is7z -> {
                Text(
                    text = "7z",
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp
                )
            }
            item.isArchive -> {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = "Archive",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            item.isPdf -> {
                Text(
                    text = "PDF",
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp
                )
            }
            item.isApk -> {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "APK",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            item.isImage -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Image",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            item.isVideo -> {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Video",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            item.isAudio -> {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            item.isDocument -> {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Document",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}
