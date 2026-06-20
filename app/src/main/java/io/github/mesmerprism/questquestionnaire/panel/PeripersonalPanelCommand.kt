package io.github.mesmerprism.questquestionnaire.panel

import org.json.JSONException
import org.json.JSONObject

object PeripersonalPanelCommandContract {
    const val CommandProtocolVersion = "viscereality.peripersonal.command.v1"
    const val ReceiptProtocolVersion = "viscereality.peripersonal.command_receipt.v1"
    const val ActionCommand =
        "io.github.mesmerprism.questquestionnaire.panel.action.PERIPERSONAL_COMMAND"
    const val ExtraCommandJson =
        "io.github.mesmerprism.questquestionnaire.panel.extra.COMMAND_JSON"
    const val ExtraReceiptJson =
        "io.github.mesmerprism.questquestionnaire.panel.extra.RECEIPT_JSON"
    const val TargetApp = "panel"
    const val TargetPackage = "io.github.mesmerprism.questquestionnaire.panel"
    const val TargetRuntimeKind = "quest_questionnaire_panel_apk"
    const val SessionFolderConvention = "participantRef_sessionId_yyyyMMdd-HHmmss"
    const val RecordingLifecycle = "one_global_recording_after_block_1"
}

data class PeripersonalPanelCommand(
    val protocolVersion: String,
    val commandId: String,
    val sequence: Long,
    val sessionId: String,
    val participantRef: String,
    val targetApp: String,
    val targetPackage: String,
    val targetRuntimeKind: String,
    val action: String,
    val sentAtUtc: String,
    val handedness: String,
    val breathTrackingControllerSide: String,
    val requestedSessionFolderName: String,
    val payload: JSONObject
) {
    companion object {
        fun parse(commandJson: String?): PeripersonalPanelCommand {
            if (commandJson.isNullOrBlank()) {
                throw PeripersonalPanelCommandException("missing_command_json")
            }

            val json = try {
                JSONObject(commandJson)
            } catch (_: JSONException) {
                throw PeripersonalPanelCommandException("malformed_command_json")
            }

            val payload = json.optionalObject("payload") ?: JSONObject()
            val session = json.optionalObject("session") ?: JSONObject()
            val target = json.optionalObject("target") ?: JSONObject()

            return PeripersonalPanelCommand(
                protocolVersion = json.optionalString("protocol_version", "protocolVersion"),
                commandId = json.optionalString("command_id", "commandId"),
                sequence = json.optionalLong("sequence"),
                sessionId = json.firstString(session, payload, "session_id", "sessionId"),
                participantRef = json.firstString(
                    session,
                    payload,
                    "participant_ref",
                    "participantRef"
                ),
                targetApp = json.optionalString("target_app", "targetApp"),
                targetPackage = target.optionalString(
                    "runtime_package",
                    "runtimePackage",
                    "target_package",
                    "targetPackage"
                ).ifBlank {
                    json.optionalString("target_package", "targetPackage")
                },
                targetRuntimeKind = target.optionalString(
                    "runtime_kind",
                    "runtimeKind",
                    "target_runtime_kind",
                    "targetRuntimeKind"
                ).ifBlank {
                    json.optionalString("target_runtime_kind", "targetRuntimeKind")
                },
                action = json.optionalString("action"),
                sentAtUtc = json.optionalString("sent_at_utc", "sentAtUtc"),
                handedness = json.firstString(session, payload, "handedness"),
                breathTrackingControllerSide = json.firstString(
                    session,
                    payload,
                    "breath_tracking_controller_side",
                    "breathTrackingControllerSide"
                ),
                requestedSessionFolderName = json.firstString(
                    session,
                    payload,
                    "session_folder_name",
                    "sessionFolderName"
                ),
                payload = JSONObject(payload.toString())
            )
        }
    }
}

class PeripersonalPanelCommandException(val code: String) :
    IllegalArgumentException("Invalid peripersonal panel command: $code")

internal fun JSONObject.optionalString(vararg names: String): String {
    for (name in names) {
        if (!has(name) || isNull(name)) {
            continue
        }
        val value = opt(name)
        if (value is String && value.isNotBlank()) {
            return value.trim()
        }
    }
    return ""
}

private fun JSONObject.optionalLong(name: String): Long {
    if (!has(name) || isNull(name)) {
        return 0L
    }
    val value = opt(name)
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

private fun JSONObject.optionalObject(name: String): JSONObject? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return opt(name) as? JSONObject
}

private fun JSONObject.firstString(
    first: JSONObject,
    second: JSONObject,
    vararg names: String
): String =
    optionalString(*names)
        .ifBlank { first.optionalString(*names) }
        .ifBlank { second.optionalString(*names) }
