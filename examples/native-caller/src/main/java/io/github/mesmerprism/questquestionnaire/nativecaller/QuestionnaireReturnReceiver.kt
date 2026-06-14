package io.github.mesmerprism.questquestionnaire.nativecaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mesmerprism.questquestionnaire.sdk.PendingQuestionnaireStore

class QuestionnaireReturnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.data?.lastPathSegment.orEmpty()
        PendingQuestionnaireStore().markCallback(context, requestId)
    }
}
