package com.ulticode.modules.queue.outbox.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.outbox.JudgeOutboxPayload;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;

/**
 * Translates the Submission-owned outbox representation into the
 * provider-owned Judge envelope.
 *
 * <p>The dispatcher owns delivery state; this package-local seam owns envelope
 * assembly, limit defaults, and attempt identity. Payload decoding and
 * required-field validation live in {@link JudgeOutboxPayload}.</p>
 */
final class JudgeJobEnvelopeTranslator {

    private static final int DEFAULT_TIME_LIMIT_MS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 256 * 1024;

    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    JudgeJobEnvelopeTranslator(ObjectMapper objectMapper, UuidGenerator uuidGenerator) {
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * Return {@code null} for an unusable row so the caller can dead-letter it
     * without marking a partially populated envelope as sent.
     */
    JudgeJobEnvelope translate(JudgeOutboxRecord row) {
        if (row == null || !hasText(row.getSubmissionId())) {
            return null;
        }
        return JudgeOutboxPayload.fromLegacy(row.getPayload(), objectMapper)
                .map(payload -> new JudgeJobEnvelope(
                        JudgeJobEnvelope.VERSION_2,
                        row.getId(),
                        row.getSubmissionId(),
                        payload.problemId(),
                        payload.userId(),
                        payload.language(),
                        payload.code(),
                        payload.timeLimitMs() == null
                                ? DEFAULT_TIME_LIMIT_MS : payload.timeLimitMs(),
                        payload.memoryLimitKb() == null
                                ? DEFAULT_MEMORY_LIMIT_KB : payload.memoryLimitKb(),
                        row.getGeneration(),
                        uuidGenerator.newId()))
                .orElse(null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
