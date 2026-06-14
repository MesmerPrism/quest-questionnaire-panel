package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalContext
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalStatus
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireResultTest {
    @Test
    fun cancelledResultIncludesTerminalContextWithoutError() {
        val json = QuestionnaireResult.from(
            request = request(),
            status = QuestionnaireTerminalStatus.Cancelled,
            answers = JSONObject(),
            startedAt = StartedAt,
            submittedAt = SubmittedAt,
            terminal = QuestionnaireTerminalContext(
                reason = "user_cancelled",
                currentStage = QuestionnaireContract.StagePostConditionPresence,
                screenIndex = 1
            )
        ).toJson()

        assertEquals("cancelled", json.getString("status"))
        assertTrue(json.isNull("error"))

        val terminal = json.getJSONObject("terminal")
        assertEquals("user_cancelled", terminal.getString("reason"))
        assertEquals(
            QuestionnaireContract.StagePostConditionPresence,
            terminal.getString("current_stage")
        )
        assertEquals(1, terminal.getInt("screen_index"))
    }

    @Test
    fun errorResultIncludesTerminalContextAndSafeError() {
        val json = QuestionnaireResult.from(
            request = request(),
            status = QuestionnaireTerminalStatus.Error,
            answers = JSONObject(),
            startedAt = StartedAt,
            submittedAt = SubmittedAt,
            terminal = QuestionnaireTerminalContext(
                reason = "renderer_runtime_error",
                currentStage = QuestionnaireContract.StagePostConditionPictographic,
                screenIndex = 0
            ),
            error = QuestionnaireResultError(
                code = "renderer_initialization_failed",
                message = "Panel could not initialize the requested questionnaire."
            )
        ).toJson()

        assertEquals("error", json.getString("status"))
        assertEquals(
            "renderer_runtime_error",
            json.getJSONObject("terminal").getString("reason")
        )
        assertEquals(
            "renderer_initialization_failed",
            json.getJSONObject("error").getString("code")
        )
    }

    @Test
    fun completedResultCanIncludeTimingMetadata() {
        val json = QuestionnaireResult.from(
            request = request(),
            status = QuestionnaireTerminalStatus.Completed,
            answers = JSONObject().put("ok", true),
            startedAt = StartedAt,
            submittedAt = SubmittedAt,
            timing = QuestionnaireResultTiming(
                startedAt = StartedAt,
                submittedAt = SubmittedAt,
                durationMs = 42_000,
                screens = listOf(
                    QuestionnaireScreenTiming(
                        screenId = QuestionnaireContract.StagePostConditionPictographic,
                        ordinal = 0,
                        enteredAt = StartedAt,
                        enteredElapsedMs = 0,
                        firstInteractionAt = StartedAt.plusSeconds(3),
                        firstInteractionElapsedMs = 3_000,
                        leftAt = SubmittedAt,
                        leftElapsedMs = 42_000,
                        durationMs = 42_000,
                        interactionCount = 2,
                        validationFailures = 1
                    )
                )
            )
        ).toJson()

        val timing = json.getJSONObject("timing")
        assertEquals(42_000L, timing.getLong("duration_ms"))
        val screen = timing.getJSONArray("screens").getJSONObject(0)
        assertEquals(
            QuestionnaireContract.StagePostConditionPictographic,
            screen.getString("screen_id")
        )
        assertEquals(2, screen.getInt("interaction_count"))
        assertEquals(1, screen.getInt("validation_failures"))
    }

    private fun request(): QuestionnaireRequest =
        QuestionnaireRequest(
            protocolVersion = QuestionnaireContract.ProtocolVersion,
            sessionId = "session-1",
            requestId = "request-1",
            nonce = "0123456789abcdef",
            studyId = "brb",
            schemaId = "brb-questionnaire-v1",
            openStage = QuestionnaireContract.StagePostConditionPictographic,
            conditionNumber = 1,
            screenSequence = QuestionnaireContract.ConditionOnePostSequence
        )

    private companion object {
        val StartedAt: Instant = Instant.parse("2026-06-13T21:10:00Z")
        val SubmittedAt: Instant = Instant.parse("2026-06-13T21:10:42Z")
    }
}
