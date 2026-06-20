package io.github.mesmerprism.questquestionnaire.panel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.time.Instant

class PeripersonalPanelCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val commandJson = intent.getStringExtra(PeripersonalPanelCommandContract.ExtraCommandJson)
            ?: intent.getStringExtra("command_json")
        val result = PeripersonalPanelSessionPreparer.prepare(
            appFilesDir = context.filesDir,
            commandJson = commandJson,
            receivedAt = Instant.now()
        )
        if (result.prepared) {
            context.getSharedPreferences(SessionPreferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString("session_id", result.sessionId)
                .putString("participant_ref", result.participantRef)
                .putString("session_folder_name", result.sessionFolderName)
                .putString("session_dir", result.sessionDir)
                .putString("handedness", result.handedness)
                .putString("breath_tracking_controller_side", result.breathTrackingControllerSide)
                .putString("prepared_at_utc", result.preparedAtUtc)
                .apply()
        }

        val receiptJson = result.receipt.toString(2)
        val extras = Bundle().apply {
            putString(PeripersonalPanelCommandContract.ExtraReceiptJson, receiptJson)
            putString("receipt_json", receiptJson)
        }

        setResultCode(if (result.accepted) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        setResultData(receiptJson)
        setResultExtras(extras)
    }

    private companion object {
        const val SessionPreferencesName = "peripersonal_panel_session"
    }
}
