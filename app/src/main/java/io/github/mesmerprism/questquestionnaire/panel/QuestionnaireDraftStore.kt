package io.github.mesmerprism.questquestionnaire.panel

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class QuestionnaireDraftStore internal constructor(
    private val draftsDir: File
) {
    fun read(request: QuestionnaireRequest): QuestionnaireDraftSnapshot? {
        val file = draftFileFor(request)
        if (!file.isFile) {
            return null
        }

        val json = try {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            return null
        }

        return try {
            val snapshot = QuestionnaireDraftSnapshot(
                screenIndex = json.getInt("screen_index"),
                state = JSONObject(json.getJSONObject("state").toString())
            )
            if (json.matches(request) && snapshot.screenIndex in request.screenSequence.indices) {
                snapshot
            } else {
                null
            }
        } catch (_: JSONException) {
            null
        }
    }

    fun write(
        request: QuestionnaireRequest,
        screenIndex: Int,
        state: JSONObject
    ) {
        draftsDir.mkdirs()
        val file = draftFileFor(request)
        val tempFile = File(draftsDir, "${file.name}.tmp")
        val payload = JSONObject()
            .put("draft_version", DraftVersion)
            .put("protocol_version", request.protocolVersion)
            .put("session_id", request.sessionId)
            .put("request_id", request.requestId)
            .put("nonce", request.nonce)
            .put("study_id", request.studyId)
            .put("schema_id", request.schemaId)
            .put("open_stage", request.openStage)
            .put("condition_number", request.conditionNumber ?: JSONObject.NULL)
            .put("screen_sequence", JSONArray(request.screenSequence))
            .put("screen_index", screenIndex.coerceIn(request.screenSequence.indices))
            .put("state", JSONObject(state.toString()))

        tempFile.writeText(payload.toString(), StandardCharsets.UTF_8)
        if (file.exists()) {
            file.delete()
        }
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            throw IllegalStateException("draft_write_failed")
        }
    }

    fun clear(request: QuestionnaireRequest) {
        val file = draftFileFor(request)
        if (file.exists()) {
            file.delete()
        }
        val tempFile = File(draftsDir, "${file.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    internal fun draftFileFor(request: QuestionnaireRequest): File =
        File(draftsDir, "draft-v$DraftVersion-${request.draftHash()}.json")

    private fun JSONObject.matches(request: QuestionnaireRequest): Boolean =
        optInt("draft_version", -1) == DraftVersion &&
            optString("protocol_version") == request.protocolVersion &&
            optString("session_id") == request.sessionId &&
            optString("request_id") == request.requestId &&
            optString("nonce") == request.nonce &&
            optString("study_id") == request.studyId &&
            optString("schema_id") == request.schemaId &&
            optString("open_stage") == request.openStage &&
            optionalIntOrNull("condition_number") == request.conditionNumber &&
            stringList("screen_sequence") == request.screenSequence

    private fun JSONObject.optionalIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) {
            null
        } else {
            optInt(name)
        }

    private fun JSONObject.stringList(name: String): List<String>? {
        val array = optJSONArray(name) ?: return null
        val items = mutableListOf<String>()
        for (index in 0 until array.length()) {
            items += array.optString(index, "")
        }
        return items
    }

    private fun QuestionnaireRequest.draftHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$requestId\u0000$nonce".toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val DraftVersion = 1

        fun create(context: Context): QuestionnaireDraftStore =
            QuestionnaireDraftStore(
                File(context.noBackupFilesDir, "questionnaire-drafts")
            )
    }
}

data class QuestionnaireDraftSnapshot(
    val screenIndex: Int,
    val state: JSONObject
)
