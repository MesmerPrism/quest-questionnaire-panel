package io.github.mesmerprism.questquestionnaire.panel

import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStream
import org.json.JSONObject

class TerminalResultWriter {
    fun write(
        contentResolver: ContentResolver,
        resultUri: Uri,
        returnToCaller: PendingIntent,
        result: QuestionnaireResult
    ): TerminalResultWriteOutcome =
        writeTerminalResultPayload(
            payload = result.toJson(),
            openOutputStream = { contentResolver.openOutputStream(resultUri, "wt") },
            sendCallback = { returnToCaller.send() }
        )
}

sealed class TerminalResultWriteOutcome {
    object CallbackSent : TerminalResultWriteOutcome()
    object WriteFailed : TerminalResultWriteOutcome()
    object CallbackFailedAfterWrite : TerminalResultWriteOutcome()
}

internal fun writeTerminalResultPayload(
    payload: JSONObject,
    openOutputStream: () -> OutputStream?,
    sendCallback: () -> Unit
): TerminalResultWriteOutcome {
    try {
        openOutputStream().use { stream ->
            requireNotNull(stream) { "Could not open result URI for writing" }
                .write(payload.toString(2).toByteArray(Charsets.UTF_8))
        }
    } catch (_: Exception) {
        return TerminalResultWriteOutcome.WriteFailed
    }

    return try {
        sendCallback()
        TerminalResultWriteOutcome.CallbackSent
    } catch (_: Exception) {
        TerminalResultWriteOutcome.CallbackFailedAfterWrite
    }
}
