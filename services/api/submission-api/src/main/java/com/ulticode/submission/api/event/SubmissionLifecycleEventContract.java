package com.ulticode.submission.api.event;

import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Wire contract for the Submission owner's durable lifecycle events.
 *
 * <p>Payloads deliberately carry facts needed by consumers, not source code or
 * hidden test data. The outbox envelope supplies {@code eventId}, aggregate
 * version, causation and trace metadata; these names keep the payload stable
 * across the App-to-Submission cutover.
 *
 * <p>Field sets below describe <strong>payload</strong> fields only; envelope
 * fields live in {@link IntegrationEventEnvelopeContract#FIELDS} and must not
 * be mixed into payloads. Wiring status:
 * <ul>
 *   <li>{@link #JUDGED_EVENT_TYPE} — live: Submission's
 *       {@code SubmissionResultDispatcher} publishes schema-v1
 *       {@code SubmissionJudged} payloads exactly matching
 *       {@link #JUDGED_FIELDS} (contestId omitted when absent).</li>
 *   <li>{@link #CREATED_EVENT_TYPE} — live for remote contest intake; the
 *       optional {@code virtualSessionId} is present only for virtual
 *       sessions.</li>
 * </ul>
 */
public final class SubmissionLifecycleEventContract {

    public static final int SCHEMA_VERSION = 1;
    public static final String OWNER = "Submission";
    public static final String CREATED_EVENT_TYPE = "SubmissionCreated";
    public static final String JUDGED_EVENT_TYPE = "SubmissionJudged";

    public static final Set<String> ENVELOPE_FIELDS = IntegrationEventEnvelopeContract.FIELDS;
    public static final String EVENT_ID = IntegrationEventEnvelopeContract.EVENT_ID;
    public static final String OWNER_FIELD = IntegrationEventEnvelopeContract.OWNER;
    public static final String EVENT_TYPE_FIELD = IntegrationEventEnvelopeContract.EVENT_TYPE;
    public static final String SCHEMA_VERSION_FIELD = IntegrationEventEnvelopeContract.SCHEMA_VERSION;
    public static final String AGGREGATE_ID = IntegrationEventEnvelopeContract.AGGREGATE_ID;
    public static final String AGGREGATE_VERSION = IntegrationEventEnvelopeContract.AGGREGATE_VERSION;
    public static final String CAUSATION_ID = IntegrationEventEnvelopeContract.CAUSATION_ID;
    public static final String TRACE_ID = IntegrationEventEnvelopeContract.TRACE_ID;
    public static final String PAYLOAD = IntegrationEventEnvelopeContract.PAYLOAD;
    public static final String SUBMISSION_ID = "submissionId";
    public static final String USER_ID = "userId";
    public static final String PROBLEM_ID = "problemId";
    public static final String CONTEST_ID = "contestId";
    public static final String VIRTUAL_SESSION_ID = "virtualSessionId";
    public static final String GENERATION = "generation";
    public static final String ATTEMPT_ID = "attemptId";
    public static final String LANGUAGE = "language";
    public static final String STATUS = "status";
    public static final String VERDICT = "verdict";
    public static final String RUNTIME_MS = "runtimeMs";
    public static final String MEMORY_MB = "memoryMb";
    public static final String OCCURRED_AT = "occurredAt";

    public static final Set<String> CREATED_FIELDS = Set.of(
            SUBMISSION_ID, USER_ID, PROBLEM_ID, CONTEST_ID,
            VIRTUAL_SESSION_ID, GENERATION, LANGUAGE, OCCURRED_AT);
    public static final Set<String> JUDGED_FIELDS = Set.of(
            SUBMISSION_ID, USER_ID, PROBLEM_ID, CONTEST_ID,
            GENERATION, VERDICT, RUNTIME_MS, MEMORY_MB);
    public static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "code", "sourceCode", "testCases", "hiddenTestCases",
            "accessToken", "refreshToken", "cookie", "password");

    private SubmissionLifecycleEventContract() {
    }

    /** Read fields individually so consumers can preserve their validation order. */
    public static String requiredJudgedString(Map<String, Object> payload, String key) {
        String value = optionalJudgedString(payload, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        return value;
    }

    public static String optionalJudgedString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static SubmissionStatus requiredJudgedStatus(Map<String, Object> payload) {
        return SubmissionStatus.fromWire(requiredJudgedString(payload, VERDICT));
    }

    public static long requiredJudgedLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        try {
            long parsed = parseExactJudgedLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("Negative SubmissionJudged field: " + key);
            }
            return parsed;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged field: " + key, e);
        }
    }

    /** Parse a present integer without truncation. The caller owns missing values and field bounds. */
    public static long parseExactJudgedLong(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number)).longValueExact();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /** Read legacy delivery runtime, narrowing numeric values and clamping negatives to zero. */
    public static long legacyJudgedRuntimeMs(Map<String, Object> payload) {
        Object value = payload.get(RUNTIME_MS);
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return Math.max(0L, Long.parseLong(text));
        }
        return 0L;
    }

    /** Read legacy delivery memory in MB, leaving non-finite values to the caller. */
    public static double legacyJudgedMemoryMb(Map<String, Object> payload) {
        Object value = payload.get(MEMORY_MB);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0;
    }
}
