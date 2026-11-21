package com.ulticode.modules.contest.consumer;

import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.contest.service.ContestAdjudicationService;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CONTEST_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.GENERATION;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.MEMORY_MB;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.PROBLEM_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.RUNTIME_MS;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.SUBMISSION_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.USER_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.optionalJudgedString;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.parseExactJudgedLong;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedLong;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedStatus;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedString;

/**
 * Handles durable {@code SubmissionJudged} inbox payloads for contest scoring.
 *
 * <p>Payload validation stays at this transport seam; scoring idempotency and
 * transaction boundaries remain inside {@link ContestAdjudicationService}.</p>
 */
@Component
@RequiredArgsConstructor
public class SubmissionJudgedContestConsumer {

    private final ContestAdjudicationService contestAdjudicationService;
    private final ContestSubmissionMapper contestSubmissionMapper;

    /**
     * Convert one persisted payload into the existing adjudication event seam.
     */
    public void consume(Map<String, Object> payload) {
        String submissionId = requiredJudgedString(payload, SUBMISSION_ID);
        String userId = requiredJudgedString(payload, USER_ID);
        Long problemId = optionalLong(payload.get(PROBLEM_ID));
        long generation = requiredJudgedLong(payload, GENERATION);
        SubmissionStatus status = requiredJudgedStatus(payload);

        if (!status.isTerminal() || status.getKind() == SubmissionStatus.Kind.TERMINAL_INFRA) {
            return;
        }

        String contestId = optionalJudgedString(payload, CONTEST_ID);
        if (contestId != null && contestSubmissionMapper.findBySubmissionId(submissionId).isEmpty()) {
            // SubmissionCreated and SubmissionJudged share an at-least-once
            // stream but are dispatched independently. Keep the judged inbox
            // retryable until the App-owned association is durable.
            throw new IllegalStateException(
                    "Contest association not staged for submission " + submissionId);
        }

        contestAdjudicationService.applyJudgeResult(new SubmissionJudgedEvent(
                this,
                submissionId,
                userId,
                problemId,
                status.wireValue(),
                status == SubmissionStatus.ACCEPTED,
                null,
                null,
                generation > 0 ? generation : 1,
                nonNegativeInt(payload.get(RUNTIME_MS)),
                nonNegativeDouble(payload.get(MEMORY_MB)),
                contestId));
    }

    private static Long optionalLong(Object value) {
        if (value == null || value instanceof String string
                && (string.isBlank() || "null".equalsIgnoreCase(string))) {
            return null;
        }
        try {
            return parseExactJudgedLong(value);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged problemId", e);
        }
    }

    private static int nonNegativeInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            long parsed = parseExactJudgedLong(value);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid non-negative integer SubmissionJudged field");
            }
            return (int) parsed;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged numeric field", e);
        }
    }

    private static double nonNegativeDouble(Object value) {
        if (value == null) {
            return 0;
        }
        final double parsed;
        try {
            parsed = value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged memoryMb", e);
        }
        if (!Double.isFinite(parsed) || parsed < 0) {
            throw new IllegalArgumentException("Invalid SubmissionJudged memoryMb");
        }
        return parsed;
    }
}
