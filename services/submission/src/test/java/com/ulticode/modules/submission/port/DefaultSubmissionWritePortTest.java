package com.ulticode.modules.submission.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxWriter;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.result.SubmissionResultOutboxWriter;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Submission owner writer decisions")
class DefaultSubmissionWritePortTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private SubmissionProjection projection;
    @Mock private SubmissionPerformanceStats performanceStats;
    @Mock private ContestSubmissionPort contestSubmissionPort;
    @Mock private JudgeOutboxMapper judgeOutboxMapper;
    @Mock private SubmissionResultOutboxWriter resultOutboxWriter;
    @Mock private SubmissionCreatedOutboxWriter createdOutboxWriter;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UuidGenerator uuidGenerator;

    private DefaultSubmissionWritePort writer;

    @BeforeEach
    void setUp() {
        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseJudgeOutbox(true);
        flags.getJudgeQueue().setUsePort(true);
        lenient().when(uuidGenerator.newId()).thenReturn("submission-1", "outbox-1");
        lenient().when(projection.toVO(any(Submission.class))).thenAnswer(invocation -> {
            Submission row = invocation.getArgument(0);
            SubmissionVO vo = new SubmissionVO();
            vo.setId(row.getId());
            vo.setLanguage(row.getLanguage());
            vo.setStatus(row.getStatus());
            return vo;
        });
        writer = new DefaultSubmissionWritePort(
                submissionMapper,
                new ObjectMapper().findAndRegisterModules(),
                projection,
                performanceStats,
                contestSubmissionPort,
                judgeOutboxMapper,
                flags,
                new SimpleMeterRegistry(),
                resultOutboxWriter,
                createdOutboxWriter,
                eventPublisher,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC),
                uuidGenerator);
    }

    @Test
    void blankUserId() {
        assertThatThrownBy(() -> writer.submit(" ", request("python"), facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void nullRequest() {
        assertThatThrownBy(() -> writer.submit("user-1", null, facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void blankCode() {
        CreateSubmissionDTO request = request("python");
        request.setCode(" ");
        assertThatThrownBy(() -> writer.submit("user-1", request, facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void blankLanguage() {
        assertThatThrownBy(() -> writer.submit("user-1", request(" "), facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void unsupportedLanguage() {
        assertThatThrownBy(() -> writer.submit("user-1", request("rust"), facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void missingFacts() {
        assertThatThrownBy(() -> writer.submit("user-1", request("python")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("facts snapshot");
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void mismatchedFacts() {
        assertThatThrownBy(() -> writer.submit("user-1", request("python"), facts("other", 101L)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> writer.submit("user-1", request("python"), facts("user-1", 999L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void contestIdRequired() {
        assertThatThrownBy(() -> writer.submitContest(
                "user-1", request("python"), facts("user-1", 101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Contest context");
        verifyNoInteractions(submissionMapper, judgeOutboxMapper);
    }

    @Test
    void normalizesLanguage() {
        SubmissionVO result = writer.submit(
                "user-1", request("PyThOn"), facts("user-1", 101L));

        ArgumentCaptor<Submission> row = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper).insert(row.capture());
        assertThat(row.getValue().getLanguage()).isEqualTo("python");
        assertThat(result.getLanguage()).isEqualTo("python");
    }

    @Test
    void ownerOutboxIsNotShadow() {
        writer.submit("user-1", request("python"), facts("user-1", 101L));

        ArgumentCaptor<JudgeOutboxRecord> outbox = ArgumentCaptor.forClass(JudgeOutboxRecord.class);
        verify(judgeOutboxMapper).insert(outbox.capture());
        assertThat(outbox.getValue().getIsShadow()).isFalse();
        assertThat(outbox.getValue().getGeneration()).isEqualTo(1L);
    }

    @Test
    void ownerDoesNotUseLegacyQueue() {
        assertThat(Arrays.stream(DefaultSubmissionWritePort.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType))
                .doesNotContain(JudgeEnqueuePort.class);
    }

    private static CreateSubmissionDTO request(String language) {
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        request.setProblemId(101L);
        request.setLanguage(language);
        request.setCode("print(1)");
        return request;
    }

    private static SubmissionFactsSnapshot facts(String userId, Long problemId) {
        return new SubmissionFactsSnapshot(
                userId,
                true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        problemId, "Two Sum", "two-sum", 2, 256, null),
                1L,
                SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
    }
}
