package io.github.mesmerprism.questquestionnaire.nativecaller

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

data class OperatorBridgeLaunchResult(
    val accepted: Boolean,
    val message: String,
    val sessionId: String? = null,
    val questionnaireId: String? = null,
    val openStage: String? = null
) {
    companion object {
        fun rejected(message: String): OperatorBridgeLaunchResult =
            OperatorBridgeLaunchResult(accepted = false, message = message)
    }
}

class QuestQuestionnaireOperatorBridge(
    private val activity: NativeCallerActivity,
    private val port: Int = 8787
) {
    @Volatile
    private var running = false

    @Volatile
    private var panelForeground = false

    @Volatile
    private var lastCommand: JSONObject? = null

    @Volatile
    private var lastResult: JSONObject? = null

    @Volatile
    private var lastMessage: String = "Questionnaire bridge ready."

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        serverThread = Thread({ serve() }, "quest-questionnaire-operator-bridge").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun markCallerForeground(summary: String) {
        panelForeground = false
        lastMessage = summary
        if (summary.startsWith("Result status=")) {
            val status = summary.substringAfter("Result status=").substringBefore(" ")
            lastResult = (lastResult ?: JSONObject()).apply {
                put("status", status)
            }
        }
    }

    private fun serve() {
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                serverSocket = socket
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        if (!running) break else continue
                    }
                    Thread({ handle(client) }, "quest-questionnaire-operator-bridge-client").also {
                        it.isDaemon = true
                        it.start()
                    }
                }
            }
        } catch (exception: Exception) {
            lastMessage = "Questionnaire bridge stopped: ${exception.javaClass.simpleName}"
        } finally {
            running = false
            serverSocket = null
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) {
                writeJson(client, 400, JSONObject().put("message", "Missing HTTP request line."))
                return
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeJson(client, 400, JSONObject().put("message", "Malformed HTTP request line."))
                return
            }
            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                val separator = header.indexOf(':')
                if (separator > 0 && header.substring(0, separator).equals("Content-Length", true)) {
                    contentLength = header.substring(separator + 1).trim().toIntOrNull() ?: 0
                }
            }
            val method = parts[0]
            val path = parts[1]
            when {
                method == "GET" && path == "/v1/status" ->
                    writeJson(client, 200, statusJson())
                method == "POST" && path == "/v1/command" -> {
                    val body = readBody(reader, contentLength)
                    val response = runCatching { applyCommand(JSONObject(body)) }
                        .getOrElse { error ->
                            CommandResponse(
                                statusCode = 400,
                                body = JSONObject().put("message", "Invalid command JSON: ${error.message}")
                            )
                        }
                    writeJson(client, response.statusCode, response.body)
                }
                else ->
                    writeJson(client, 404, JSONObject().put("message", "Unknown bridge route."))
            }
        }
    }

    private fun readBody(reader: BufferedReader, contentLength: Int): String {
        if (contentLength <= 0) return "{}"
        val buffer = CharArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = reader.read(buffer, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return String(buffer, 0, offset)
    }

    private fun applyCommand(payload: JSONObject): CommandResponse {
        if (payload.optString("protocol_version") != "quest.questionnaire.operator.v1") {
            return rejected(payload, "Unsupported operator protocol version.")
        }
        return when (payload.optString("action")) {
            "open_questionnaire" -> openQuestionnaire(payload)
            "dismiss_questionnaire" -> rejected(
                payload,
                "Dismiss is not supported by the device bridge; complete or cancel the panel."
            )
            else -> rejected(payload, "Unsupported questionnaire action.")
        }
    }

    private fun openQuestionnaire(payload: JSONObject): CommandResponse {
        val result = launchOnUiThread(payload)
        val commandId = payload.optString("command_id")
        val commandName = payload.optString("command_name")
        lastCommand = JSONObject()
            .put("command_id", commandId.ifBlank { JSONObject.NULL })
            .put("command_name", commandName.ifBlank { JSONObject.NULL })
            .put("accepted", result.accepted)
            .put("message", result.message)
        lastMessage = result.message
        if (result.accepted) {
            panelForeground = true
            lastResult = JSONObject()
                .put("request_id", commandId.ifBlank { JSONObject.NULL })
                .put("session_id", result.sessionId ?: JSONObject.NULL)
                .put("status", "foreground")
                .put("questionnaire_id", result.questionnaireId ?: JSONObject.NULL)
                .put("open_stage", result.openStage ?: JSONObject.NULL)
        }
        val body = JSONObject()
            .put("protocol_version", "quest.questionnaire.operator.v1")
            .put("accepted", result.accepted)
            .put("message", result.message)
            .put("foreground", foregroundJson(result.questionnaireId, result.openStage))
            .put("last_result", lastResult ?: JSONObject.NULL)
        return CommandResponse(if (result.accepted) 200 else 400, body)
    }

    private fun launchOnUiThread(payload: JSONObject): OperatorBridgeLaunchResult {
        var result = OperatorBridgeLaunchResult.rejected("Timed out launching questionnaire.")
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            result = activity.launchOperatorBridgeQuestionnaire(payload)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return result
    }

    private fun rejected(payload: JSONObject, message: String): CommandResponse {
        lastCommand = JSONObject()
            .put("command_id", payload.optString("command_id").ifBlank { JSONObject.NULL })
            .put("command_name", payload.optString("command_name").ifBlank { JSONObject.NULL })
            .put("accepted", false)
            .put("message", message)
        lastMessage = message
        return CommandResponse(
            statusCode = 400,
            body = JSONObject()
                .put("protocol_version", "quest.questionnaire.operator.v1")
                .put("accepted", false)
                .put("message", message)
                .put("foreground", foregroundJson(null, null))
                .put("last_result", lastResult ?: JSONObject.NULL)
        )
    }

    private fun statusJson(): JSONObject =
        JSONObject()
            .put("protocol_version", "quest.questionnaire.operator.v1")
            .put(
                "bridge",
                JSONObject()
                    .put("app", "quest-questionnaire-native-caller")
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("device_label", "quest")
            )
            .put("foreground", foregroundJson(null, null))
            .put("last_command", lastCommand ?: JSONObject.NULL)
            .put("last_result", lastResult ?: JSONObject.NULL)
            .put("message", activity.operatorBridgeStatusSummary().ifBlank { lastMessage })

    private fun foregroundJson(questionnaireId: String?, openStage: String?): JSONObject =
        JSONObject()
            .put("xr_app_foreground", !panelForeground)
            .put("panel_foreground", panelForeground)
            .put(
                "foreground_package",
                if (panelForeground) QuestionnaireContract.PanelPackage else activity.packageName
            )
            .put(
                "foreground_activity",
                if (panelForeground) QuestionnaireContract.PanelActivity else ".NativeCallerActivity"
            )
            .put(
                "questionnaire_id",
                questionnaireId ?: lastResult?.optString("questionnaire_id")?.takeIf { it.isNotBlank() }
                    ?: JSONObject.NULL
            )
            .put(
                "open_stage",
                openStage ?: lastResult?.optString("open_stage")?.takeIf { it.isNotBlank() }
                    ?: JSONObject.NULL
            )

    private fun writeJson(socket: Socket, statusCode: Int, body: JSONObject) {
        val bodyBytes = body.toString(2).toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode in 200..299) "OK" else "Error"
        val header = buildString {
            append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write(header)
            output.write(bodyBytes)
            output.flush()
        }
    }

    private data class CommandResponse(
        val statusCode: Int,
        val body: JSONObject
    )
}
