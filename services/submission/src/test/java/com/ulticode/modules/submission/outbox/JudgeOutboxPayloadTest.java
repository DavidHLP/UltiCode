package com.ulticode.modules.submission.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.entity.Submission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Judge outbox payload")
class JudgeOutboxPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void producerPayloadKeepsThePersistedKeySetAndOrder() {
        JudgeOutboxPayload payload = JudgeOutboxPayload.forSubmission(submissionFixture(), "101", 2L);

        assertThat(payload.toMap()).containsExactly(
                Map.entry("submissionId", "sub-1"),
                Map.entry("problemId", "101"),
                Map.entry("userId", "user-1"),
                Map.entry("language", "java"),
                Map.entry("code", "class Main {}"),
                Map.entry("generation", 2L));
    }

    @Test
    void decodesMapPayloadAndLegacyStringFormWithCoercion() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("problemId", 101);
        values.put("userId", "user-1");
        values.put("language", "java");
        values.put("code", "class Main {}");
        values.put("timeLimitMs", "1500");
        values.put("memoryLimitKb", 262144);

        Optional<JudgeOutboxPayload> fromMap = JudgeOutboxPayload.fromLegacy(values, objectMapper);
        Optional<JudgeOutboxPayload> fromString =
                JudgeOutboxPayload.fromLegacy(objectMapper.writeValueAsString(values), objectMapper);

        assertThat(fromMap).isPresent();
        assertThat(fromMap.get().problemId()).isEqualTo("101");
        assertThat(fromMap.get().timeLimitMs()).isEqualTo(1500);
        assertThat(fromMap.get().memoryLimitKb()).isEqualTo(262144);
        assertThat(fromString).isPresent();
        assertThat(fromString.get().code()).isEqualTo("class Main {}");
    }

    @Test
    void rejectsPayloadsMissingRequiredFields() {
        Map<String, Object> values = new HashMap<>();
        values.put("problemId", "101");
        values.put("code", "class Main {}");

        assertThat(JudgeOutboxPayload.fromLegacy(values, objectMapper)).isEmpty();
        assertThat(JudgeOutboxPayload.fromLegacy("{not-json", objectMapper)).isEmpty();
        assertThat(JudgeOutboxPayload.fromLegacy(null, objectMapper)).isEmpty();
    }

    @Test
    void invalidOptionalNumbersFallBackToAbsent() {
        Map<String, Object> values = new HashMap<>();
        values.put("problemId", "101");
        values.put("userId", "user-1");
        values.put("language", "java");
        values.put("code", "class Main {}");
        values.put("timeLimitMs", "not-a-number");
        values.put("memoryLimitKb", "not-a-number");

        Optional<JudgeOutboxPayload> payload = JudgeOutboxPayload.fromLegacy(values, objectMapper);

        assertThat(payload).isPresent();
        assertThat(payload.get().timeLimitMs()).isNull();
        assertThat(payload.get().memoryLimitKb()).isNull();
    }

    private static Submission submissionFixture() {
        Submission submission = new Submission();
        submission.setId("sub-1");
        submission.setUserId("user-1");
        submission.setLanguage("java");
        submission.setCode("class Main {}");
        return submission;
    }
}
