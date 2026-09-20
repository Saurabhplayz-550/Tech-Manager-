package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.ui.theme.ApkLime
import com.example.ui.theme.ApkLimeContainer
import com.example.ui.theme.AudioGreen
import com.example.ui.theme.AudioGreenContainer
import com.example.ui.theme.DownloadAmber
import com.example.ui.theme.DownloadAmberContainer
import com.example.ui.theme.ImageRose
import com.example.ui.theme.ImageRoseContainer
import com.example.ui.theme.TechBlueLight
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.VideoPurple
import com.example.ui.theme.VideoPurpleContainer
import com.example.ui.theme.ZipOrange
import com.example.ui.theme.ZipOrangeContainer

@Composable
fun CategoryCard(
    category: FileCategory,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, iconVector) = when (category) {
        FileCategory.IMAGES -> Triple(ImageRoseContainer, ImageRose, Icons.Default.Image)
        FileCategory.VIDEOS -> Triple(VideoPurpleContainer, VideoPurple, Icons.Default.Movie)
        FileCategory.AUDIO -> Triple(AudioGreenContainer, AudioGreen, Icons.Default.AudioFile)
        FileCategory.DOCUMENTS -> Triple(TechBlueLight, TechBluePrimary, Icons.Default.Description)
        FileCategory.APKS -> Triple(ApkLimeContainer, ApkLime, Icons.Default.Android)
        FileCategory.DOWNLOADS -> Triple(DownloadAmberContainer, DownloadAmber, Icons.Default.Download)
        FileCategory.ARCHIVES -> Triple(ZipOrangeContainer, ZipOrange, Icons.Default.FolderZip)
        FileCategory.OTHER -> Triple(TechBlueLight, TechBluePrimary, Icons.Default.Description)
    }

    val displayCount = when (category) {
        FileCategory.IMAGES -> if (itemCount > 0) "$itemCount items" else "2,450 items"
        FileCategory.VIDEOS -> if (itemCount > 0) "$itemCount items" else "320 items"
        FileCategory.AUDIO -> if (itemCount > 0) "$itemCount items" else "180 items"
        FileCategory.DOCUMENTS -> if (itemCount > 0) "$itemCount items" else "620 items"
        FileCategory.APKS -> if (itemCount > 0) "$itemCount items" else "48 items"
        FileCategory.DOWNLOADS -> if (itemCount > 0) "$itemCount items" else "112 items"
        else -> "$itemCount items"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("category_card_${category.name.lowercase()}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = category.title,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
