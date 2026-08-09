package com.ulticode.modules.websocket.consumer;

import com.ulticode.app.api.dto.SubmissionResultPayload;
import com.ulticode.app.api.service.SubmissionResultPushPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles the durable {@code SubmissionJudged} event for the submission-result
 * WebSocket queue. The inbox bridge owns event deduplication and retry state;
 * this consumer only restores the existing wire payload after the event is
 * durably published.
 */
@Component
@RequiredArgsConstructor
public class SubmissionJudgedWebSocketConsumer {

    private final SubmissionResultPushPort resultPushPort;

    /**
     * Push one terminal result using the legacy {@code submission_result}
     * payload shape. A malformed event throws so the inbox can retry it.
     */
    public void consume(Map<String, Object> payload) {
        String submissionId = requiredString(payload, "submissionId");
        String userId = requiredString(payload, "userId");
        String problemId = optionalString(payload.get("problemId"));
        String contestId = optionalString(payload.get("contestId"));
        String verdict = requiredString(payload, "verdict");
        SubmissionStatus status = SubmissionStatusCodec.fromWire(verdict);
        if (!status.isTerminal()) {
            return;
        }

        int runtimeMs = nonNegativeInt(payload.get("runtimeMs"));
        long memoryBytes = memoryBytes(payload.get("memoryMb"));
        resultPushPort.emitSubmissionResult(
                userId,
                SubmissionResultPayload.of(
                        submissionId,
                        contestId,
                        problemId,
                        userId,
                        status.wireValue(),
                        0.0,
                        runtimeMs,
                        memoryBytes));
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int nonNegativeInt(Object value) {
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else if (value instanceof String text && !text.isBlank()) {
            parsed = Long.parseLong(text);
        } else {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, parsed));
    }

    private static long memoryBytes(Object value) {
        double memoryMb;
        if (value instanceof Number number) {
            memoryMb = number.doubleValue();
        } else if (value instanceof String text && !text.isBlank()) {
            memoryMb = Double.parseDouble(text);
        } else {
            return 0L;
        }
        if (!Double.isFinite(memoryMb) || memoryMb <= 0) {
            return 0L;
        }
        double bytes = memoryMb * 1024L * 1024L;
        return bytes >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) bytes;
    }
}
