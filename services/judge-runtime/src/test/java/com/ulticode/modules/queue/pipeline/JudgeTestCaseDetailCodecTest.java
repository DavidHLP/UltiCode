package com.ulticode.modules.queue.pipeline;

import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.domain.submission.enums.CaseScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Judge test-case detail wire codec")
class JudgeTestCaseDetailCodecTest {

    @Test
    @DisplayName("serializes the entity-free detail with the persisted field names")
    void serializesWireShape() {
        JudgeRunResponse.RunCaseResult.InputParam input = JudgeRunResponse.RunCaseResult.InputParam.builder()
                .id("input-1")
                .label("n")
                .name("n")
                .value("2")
                .build();
        JudgeTestCaseDetail detail = new JudgeTestCaseDetail(
                "Accepted", 12, 1.5, "ok", "2", "2", List.of(input),
                "tc-1", CaseScope.SAMPLE);

        String json = JudgeTestCaseDetailCodec.toJson(List.of(detail));

        assertThat(json)
                .contains("\"status\":\"Accepted\"")
                .contains("\"caseId\":\"tc-1\"")
                .contains("\"caseScope\":\"SAMPLE\"")
                .contains("\"inputs\":[{\"id\":\"input-1\"");
    }

    @Test
    @DisplayName("empty details keep the legacy null payload convention")
    void emptyDetailsAreNull() {
        assertThat(JudgeTestCaseDetailCodec.toJson(null)).isNull();
        assertThat(JudgeTestCaseDetailCodec.toJson(List.of())).isNull();
    }
}
