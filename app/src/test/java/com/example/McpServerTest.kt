package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mcp.McpSecurityManager
import com.example.mcp.McpServer
import com.example.mcp.McpTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class McpServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMcpToolsDefinitionList() {
        val toolsArray = McpTools.getToolsListJson()
        assertEquals(4, toolsArray.length())

        val toolNames = mutableListOf<String>()
        for (i in 0 until toolsArray.length()) {
            val tool = toolsArray.getJSONObject(i)
            toolNames.add(tool.getString("name"))
            assertTrue(tool.has("description"))
            assertTrue(tool.has("inputSchema"))
        }

        assertTrue("Contains list_files", toolNames.contains("list_files"))
        assertTrue("Contains create_zip", toolNames.contains("create_zip"))
        assertTrue("Contains extract_zip", toolNames.contains("extract_zip"))
        assertTrue("Contains download_file", toolNames.contains("download_file"))
    }

    @Test
    fun testPathAllowedValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val internalFile = File(context.filesDir, "test.txt").apply { writeText("internal") }
        assertTrue("App internal files dir should be allowed", McpTools.isPathAllowed(internalFile.absolutePath, context))

        val outsidePath = "/system/etc/hosts"
        assertFalse("System path must not be allowed", McpTools.isPathAllowed(outsidePath, context))
    }

    @Test
    fun testMcpSecurityManagerToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = McpSecurityManager.getOrCreateAuthToken(context)
        assertTrue("Token should start with mcp_", token.startsWith("mcp_"))
        assertTrue("Token should be sufficiently long", token.length >= 20)

        val retrieved = McpSecurityManager.getOrCreateAuthToken(context)
        assertEquals("Token should remain stable across calls", token, retrieved)

        McpSecurityManager.saveRelayHost(context, "mcp.example.deno.dev")
        assertEquals("mcp.example.deno.dev", McpSecurityManager.getRelayHost(context))

        McpSecurityManager.saveRelaySecret(context, "super_secret_123")
        assertEquals("super_secret_123", McpSecurityManager.getRelaySecret(context))
    }

    @Test
    fun testSharedHandleMcpRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authToken = "mcp_shared_test_token"
        val server = McpServer(
            context = context,
            port = 8899,
            authTokenProvider = { authToken }
        )

        // 1. Rejected with missing or invalid token
        val (unauthCode, _) = server.handleMcpRequest(emptyMap(), "{}")
        assertEquals(401, unauthCode)

        val (wrongAuthCode, _) = server.handleMcpRequest(mapOf("X-Auth-Token" to "wrong_token"), "{}")
        assertEquals(401, wrongAuthCode)

        // 2. Success with valid token (initialize)
        val initRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
        }.toString()

        val (authCode, authResponse) = server.handleMcpRequest(mapOf("X-Auth-Token" to authToken), initRequest)
        assertEquals(200, authCode)
        val json = JSONObject(authResponse)
        assertEquals("2.0", json.getString("jsonrpc"))
        assertTrue(json.has("result"))
        assertEquals("2024-11-05", json.getJSONObject("result").getString("protocolVersion"))

        // 3. Success with tools/list
        val listRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/list")
        }.toString()

        val (listCode, listResponse) = server.handleMcpRequest(mapOf("Authorization" to "Bearer $authToken"), listRequest)
        assertEquals(200, listCode)
        val listJson = JSONObject(listResponse)
        val tools = listJson.getJSONObject("result").getJSONArray("tools")
        assertEquals(4, tools.length())
    }

    @Test
    fun testToolExecutionListFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.filesDir, "test_dir").apply { mkdirs() }
        File(testDir, "sample1.txt").writeText("content 1")
        File(testDir, "sample2.txt").writeText("content 2")

        val args = JSONObject().apply { put("path", testDir.absolutePath) }
        val (isError, resultJsonStr) = McpTools.executeTool(context, "list_files", args)

        assertFalse("Tool execution should not error", isError)
        val result = JSONObject(resultJsonStr)
        assertEquals(2, result.getInt("itemCount"))
        val items = result.getJSONArray("items")
        assertEquals(2, items.length())
    }

    @Test
    fun testToolExecutionZipAndExtract() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.filesDir, "zip_work").apply { mkdirs() }
        val file1 = File(workDir, "hello.txt").apply { writeText("Hello MCP") }

        // Create Zip
        val createArgs = JSONObject().apply {
            put("sourcePaths", JSONArray().apply { put(file1.absolutePath) })
            put("zipName", "test_bundle.zip")
            put("destinationPath", workDir.absolutePath)
        }

        val (isCreateErr, createResult) = McpTools.executeTool(context, "create_zip", createArgs)
        assertFalse("Zip creation should succeed", isCreateErr)
        val zipFile = File(workDir, "test_bundle.zip")
        assertTrue("Created zip exists", zipFile.exists())

        // Extract Zip
        val extractDir = File(workDir, "out_extracted").apply { mkdirs() }
        val extractArgs = JSONObject().apply {
            put("zipPath", zipFile.absolutePath)
            put("destinationPath", extractDir.absolutePath)
        }

        val (isExtractErr, extractResult) = McpTools.executeTool(context, "extract_zip", extractArgs)
        assertFalse("Extraction should succeed", isExtractErr)
        val extractedHello = File(extractDir, "hello.txt")
        assertTrue("Extracted file exists", extractedHello.exists())
        assertEquals("Hello MCP", extractedHello.readText())
    }

    @Test
    fun testToolExecutionDownloadFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.filesDir, "download_test.txt").apply { writeText("Downloadable Content 123") }

        val args = JSONObject().apply { put("path", testFile.absolutePath) }
        val (isError, resultJsonStr) = McpTools.executeTool(context, "download_file", args)

        assertFalse("Download tool should succeed", isError)
        val result = JSONObject(resultJsonStr)
        assertEquals("download_test.txt", result.getString("fileName"))
        assertTrue(result.has("contentBase64"))
    }

    @Test
    fun testMcpServerHttpAndAuth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPort = 8991
        val authToken = "mcp_test_token_secret_xyz"

        val serverScope = CoroutineScope(Dispatchers.IO + Job())
        val server = McpServer(
            context = context,
            port = testPort,
            authTokenProvider = { authToken }
        )

        try {
            server.start(serverScope)
            delay(300) // Allow server socket to bind

            val serverUrl = "http://127.0.0.1:$testPort/mcp"

            // 1. Request without auth header should return 401
            val connUnauthorized = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                outputStream.write("{}".toByteArray())
            }
            assertEquals(401, connUnauthorized.responseCode)

            // 2. Request with auth header: initialize
            val initRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject())
            }.toString()

            val connInit = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Auth-Token", authToken)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.write(initRequest.toByteArray())
            }

            assertEquals(200, connInit.responseCode)
            val initResponse = connInit.inputStream.bufferedReader().readText()
            val initJson = JSONObject(initResponse)
            assertEquals("2.0", initJson.getString("jsonrpc"))
            assertTrue(initJson.has("result"))
            val initResult = initJson.getJSONObject("result")
            assertEquals("2024-11-05", initResult.getString("protocolVersion"))

            // 3. Request: tools/list
            val listRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "tools/list")
                put("params", JSONObject())
            }.toString()

            val connList = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Auth-Token", authToken)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.write(listRequest.toByteArray())
            }

            assertEquals(200, connList.responseCode)
            val listResponse = connList.inputStream.bufferedReader().readText()
            val listJson = JSONObject(listResponse)
            val tools = listJson.getJSONObject("result").getJSONArray("tools")
            assertEquals(4, tools.length())

        } finally {
            server.stop()
            serverScope.cancel()
        }
    }
}
