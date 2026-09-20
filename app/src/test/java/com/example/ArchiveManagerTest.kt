package com.example

import com.example.model.CompressionLevel
import com.example.util.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCompressMultipleFilesAndExtract() = runBlocking {
        val workDir = tempFolder.newFolder("work")
        val file1 = File(workDir, "test1.txt").apply { writeText("Hello World 1") }
        val file2 = File(workDir, "test2.txt").apply { writeText("Hello World 2 with more content") }
        val subDir = File(workDir, "sub").apply { mkdir() }
        val file3 = File(subDir, "test3.txt").apply { writeText("Subfolder text file") }

        val destinationZip = File(tempFolder.root, "output.zip")

        // Compress multiple files
        val compressResult = ArchiveManager.compressFiles(
            sourceFiles = listOf(file1, file2, subDir),
            destinationZip = destinationZip,
            compressionLevel = CompressionLevel.NORMAL,
            keepOriginals = true,
            onProgress = { _, _, _ -> }
        )

        assertTrue("Compression should succeed", compressResult.isSuccess)
        assertTrue("Zip file should exist", destinationZip.exists())
        assertTrue("Zip file should have content", destinationZip.length() > 0)

        // Extract to target directory
        val extractTarget = tempFolder.newFolder("extracted")
        val extractResult = ArchiveManager.extractZip(
            zipFile = destinationZip,
            targetDir = extractTarget,
            selectedPaths = null,
            onProgress = { _, _, _ -> }
        )

        assertTrue("Extraction should succeed", extractResult.isSuccess)
        val extracted1 = File(extractTarget, "test1.txt")
        val extracted2 = File(extractTarget, "test2.txt")
        val extracted3 = File(extractTarget, "sub/test3.txt")

        assertTrue("Extracted file 1 exists", extracted1.exists())
        assertTrue("Extracted file 2 exists", extracted2.exists())
        assertTrue("Extracted file 3 in subfolder exists", extracted3.exists())

        assertEquals("Hello World 1", extracted1.readText())
        assertEquals("Hello World 2 with more content", extracted2.readText())
        assertEquals("Subfolder text file", extracted3.readText())
    }
}
