package com.example.mcp

import android.content.Context
import android.os.Environment
import android.util.Base64
import com.example.model.CompressionLevel
import com.example.util.ArchiveManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object McpTools {
    private const val MAX_DOWNLOAD_SIZE_BYTES = 20 * 1024 * 1024L // 20 MB

    /**
     * Validates that the path is strictly within permitted user/app storage roots.
     * Prevents directory traversal attacks (e.g. /etc, /system, /data/data/other).
     */
    fun isPathAllowed(path: String, context: Context): Boolean {
        return try {
            val targetFile = File(path).canonicalFile
            val allowedRoots = mutableListOf<File>()

            // Primary external storage (/storage/emulated/0)
            Environment.getExternalStorageDirectory()?.let { allowedRoots.add(it.canonicalFile) }

            // Secondary storage roots (e.g. MicroSD cards)
            File("/storage").listFiles()?.forEach { sd ->
                if (sd.isDirectory && sd.name != "self" && sd.name != "emulated") {
                    allowedRoots.add(sd.canonicalFile)
                }
            }

            // App-specific storage
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { allowedRoots.add(it.canonicalFile) }
            allowedRoots.add(context.filesDir.canonicalFile)

            allowedRoots.any { root ->
                targetFile.canonicalPath == root.canonicalPath || targetFile.canonicalPath.startsWith(root.canonicalPath + File.separator)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns list of tools for MCP "tools/list" request.
     */
    fun getToolsListJson(): JSONArray {
        val toolsArray = JSONArray()

        // 1. list_files
        toolsArray.put(JSONObject().apply {
            put("name", "list_files")
            put("description", "Lists files and directories at the specified path on the phone storage.")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Directory path on device, e.g. '/storage/emulated/0' or '/storage/emulated/0/Download'")
                    })
                })
                put("required", JSONArray().apply { put("path") })
            })
        })

        // 2. create_zip
        toolsArray.put(JSONObject().apply {
            put("name", "create_zip")
            put("description", "Compresses specified source files and folders into a ZIP archive.")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("sourcePaths", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply { put("type", "string") })
                        put("description", "List of absolute paths to files or folders to compress")
                    })
                    put("zipName", JSONObject().apply {
                        put("type", "string")
                        put("description", "Name of the zip file to create, e.g. 'archive.zip'")
                    })
                    put("destinationPath", JSONObject().apply {
                        put("type", "string")
                        put("description", "Directory where the archive will be saved, e.g. '/storage/emulated/0/Download'")
                    })
                })
                put("required", JSONArray().apply {
                    put("sourcePaths")
                    put("zipName")
                    put("destinationPath")
                })
            })
        })

        // 3. extract_zip
        toolsArray.put(JSONObject().apply {
            put("name", "extract_zip")
            put("description", "Extracts the contents of a ZIP archive into a destination directory.")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("zipPath", JSONObject().apply {
                        put("type", "string")
                        put("description", "Absolute path to the .zip archive file")
                    })
                    put("destinationPath", JSONObject().apply {
                        put("type", "string")
                        put("description", "Directory path where contents will be extracted")
                    })
                })
                put("required", JSONArray().apply {
                    put("zipPath")
                    put("destinationPath")
                })
            })
        })

        // 4. download_file
        toolsArray.put(JSONObject().apply {
            put("name", "download_file")
            put("description", "Reads a file from the device and returns its content encoded as Base64 (max 20MB).")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Absolute path to the file to download")
                    })
                })
                put("required", JSONArray().apply { put("path") })
            })
        })

        return toolsArray
    }

    /**
     * Executes the requested tool with the given arguments.
     * Returns Pair<isError: Boolean, contentText: String>.
     */
    suspend fun executeTool(
        context: Context,
        toolName: String,
        arguments: JSONObject
    ): Pair<Boolean, String> {
        return try {
            when (toolName) {
                "list_files" -> executeListFiles(context, arguments)
                "create_zip" -> executeCreateZip(context, arguments)
                "extract_zip" -> executeExtractZip(context, arguments)
                "download_file" -> executeDownloadFile(context, arguments)
                else -> Pair(true, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Pair(true, "Tool execution failed: ${e.message}")
        }
    }

    private fun executeListFiles(context: Context, args: JSONObject): Pair<Boolean, String> {
        val path = args.optString("path", "").trim()
        if (path.isEmpty()) {
            return Pair(true, "Missing required argument 'path'")
        }

        if (!isPathAllowed(path, context)) {
            return Pair(true, "Access denied: Path '$path' is outside permitted storage directories.")
        }

        val targetDir = File(path)
        if (!targetDir.exists()) {
            return Pair(true, "Directory does not exist: $path")
        }
        if (!targetDir.isDirectory) {
            return Pair(true, "Path is not a directory: $path")
        }

        val items = targetDir.listFiles() ?: emptyArray()
        val resultList = JSONArray()

        // Sort folders first, then files alphabetically
        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { file ->
            val fileObj = JSONObject().apply {
                put("name", file.name)
                put("path", file.absolutePath)
                put("isDirectory", file.isDirectory)
                put("sizeBytes", if (file.isDirectory) 0L else file.length())
                put("lastModified", file.lastModified())
                put("canRead", file.canRead())
                put("canWrite", file.canWrite())
            }
            resultList.put(fileObj)
        }

        val response = JSONObject().apply {
            put("path", targetDir.canonicalPath)
            put("itemCount", items.size)
            put("items", resultList)
        }

        return Pair(false, response.toString(2))
    }

    private suspend fun executeCreateZip(context: Context, args: JSONObject): Pair<Boolean, String> {
        val destPath = args.optString("destinationPath", "").trim()
        var zipName = args.optString("zipName", "").trim()
        val sourceArray = args.optJSONArray("sourcePaths")

        if (destPath.isEmpty() || zipName.isEmpty() || sourceArray == null || sourceArray.length() == 0) {
            return Pair(true, "Missing required arguments ('destinationPath', 'zipName', 'sourcePaths')")
        }

        if (!zipName.endsWith(".zip", ignoreCase = true)) {
            zipName = "$zipName.zip"
        }

        if (!isPathAllowed(destPath, context)) {
            return Pair(true, "Access denied: Destination path '$destPath' is outside permitted storage.")
        }

        val destDir = File(destPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val destZipFile = File(destDir, zipName)

        val sourceFiles = mutableListOf<File>()
        for (i in 0 until sourceArray.length()) {
            val srcPath = sourceArray.getString(i)
            if (!isPathAllowed(srcPath, context)) {
                return Pair(true, "Access denied: Source path '$srcPath' is outside permitted storage.")
            }
            val srcFile = File(srcPath)
            if (!srcFile.exists()) {
                return Pair(true, "Source file does not exist: $srcPath")
            }
            sourceFiles.add(srcFile)
        }

        val result = ArchiveManager.compressFiles(
            sourceFiles = sourceFiles,
            destinationZip = destZipFile,
            compressionLevel = CompressionLevel.NORMAL,
            keepOriginals = true,
            onProgress = { _: Int, _: Int, _: String -> }
        )

        return if (result.isSuccess) {
            val created = result.getOrNull() ?: destZipFile
            val response = JSONObject().apply {
                put("status", "success")
                put("message", "Archive created successfully")
                put("zipPath", created.absolutePath)
                put("sizeBytes", created.length())
                put("sourceFileCount", sourceFiles.size)
            }
            Pair(false, response.toString(2))
        } else {
            Pair(true, "Zip creation failed: ${result.exceptionOrNull()?.message}")
        }
    }

    private suspend fun executeExtractZip(context: Context, args: JSONObject): Pair<Boolean, String> {
        val zipPath = args.optString("zipPath", "").trim()
        val destPath = args.optString("destinationPath", "").trim()

        if (zipPath.isEmpty() || destPath.isEmpty()) {
            return Pair(true, "Missing required arguments ('zipPath', 'destinationPath')")
        }

        if (!isPathAllowed(zipPath, context)) {
            return Pair(true, "Access denied: Zip path '$zipPath' is outside permitted storage.")
        }
        if (!isPathAllowed(destPath, context)) {
            return Pair(true, "Access denied: Destination path '$destPath' is outside permitted storage.")
        }

        val zipFile = File(zipPath)
        if (!zipFile.exists() || !zipFile.isFile) {
            return Pair(true, "ZIP file does not exist or is invalid: $zipPath")
        }

        val destDir = File(destPath)
        destDir.mkdirs()

        val result = ArchiveManager.extractZip(
            zipFile = zipFile,
            targetDir = destDir,
            selectedPaths = null,
            onProgress = { _: Int, _: Int, _: String -> }
        )

        return if (result.isSuccess) {
            val extractedCount = result.getOrDefault(0)
            val response = JSONObject().apply {
                put("status", "success")
                put("message", "Archive extracted successfully")
                put("extractedFilesCount", extractedCount)
                put("destinationPath", destDir.absolutePath)
            }
            Pair(false, response.toString(2))
        } else {
            Pair(true, "Extraction failed: ${result.exceptionOrNull()?.message}")
        }
    }

    private fun executeDownloadFile(context: Context, args: JSONObject): Pair<Boolean, String> {
        val path = args.optString("path", "").trim()
        if (path.isEmpty()) {
            return Pair(true, "Missing required argument 'path'")
        }

        if (!isPathAllowed(path, context)) {
            return Pair(true, "Access denied: Path '$path' is outside permitted storage.")
        }

        val file = File(path)
        if (!file.exists()) {
            return Pair(true, "File does not exist: $path")
        }
        if (!file.isFile) {
            return Pair(true, "Requested path is a directory, not a file: $path")
        }

        val fileSize = file.length()
        if (fileSize > MAX_DOWNLOAD_SIZE_BYTES) {
            val sizeMb = String.format("%.2f", fileSize / (1024.0 * 1024.0))
            return Pair(true, "File size ($sizeMb MB) exceeds the safe download limit of 20MB.")
        }

        val bytes = file.readBytes()
        val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val response = JSONObject().apply {
            put("fileName", file.name)
            put("path", file.absolutePath)
            put("sizeBytes", fileSize)
            put("contentBase64", base64Content)
        }

        return Pair(false, response.toString())
    }
}
