package com.ulticode.modules.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

/** Encodes, validates, and resumes durable reconciliation scan checkpoints. */
@Component
public final class ReconciliationCheckpointCodec {

    private final ObjectMapper objectMapper;

    public ReconciliationCheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    Progress initial(LocalDateTime createdSince) {
        return Progress.initial(createdSince == null ? null : createdSince.toString());
    }

    Progress resume(String mode, LocalDateTime createdSince,
                    ReconciliationRun partial, ReconciliationRun completed) {
        if (partial == null || isSuperseded(partial, completed)) {
            return initial(createdSince);
        }
        return decode(mode, createdSince, partial.getDetail());
    }

    Progress decode(String mode, LocalDateTime createdSince, String detailJson) {
        String expectedCreatedSince = createdSince == null ? null : createdSince.toString();
        try {
            JsonNode detail = objectMapper.readTree(detailJson);
            if (detail == null || !mode.equals(checkpointText(detail, "mode"))) {
                throw new IllegalStateException("checkpoint mode does not match run mode");
            }
            JsonNode continuation = checkpointObject(detail, "continuation");
            String persistedCreatedSince = checkpointNullableText(continuation, "createdSince");
            if (!Objects.equals(persistedCreatedSince, expectedCreatedSince)) {
                return initial(createdSince);
            }
            return Progress.from(continuation, persistedCreatedSince);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("invalid reconciliation checkpoint", exception);
        }
    }

    String encode(Progress progress) {
        ObjectNode continuation = objectMapper.createObjectNode();
        if (progress.createdSince == null) {
            continuation.putNull("createdSince");
        } else {
            continuation.put("createdSince", progress.createdSince);
        }

        ObjectNode submission = continuation.putObject("submission");
        submission.put("cursor", progress.submissionCursor);
        submission.put("missing", progress.submissionMissing);
        submission.put("complete", progress.submissionComplete);

        ObjectNode notification = continuation.putObject("notification");
        notification.put("cursor", progress.notificationCursor);
        notification.put("missing", progress.notificationMissing);
        notification.put("complete", progress.notificationComplete);

        ObjectNode audit = continuation.putObject("audit");
        audit.put("offset", progress.auditOffset);
        audit.put("missing", progress.auditMissing);
        audit.put("complete", progress.auditComplete);
        return continuation.toString();
    }

    private static boolean isSuperseded(ReconciliationRun partial, ReconciliationRun completed) {
        if (completed == null || partial.getStartedAt() == null || completed.getStartedAt() == null
                || partial.getRunId() == null || completed.getRunId() == null) {
            return false;
        }
        int startedAtComparison = completed.getStartedAt().compareTo(partial.getStartedAt());
        return startedAtComparison > 0
                || (startedAtComparison == 0
                && completed.getRunId().compareTo(partial.getRunId()) >= 0);
    }

    private static JsonNode checkpointObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("checkpoint field must be an object: " + field);
        }
        return value;
    }

    private static String checkpointText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException("checkpoint field must be text: " + field);
        }
        return value.textValue();
    }

    private static String checkpointNullableText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("checkpoint field must be text or null: " + field);
        }
        return value.textValue();
    }

    private static long checkpointLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IllegalStateException("checkpoint field must be a non-negative integer: " + field);
        }
        return value.longValue();
    }

    private static int checkpointInt(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalStateException("checkpoint field must be a non-negative integer: " + field);
        }
        return value.intValue();
    }

    private static boolean checkpointBoolean(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("checkpoint field must be boolean: " + field);
        }
        return value.booleanValue();
    }

    static final class Progress {
        private final String createdSince;
        private String submissionCursor;
        private long submissionMissing;
        private boolean submissionComplete;
        private String notificationCursor;
        private long notificationMissing;
        private boolean notificationComplete;
        private int auditOffset;
        private long auditMissing;
        private boolean auditComplete;

        private Progress(String createdSince,
                         String submissionCursor, long submissionMissing,
                         boolean submissionComplete, String notificationCursor,
                         long notificationMissing, boolean notificationComplete,
                         int auditOffset, long auditMissing, boolean auditComplete) {
            this.createdSince = createdSince;
            this.submissionCursor = submissionCursor;
            this.submissionMissing = submissionMissing;
            this.submissionComplete = submissionComplete;
            this.notificationCursor = notificationCursor;
            this.notificationMissing = notificationMissing;
            this.notificationComplete = notificationComplete;
            this.auditOffset = auditOffset;
            this.auditMissing = auditMissing;
            this.auditComplete = auditComplete;
        }

        private static Progress initial(String createdSince) {
            return new Progress(createdSince, "", 0L, false, "", 0L, false, 0, 0L, false);
        }

        private static Progress from(JsonNode continuation, String createdSince) {
            JsonNode submission = checkpointObject(continuation, "submission");
            JsonNode notification = checkpointObject(continuation, "notification");
            JsonNode audit = checkpointObject(continuation, "audit");
            Progress progress = new Progress(createdSince,
                    checkpointText(submission, "cursor"), checkpointLong(submission, "missing"),
                    checkpointBoolean(submission, "complete"),
                    checkpointText(notification, "cursor"), checkpointLong(notification, "missing"),
                    checkpointBoolean(notification, "complete"),
                    checkpointInt(audit, "offset"), checkpointLong(audit, "missing"),
                    checkpointBoolean(audit, "complete"));
            if ((!progress.submissionComplete && progress.submissionCursor.isBlank())
                    || (!progress.notificationComplete && progress.notificationCursor.isBlank())) {
                throw new IllegalStateException("incomplete checkpoint is missing a cursor");
            }
            return progress;
        }

        void advanceSubmission(OrphanScan.KeysetResult scan) {
            submissionMissing = Math.addExact(submissionMissing, scan.missing());
            submissionCursor = scan.nextCursor();
            submissionComplete = scan.complete();
        }

        void advanceNotification(OrphanScan.KeysetResult scan) {
            notificationMissing = Math.addExact(notificationMissing, scan.missing());
            notificationCursor = scan.nextCursor();
            notificationComplete = scan.complete();
        }

        void advanceAudit(OrphanScan.OffsetResult scan) {
            auditMissing = Math.addExact(auditMissing, scan.missing());
            auditOffset = Math.addExact(auditOffset, scan.nextOffset());
            auditComplete = scan.complete();
        }

        String submissionCursor() {
            return submissionCursor;
        }

        long submissionMissing() {
            return submissionMissing;
        }

        boolean submissionComplete() {
            return submissionComplete;
        }

        String notificationCursor() {
            return notificationCursor;
        }

        long notificationMissing() {
            return notificationMissing;
        }

        boolean notificationComplete() {
            return notificationComplete;
        }

        int auditOffset() {
            return auditOffset;
        }

        long auditMissing() {
            return auditMissing;
        }

        boolean auditComplete() {
            return auditComplete;
        }

        boolean isComplete() {
            return submissionComplete && notificationComplete && auditComplete;
        }
    }
}
