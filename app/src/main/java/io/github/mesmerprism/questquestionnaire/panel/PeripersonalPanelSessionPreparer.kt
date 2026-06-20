package io.github.mesmerprism.questquestionnaire.panel

import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

object PeripersonalPanelSessionPreparer {
    private val FolderTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    private val SessionFolderRegex = Regex("^.+_.+_\\d{8}-\\d{6}$")

    fun prepare(
        appFilesDir: File,
        commandJson: String?,
        receivedAt: Instant = Instant.now()
    ): PeripersonalPanelPrepareResult {
        val command = try {
            PeripersonalPanelCommand.parse(commandJson)
        } catch (exception: PeripersonalPanelCommandException) {
            return rejected(
                command = null,
                issueCode = exception.code,
                message = exception.message ?: exception.code,
                receivedAt = receivedAt
            )
        }

        val validationIssue = validate(command)
        if (validationIssue != null) {
            return rejected(
                command = command,
                issueCode = validationIssue.first,
                message = validationIssue.second,
                receivedAt = receivedAt
            )
        }

        val expectedBreathSide = breathTrackingControllerFromHandedness(command.handedness)
        if (expectedBreathSide.isBlank()) {
            return rejected(
                command = command,
                issueCode = "unsupported_handedness",
                message = "Handedness must be right-handed or left-handed for setup.",
                receivedAt = receivedAt
            )
        }

        val declaredBreathSide = command.breathTrackingControllerSide.normalizedControllerSide()
        if (declaredBreathSide.isNotBlank() && declaredBreathSide != expectedBreathSide) {
            return rejected(
                command = command,
                issueCode = "breath_controller_rule_mismatch",
                message = "Breath tracking controller must be $expectedBreathSide for ${command.handedness}.",
                receivedAt = receivedAt
            )
        }

        val preparedAt = receivedAt.toString()
        val sessionFolderName = resolveSessionFolderName(command, receivedAt)
        if (!isStrictSessionFolderName(sessionFolderName)) {
            return rejected(
                command = command,
                issueCode = "invalid_session_folder_name",
                message = "Session folder must follow ${PeripersonalPanelCommandContract.SessionFolderConvention}.",
                receivedAt = receivedAt
            )
        }

        val sessionRoot = File(appFilesDir, "peripersonal_sessions")
        val sessionDir = File(sessionRoot, sessionFolderName)
        sessionDir.mkdirs()

        val metadata = JSONObject()
            .put("protocol_version", PeripersonalPanelCommandContract.CommandProtocolVersion)
            .put("target_app", PeripersonalPanelCommandContract.TargetApp)
            .put("target_package", PeripersonalPanelCommandContract.TargetPackage)
            .put("session_id", command.sessionId)
            .put("participant_ref", command.participantRef)
            .put("handedness", command.handedness)
            .put("breath_tracking_controller_side", expectedBreathSide)
            .put("session_folder_name", sessionFolderName)
            .put("session_folder_convention", PeripersonalPanelCommandContract.SessionFolderConvention)
            .put("recording_lifecycle", PeripersonalPanelCommandContract.RecordingLifecycle)
            .put("prepared_at_utc", preparedAt)
            .put("command_id", command.commandId)
            .put("sequence", command.sequence)
        val snapshot = JSONObject()
            .put("session_ready", true)
            .put("recording_active", false)
            .put("questionnaire_panel_ready", true)
            .put("questionnaires_run_during_global_recording", true)
            .put("session_folder_name", sessionFolderName)
            .put("session_dir", sessionDir.absolutePath)
            .put("metadata_path", File(sessionDir, MetadataFileName).absolutePath)
            .put("event_log_path", File(sessionDir, EventsFileName).absolutePath)
            .put("handedness", command.handedness)
            .put("breath_tracking_controller_side", expectedBreathSide)

        File(sessionDir, MetadataFileName).writeText(metadata.toString(2), Charsets.UTF_8)
        File(sessionDir, ReadinessFileName).writeText(snapshot.toString(2), Charsets.UTF_8)
        File(sessionDir, EventsFileName).appendText(
            JSONObject()
                .put("event", "session_prepared")
                .put("recorded_at_utc", preparedAt)
                .put("command_id", command.commandId)
                .put("session_id", command.sessionId)
                .put("participant_ref", command.participantRef)
                .put("session_folder_name", sessionFolderName)
                .toString() + "\n",
            Charsets.UTF_8
        )

        return PeripersonalPanelPrepareResult(
            accepted = true,
            prepared = true,
            sessionId = command.sessionId,
            participantRef = command.participantRef,
            sessionFolderName = sessionFolderName,
            sessionDir = sessionDir.absolutePath,
            handedness = command.handedness,
            breathTrackingControllerSide = expectedBreathSide,
            preparedAtUtc = preparedAt,
            receipt = buildReceipt(
                command = command,
                receivedAt = receivedAt,
                accepted = true,
                executed = true,
                completed = true,
                issueCode = "",
                message = "Panel session prepared.",
                observedState = snapshot
            )
        )
    }

