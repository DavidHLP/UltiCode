package com.ulticode.modules.websocket.consumer;

import com.ulticode.submission.api.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.port.SubmissionResultPushPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CONTEST_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.PROBLEM_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.SUBMISSION_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.USER_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.legacyJudgedMemoryMb;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.legacyJudgedRuntimeMs;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.optionalJudgedString;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedStatus;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedString;

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
        String submissionId = requiredJudgedString(payload, SUBMISSION_ID);
        String userId = requiredJudgedString(payload, USER_ID);
        String problemId = optionalJudgedString(payload, PROBLEM_ID);
        String contestId = optionalJudgedString(payload, CONTEST_ID);
        SubmissionStatus status = requiredJudgedStatus(payload);
        if (!status.isTerminal()) {
            return;
        }

        int runtimeMs = (int) Math.min(Integer.MAX_VALUE, legacyJudgedRuntimeMs(payload));
        long memoryBytes = memoryBytes(legacyJudgedMemoryMb(payload));
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

    private static long memoryBytes(double memoryMb) {
        if (!Double.isFinite(memoryMb) || memoryMb <= 0) {
            return 0L;
        }
        double bytes = memoryMb * 1024L * 1024L;
        return bytes >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) bytes;
    }
}
