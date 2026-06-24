package io.github.mesmerprism.questquestionnaire.panel

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * Low-rate app-owned foreground status for the Windows operator shell.
 *
 * This is observability only. Questionnaire answers still use the caller-owned
 * content URI result contract; WPF consumes these UDP beacons only to classify
 * which Quest app currently owns input.
 */
internal class ForegroundStatusBeacon(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val sequence = AtomicLong()
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "foreground-status-beacon").apply { isDaemon = true }
        }

    @Volatile private var activityStarted = false
    @Volatile private var activityResumed = false
    @Volatile private var windowFocused = false
    @Volatile private var sessionId = ""
    @Volatile private var requestId = ""
    @Volatile private var studyId = ""
    @Volatile private var schemaId = ""
    @Volatile private var openStage = ""
    @Volatile private var closed = false

    init {
        executor.scheduleAtFixedRate(
            { publish("tick") },
            0L,
            BeaconIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    fun updateLaunch(request: QuestionnaireRequest) {
        sessionId = request.sessionId
        requestId = request.requestId
        studyId = request.studyId
        schemaId = request.schemaId
        openStage = request.openStage
        publish("launch_request")
    }

    fun onActivityStarted() {
        activityStarted = true
        publish("activity_started")
    }

    fun onActivityResumed() {
        activityResumed = true
        publish("activity_resumed")
    }

    fun onActivityPaused() {
        activityResumed = false
        publish("activity_paused")
    }

    fun onActivityStopped() {
        activityStarted = false
        activityResumed = false
        windowFocused = false
        publish("activity_stopped")
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        windowFocused = hasFocus
        publish(if (hasFocus) "window_focus_acquired" else "window_focus_lost")
    }

    fun publish(reason: String) {
        if (closed) {
            return
        }

        executor.execute {
            if (closed) {
                return@execute
            }
            sendPayload(buildPayload(reason))
        }
    }

    override fun close() {
        closed = true
        executor.shutdownNow()
    }

    private fun buildPayload(reason: String): String {
        val lifecycle = JSONObject()
            .put("activity_started", activityStarted)
            .put("activity_resumed", activityResumed)
            .put("window_focused", windowFocused)

        val questionnaire = JSONObject()
            .put("request_id", requestId)
            .put("questionnaire_id", schemaId)
            .put("study_id", studyId)
            .put("open_stage", openStage)

        return JSONObject()
            .put("protocol_version", ProtocolVersion)
            .put("source_app", "panel")
            .put("package_name", appContext.packageName)
            .put("activity_name", "QuestionnaireActivity")
            .put("sequence", sequence.incrementAndGet())
            .put("emitted_at_utc", Instant.now().toString())
            .put("session_id", sessionId)
            .put("participant_ref", "")
            .put("reason", reason)
            .put("lifecycle", lifecycle)
            .put("questionnaire", questionnaire)
            .toString()
    }

    private fun sendPayload(payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val addresses = listOf(BroadcastAddress, MulticastAddress)
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (address in addresses) {
                    try {
                        socket.send(
                            DatagramPacket(
                                bytes,
                                bytes.size,
                                InetAddress.getByName(address),
                                BeaconPort
                            )
                        )
                    } catch (_: Exception) {
                        // Best-effort telemetry; questionnaire flow must not depend on it.
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort telemetry; questionnaire flow must not depend on it.
        }
    }

    private companion object {
        const val ProtocolVersion = "viscereality.foreground_status.v1"
        const val BeaconPort = 47892
        const val BroadcastAddress = "255.255.255.255"
        const val MulticastAddress = "239.255.42.42"
        const val BeaconIntervalMs = 1000L
    }
}
