package com.ulticode.modules.submission.codec;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.modules.queue.pipeline.JudgeTestCaseDetail;
import com.ulticode.modules.queue.pipeline.JudgeTestCaseDetailCodec;
import com.ulticode.submission.api.codec.TestCaseDetailCodec;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Submission detail compatibility at the App boundary")
class SubmissionDetailWireCompatibilityTest {

    @Test
    @DisplayName("accepts judge-runtime codec output without changing the persisted shape")
    void acceptsJudgeWireDetails() {
        // Serialize through the production judge-runtime codec so field-name
        // drift between JudgeTestCaseDetailCodec and TestCaseDetailCodec
        // fails this test instead of corrupting persisted details.
        RunResultDTO.RunCaseResult.InputParam input = RunResultDTO.RunCaseResult.InputParam.builder()
                .id("input-1")
                .label("n")
                .name("n")
                .value("2")
                .build();
        JudgeTestCaseDetail detail = new JudgeTestCaseDetail(
                "Accepted", 12, 1.5, "ok", "2", "2", List.of(input),
                "tc-1", CaseScope.SAMPLE);
        String json = JudgeTestCaseDetailCodec.toJson(List.of(detail));

        var details = TestCaseDetailCodec.fromJson(json);

        assertThat(details).hasSize(1);
        SubmissionTestCaseDetailDTO persisted = details.get(0);
        assertThat(persisted.status()).isEqualTo("Accepted");
        assertThat(persisted.caseId()).isEqualTo("tc-1");
        assertThat(persisted.caseScope()).isEqualTo(CaseScope.SAMPLE);
        assertThat(persisted.output()).isEqualTo("2");
        assertThat(persisted.expectedOutput()).isEqualTo("2");
        assertThat(persisted.inputs()).hasSize(1);
        assertThat(persisted.inputs().get(0).id()).isEqualTo("input-1");
    }

    @Test
    @DisplayName("continues accepting legacy details without caseId or caseScope")
    void acceptsLegacyWireDetails() {
        var details = TestCaseDetailCodec.fromJson("[{\"status\":\"Accepted\",\"time\":10}]");

        assertThat(details).hasSize(1);
        assertThat(details.get(0).caseId()).isNull();
        assertThat(details.get(0).caseScope()).isNull();
    }
}
