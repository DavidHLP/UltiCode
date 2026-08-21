package com.ulticode.submission.api.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeQueue;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeQueueContractShapeTest {

    @Test
    void queueContractLivesInProviderOwnedNamespace() {
        assertThat(List.of(
                JudgeJobEnvelope.class, JudgeJobHandle.class,
                JudgeQueue.class, JudgeStreamKeys.class))
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .isEqualTo("com.ulticode.submission.api.queue"));
    }

    @Test
    void envelopeVersionsAndJsonShapeRemainCompatible() throws Exception {
        JudgeJobEnvelope v1 = new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_1, "job-1", "submission-1", "42", "user-1",
                "java", "class Main {}", 1000, 1024, null, null);
        JudgeJobEnvelope v2 = new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2, "job-2", "submission-1", "42", "user-1",
                "java", "class Main {}", 1000, 1024, 7L, "attempt-1");
        ObjectMapper mapper = new ObjectMapper();

        String v1Json = mapper.writeValueAsString(v1);
        String v2Json = mapper.writeValueAsString(v2);

        assertThat(v1.isFenceAware()).isFalse();
        assertThat(v2.isFenceAware()).isTrue();
        assertThat(v1Json).doesNotContain("generation", "attemptId");
        assertThat(v2Json).contains("\"generation\":7", "\"attemptId\":\"attempt-1\"");
        assertThat(mapper.readValue(v2Json, JudgeJobEnvelope.class)).isEqualTo(v2);
    }

    @Test
    void queueMethodsExposeNoImplementationTypes() {
        for (Method method : JudgeQueue.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName())
                    .doesNotStartWith("com.ulticode.modules.");
            assertThat(java.util.Arrays.stream(method.getParameterTypes())
                    .map(Class::getName).toList())
                    .noneMatch(type -> type.startsWith("com.ulticode.modules."));
        }
    }

    @Test
    void streamKeysAreStableAndShared() {
        assertThat(JudgeStreamKeys.JUDGE_STREAM_KEY).isEqualTo("judge:{judge-stream}:stream");
        assertThat(JudgeStreamKeys.JUDGE_STREAM_GROUP).isEqualTo("judge-workers");
        assertThat(JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS).isEqualTo(1_800_000L);
    }
}
