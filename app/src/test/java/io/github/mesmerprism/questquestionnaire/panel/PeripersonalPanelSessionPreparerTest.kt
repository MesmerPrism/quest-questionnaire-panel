package io.github.mesmerprism.questquestionnaire.panel

import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PeripersonalPanelSessionPreparerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun preparesAppPrivateSessionFolderWithGeorgeLifecycleMetadata() {
        val result = PeripersonalPanelSessionPreparer.prepare(
            appFilesDir = temporaryFolder.root,
            commandJson = prepareCommandJson(
                handedness = "right-handed",
                breathTrackingControllerSide = ""
            ),
            receivedAt = Instant.parse("2026-06-20T12:34:57Z")
        )

        assertTrue(result.accepted)
        assertTrue(result.prepared)
        assertEquals("P001_session-1_20260620-123456", result.sessionFolderName)
        assertEquals("left", result.breathTrackingControllerSide)

        val sessionDir = File(result.sessionDir)
        assertTrue(File(sessionDir, "panel_session_metadata.json").isFile)
        assertTrue(File(sessionDir, "panel_readiness_snapshot.json").isFile)
        assertTrue(File(sessionDir, "panel_events.jsonl").isFile)

        val metadata = JSONObject(File(sessionDir, "panel_session_metadata.json").readText())
        assertEquals("one_global_recording_after_block_1", metadata.getString("recording_lifecycle"))
        assertEquals("participantRef_sessionId_yyyyMMdd-HHmmss", metadata.getString("session_folder_convention"))
        assertEquals("left", metadata.getString("breath_tracking_controller_side"))

        val observedState = result.receipt.getJSONObject("observed_state")
        assertTrue(observedState.getBoolean("session_ready"))
        assertTrue(observedState.getBoolean("questionnaires_run_during_global_recording"))
    }

    @Test
    fun rejectsBreathControllerSideThatDoesNotFollowHandednessRule() {
        val result = PeripersonalPanelSessionPreparer.prepare(
            appFilesDir = temporaryFolder.root,
            commandJson = prepareCommandJson(
                handedness = "left-handed",
                breathTrackingControllerSide = "left"
            ),
            receivedAt = Instant.parse("2026-06-20T12:34:57Z")
        )

        assertFalse(result.accepted)
        assertFalse(result.prepared)
        assertEquals("breath_controller_rule_mismatch", result.receipt.getString("issue_code"))
    }

    @Test
    fun acceptsOperatorSuppliedStrictSessionFolderName() {
        val result = PeripersonalPanelSessionPreparer.prepare(
            appFilesDir = temporaryFolder.root,
            commandJson = prepareCommandJson(
                handedness = "left-handed",
                breathTrackingControllerSide = "right",
                requestedSessionFolderName = "P001_session-1_20260620-130000"
            ),
            receivedAt = Instant.parse("2026-06-20T12:34:57Z")
        )

        assertTrue(result.accepted)
        assertEquals("P001_session-1_20260620-130000", result.sessionFolderName)
        assertEquals("right", result.breathTrackingControllerSide)
    }

    @Test
    fun rejectsCommandsTargetedAtUnity() {
        val result = PeripersonalPanelSessionPreparer.prepare(
            appFilesDir = temporaryFolder.root,
            commandJson = prepareCommandJson(
                targetApp = "unity",
                handedness = "right-handed",
                breathTrackingControllerSide = "left"
            ),
            receivedAt = Instant.parse("2026-06-20T12:34:57Z")
        )

        assertFalse(result.accepted)
        assertEquals("invalid_target_app", result.receipt.getString("issue_code"))
    }

    private fun prepareCommandJson(
        targetApp: String = "panel",
        handedness: String,
        breathTrackingControllerSide: String,
        requestedSessionFolderName: String = ""
    ): String {
        val payload = JSONObject()
            .put("handedness", handedness)
            .put("session_id", "session-1")
            .put("participant_ref", "P001")
        if (breathTrackingControllerSide.isNotBlank()) {
            payload.put("breath_tracking_controller_side", breathTrackingControllerSide)
        }
        if (requestedSessionFolderName.isNotBlank()) {
            payload.put("session_folder_name", requestedSessionFolderName)
        }

        return JSONObject()
            .put("protocolVersion", PeripersonalPanelCommandContract.CommandProtocolVersion)
            .put("commandId", "command-001")
            .put("sequence", 7)
            .put("sessionId", "session-1")
            .put("participantRef", "P001")
            .put("targetApp", targetApp)
            .put("targetPackage", PeripersonalPanelCommandContract.TargetPackage)
            .put("targetRuntimeKind", PeripersonalPanelCommandContract.TargetRuntimeKind)
            .put("action", "prepare_session")
            .put("sentAtUtc", "2026-06-20T12:34:56Z")
            .put("payload", payload)
            .toString()
    }
}