    private fun validate(command: PeripersonalPanelCommand): Pair<String, String>? {
        if (command.protocolVersion != PeripersonalPanelCommandContract.CommandProtocolVersion) {
            return "unsupported_protocol" to
                "Unsupported peripersonal command protocol: ${command.protocolVersion}."
        }
        if (command.commandId.isBlank()) {
            return "missing_command_id" to "Command id is required."
        }
        if (command.sequence <= 0) {
            return "invalid_sequence" to "Command sequence must be positive."
        }
        if (command.sessionId.isBlank()) {
            return "missing_session_id" to "Session id is required."
        }
        if (command.participantRef.isBlank()) {
            return "missing_participant_ref" to "Participant id is required."
        }
        if (command.action != "prepare_session") {
            return "unsupported_action" to "Panel command receiver only accepts prepare_session."
        }
        if (
            command.targetApp.isNotBlank() &&
            command.targetApp != PeripersonalPanelCommandContract.TargetApp &&
            command.targetApp != "questionnaire_panel"
        ) {
            return "invalid_target_app" to "Panel commands must target app panel."
        }
        if (
            command.targetPackage.isNotBlank() &&
            command.targetPackage != PeripersonalPanelCommandContract.TargetPackage
        ) {
            return "target_package_mismatch" to
                "Command target package does not match the questionnaire panel package."
        }
        if (
            command.targetRuntimeKind.isNotBlank() &&
            command.targetRuntimeKind != PeripersonalPanelCommandContract.TargetRuntimeKind
        ) {
            return "invalid_target_runtime" to "Command target runtime kind is not the panel APK."
        }
        return null
    }

    private fun rejected(
        command: PeripersonalPanelCommand?,
        issueCode: String,
        message: String,
        receivedAt: Instant
    ): PeripersonalPanelPrepareResult =
        PeripersonalPanelPrepareResult(
            accepted = false,
            prepared = false,
            sessionId = command?.sessionId ?: "",
            participantRef = command?.participantRef ?: "",
            sessionFolderName = "",
            sessionDir = "",
            handedness = command?.handedness ?: "",
            breathTrackingControllerSide = "",
            preparedAtUtc = receivedAt.toString(),
            receipt = buildReceipt(
                command = command,
                receivedAt = receivedAt,
                accepted = false,
                executed = false,
                completed = true,
                issueCode = issueCode,
                message = message,
                observedState = JSONObject().put("session_ready", false)
            )
        )

    private fun buildReceipt(
        command: PeripersonalPanelCommand?,
        receivedAt: Instant,
        accepted: Boolean,
        executed: Boolean,
        completed: Boolean,
        issueCode: String,
        message: String,
        observedState: JSONObject
    ): JSONObject =
        JSONObject()
            .put("protocol_version", PeripersonalPanelCommandContract.ReceiptProtocolVersion)
            .put("command_id", command?.commandId ?: "")
            .put("sequence", command?.sequence ?: 0L)
            .put("session_id", command?.sessionId ?: "")
            .put("target_app", PeripersonalPanelCommandContract.TargetApp)
            .put("action", command?.action ?: "")
            .put("transport_received", "android_ordered_broadcast")
            .put("received_at_utc", receivedAt.toString())
            .put("accepted", accepted)
            .put("executed", executed)
            .put("completed", completed)
            .put("issue_code", issueCode)
            .put("message", message)
            .put("state_revision", 1L)
            .put("observed_state", observedState)

    private fun resolveSessionFolderName(
        command: PeripersonalPanelCommand,
        receivedAt: Instant
    ): String {
        if (command.requestedSessionFolderName.isNotBlank()) {
            return command.requestedSessionFolderName
        }

        return listOf(
            command.participantRef.sanitizeFolderToken(),
            command.sessionId.sanitizeFolderToken(),
            FolderTimestampFormatter.format(command.sentAtUtc.toInstantOrNull() ?: receivedAt)
        ).joinToString("_")
    }

    private fun isStrictSessionFolderName(name: String): Boolean =
        SessionFolderRegex.matches(name) &&
            !name.contains("/") &&
            !name.contains("\\") &&
            !name.contains("..")

    private fun breathTrackingControllerFromHandedness(handedness: String): String =
        when (handedness.trim().lowercase(Locale.US)) {
            "right", "r", "right-handed", "right_handed", "right handed" -> "left"
            "left", "l", "left-handed", "left_handed", "left handed" -> "right"
            else -> ""
        }

    private fun String.normalizedControllerSide(): String =
        when (trim().lowercase(Locale.US)) {
            "left", "l", "left-controller", "left_controller", "left controller" -> "left"
            "right", "r", "right-controller", "right_controller", "right controller" -> "right"
            else -> ""
        }

    private fun String.sanitizeFolderToken(): String {
        val sanitized = trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        return sanitized.ifBlank { "unknown" }
    }

    private fun String.toInstantOrNull(): Instant? {
        if (isBlank()) {
            return null
        }
        return try {
            Instant.parse(this)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(this).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    private const val MetadataFileName = "panel_session_metadata.json"
    private const val ReadinessFileName = "panel_readiness_snapshot.json"
    private const val EventsFileName = "panel_events.jsonl"
}

data class PeripersonalPanelPrepareResult(
    val accepted: Boolean,
    val prepared: Boolean,
    val sessionId: String,
    val participantRef: String,
    val sessionFolderName: String,
    val sessionDir: String,
    val handedness: String,
    val breathTrackingControllerSide: String,
    val preparedAtUtc: String,
    val receipt: JSONObject
)
