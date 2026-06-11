package io.github.mesmerprism.questquestionnaire.nativecaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class QuestionnaireReturnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.data?.lastPathSegment.orEmpty()
        QuestionnaireResultStore.markCallback(context, requestId)
    }
}
