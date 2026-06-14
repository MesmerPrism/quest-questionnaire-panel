package io.github.mesmerprism.questquestionnaire.panel

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalResultWriterTest {
    @Test
    fun closesResultStreamBeforeSendingCallback() {
        val stream = TrackingOutputStream()
        var callbackSawClosedStream = false

        val outcome = writeTerminalResultPayload(
            payload = JSONObject().put("status", "completed"),
            openOutputStream = { stream },
            sendCallback = { callbackSawClosedStream = stream.closed }
        )

        assertEquals(TerminalResultWriteOutcome.CallbackSent, outcome)
        assertTrue(callbackSawClosedStream)
        assertTrue(stream.asUtf8().contains("\"status\": \"completed\""))
    }

    @Test
    fun reportsCallbackFailureAfterWritingResult() {
        val stream = TrackingOutputStream()

        val outcome = writeTerminalResultPayload(
            payload = JSONObject().put("status", "cancelled"),
            openOutputStream = { stream },
            sendCallback = { error("callback unavailable") }
        )

        assertEquals(TerminalResultWriteOutcome.CallbackFailedAfterWrite, outcome)
        assertTrue(stream.closed)
        assertTrue(stream.asUtf8().contains("\"status\": \"cancelled\""))
    }

    @Test
    fun reportsWriteFailureWithoutSendingCallback() {
        var callbackSent = false

        val outcome = writeTerminalResultPayload(
            payload = JSONObject().put("status", "error"),
            openOutputStream = { error("write unavailable") },
            sendCallback = { callbackSent = true }
        )

        assertEquals(TerminalResultWriteOutcome.WriteFailed, outcome)
        assertFalse(callbackSent)
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed: Boolean = false

        override fun close() {
            closed = true
            super.close()
        }

        fun asUtf8(): String = toString(StandardCharsets.UTF_8.name())
    }
}
