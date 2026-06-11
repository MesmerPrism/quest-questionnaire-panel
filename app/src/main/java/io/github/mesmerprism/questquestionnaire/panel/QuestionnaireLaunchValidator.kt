package io.github.mesmerprism.questquestionnaire.panel

data class QuestionnaireLaunchSpec(
    val action: String?,
    val mimeType: String?,
    val hasWriteGrant: Boolean,
    val dataUri: String?,
    val resultUri: String?,
    val hasReturnToCaller: Boolean,
    val requestJson: String?,
    val sessionIdExtra: String?,
    val requestIdExtra: String?,
    val nonceExtra: String?
)

sealed class QuestionnaireLaunchValidation {
    data class Valid(val request: QuestionnaireRequest) : QuestionnaireLaunchValidation()
    data class Invalid(val code: String) : QuestionnaireLaunchValidation()
}

object QuestionnaireLaunchValidator {
    fun validate(spec: QuestionnaireLaunchSpec): QuestionnaireLaunchValidation {
        if (spec.action != QuestionnaireContract.ActionStart) {
            return QuestionnaireLaunchValidation.Invalid("invalid_action")
        }
        if (spec.mimeType != QuestionnaireContract.RequestMimeType) {
            return QuestionnaireLaunchValidation.Invalid("invalid_request_type")
        }
        if (!spec.hasWriteGrant) {
            return QuestionnaireLaunchValidation.Invalid("missing_write_grant")
        }

        val parsedRequest = try {
            QuestionnaireRequest.parse(
                requestJson = spec.requestJson,
                sessionIdExtra = spec.sessionIdExtra,
                requestIdExtra = spec.requestIdExtra,
                nonceExtra = spec.nonceExtra
            )
        } catch (exception: QuestionnaireRequestException) {
            return QuestionnaireLaunchValidation.Invalid(exception.code)
        }

        val resultUri = spec.resultUri
            ?: return QuestionnaireLaunchValidation.Invalid("missing_result_uri")
        if (!resultUri.startsWith("content://")) {
            return QuestionnaireLaunchValidation.Invalid("invalid_result_uri")
        }
        if (spec.dataUri != resultUri) {
            return QuestionnaireLaunchValidation.Invalid("result_uri_mismatch")
        }
        if (!spec.hasReturnToCaller) {
            return QuestionnaireLaunchValidation.Invalid("missing_return_to_caller")
        }

        return QuestionnaireLaunchValidation.Valid(parsedRequest)
    }
}
