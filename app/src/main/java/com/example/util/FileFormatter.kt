package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileFormatter {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return if (index == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    fun formatDateTime(timestamp: Long, use24Hour: Boolean = false): String {
        if (timestamp <= 0) return "Unavailable"
        val pattern = if (use24Hour) "d MMM yyyy, HH:mm" else "d MMM yyyy, h:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        if (timestamp <= 0) return "Unavailable"
        val sdf = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Unavailable"
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 0) return formatDateTime(timestamp)

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> formatDateOnly(timestamp)
        }
    }

    fun formatAddedLabel(timestamp: Long?): String {
        return if (timestamp != null && timestamp > 0) {
            "Added " + formatRelativeTime(timestamp)
        } else {
            "Added date unavailable"
        }
    }

    fun formatModifiedLabel(timestamp: Long): String {
        return "Modified " + formatRelativeTime(timestamp)
    }
}
