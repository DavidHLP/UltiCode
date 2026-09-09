package com.ulticode.modules.queue.outbox.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgeJobEnvelopeTranslatorTest {

    private final UuidGenerator uuidGenerator = mock(UuidGenerator.class);
    private final JudgeJobEnvelopeTranslator translator = new JudgeJobEnvelopeTranslator(
            new ObjectMapper(), uuidGenerator);

    @Test
    void translatesRequiredFieldsAndDefaults() {
        when(uuidGenerator.newId()).thenReturn("attempt-1");
        JudgeOutboxRecord row = row(Map.of(
                "problemId", "101",
                "userId", "user-1",
                "language", "java",
                "code", "class Main {}"));

        JudgeJobEnvelope envelope = translator.translate(row);

        assertThat(envelope).isNotNull();
        assertThat(envelope.version()).isEqualTo(JudgeJobEnvelope.VERSION_2);
        assertThat(envelope.attemptId()).isEqualTo("attempt-1");
        assertThat(envelope.timeLimitMs()).isEqualTo(2000);
        assertThat(envelope.memoryLimitKb()).isEqualTo(256 * 1024);
    }

    @Test
    void rejectsRowsMissingRequiredFields() {
        JudgeOutboxRecord row = row(Map.of(
                "problemId", "101",
                "userId", "user-1"));

        assertThat(translator.translate(row)).isNull();
    }

    private static JudgeOutboxRecord row(Map<String, Object> payload) {
        JudgeOutboxRecord row = new JudgeOutboxRecord();
        row.setId("row-1");
        row.setSubmissionId("submission-1");
        row.setGeneration(3L);
        row.setPayload(payload);
        return row;
    }
}
