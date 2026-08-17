package com.ulticode.modules.contest.consumer;

import com.ulticode.app.api.event.SubmissionJudgedEvent;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.contest.service.ContestAdjudicationService;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

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
        String submissionId = requiredString(payload, "submissionId");
        String userId = requiredString(payload, "userId");
        Long problemId = optionalLong(payload.get("problemId"));
        long generation = requiredLong(payload, "generation");
        String verdict = requiredString(payload, "verdict");
        SubmissionStatus status = SubmissionStatus.fromWire(verdict);

        if (!status.isTerminal() || status.getKind() == SubmissionStatus.Kind.TERMINAL_INFRA) {
            return;
        }

        String contestId = optionalString(payload.get("contestId"));
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
                verdict,
                status == SubmissionStatus.ACCEPTED,
                null,
                null,
                generation > 0 ? generation : 1,
                nonNegativeInt(payload.get("runtimeMs")),
                nonNegativeDouble(payload.get("memoryMb")),
                contestId));
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

    private static Long optionalLong(Object value) {
        if (value == null || value instanceof String string
                && (string.isBlank() || "null".equalsIgnoreCase(string))) {
            return null;
        }
        try {
            return value instanceof Number number
                    ? exactLong(number)
                    : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged problemId", e);
        }
    }

    private static long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        try {
            long parsed = value instanceof Number number
                    ? exactLong(number)
                    : Long.parseLong(String.valueOf(value));
            if (parsed < 0) {
                throw new IllegalArgumentException("Negative SubmissionJudged field: " + key);
            }
            return parsed;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged field: " + key, e);
        }
    }

    private static long exactLong(Number number) {
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return number.longValue();
        }
        return new BigDecimal(String.valueOf(number)).longValueExact();
    }

    private static int nonNegativeInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            long parsed = value instanceof Number number
                    ? exactLong(number)
                    : Long.parseLong(String.valueOf(value));
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
