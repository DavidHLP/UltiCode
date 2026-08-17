package com.ulticode.modules.contest.consumer;

import com.ulticode.modules.contest.integration.ContestSubmissionAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Applies SubmissionCreated events to the App-owned contest association. */
@Component
@RequiredArgsConstructor
public class SubmissionCreatedContestConsumer {

    private final ContestSubmissionAdapter contestSubmissionAdapter;

    public void consume(Map<String, Object> payload) {
        String submissionId = requiredString(payload, "submissionId");
        String userId = requiredString(payload, "userId");
        Long problemId = requiredLong(payload, "problemId");
        String contestId = requiredString(payload, "contestId");
        long generation = requiredLong(payload, "generation");
        if (generation <= 0) {
            throw new IllegalArgumentException("Invalid SubmissionCreated generation");
        }
        String language = requiredString(payload, "language");
        if (language.length() > 50) {
            throw new IllegalArgumentException("SubmissionCreated language is too long");
        }
        LocalDateTime occurredAt = parseDateTime(payload.get("occurredAt"));
        contestSubmissionAdapter.recordSubmissionFromEvent(
                submissionId, userId, problemId, contestId,
                optionalString(payload.get("virtualSessionId")), occurredAt);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionCreated field: " + key);
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing SubmissionCreated field: " + key);
        }
        try {
            long result = value instanceof Number number
                    ? exactLong(number) : Long.parseLong(String.valueOf(value));
            if (result < 0) {
                throw new IllegalArgumentException("Negative SubmissionCreated field: " + key);
            }
            return result;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionCreated field: " + key, e);
        }
    }

    private static long exactLong(Number number) {
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return number.longValue();
        }
        return new BigDecimal(String.valueOf(number)).longValueExact();
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = optionalString(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionCreated field: occurredAt");
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid SubmissionCreated occurredAt", e);
        }
    }
}
