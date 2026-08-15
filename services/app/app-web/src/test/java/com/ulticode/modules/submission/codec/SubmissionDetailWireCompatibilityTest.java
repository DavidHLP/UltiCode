package com.ulticode.modules.submission.codec;

import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.modules.submission.entity.Submission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Submission detail compatibility at the App boundary")
class SubmissionDetailWireCompatibilityTest {

    @Test
    @DisplayName("accepts entity-free judge details without changing the persisted shape")
    void acceptsJudgeWireDetails() {
        String json = "[{\"status\":\"Accepted\",\"time\":12,\"memory\":1.5,"
                + "\"detail\":\"ok\",\"output\":\"2\",\"expectedOutput\":\"2\","
                + "\"inputs\":[],\"caseId\":\"tc-1\",\"caseScope\":\"SAMPLE\"}]";

        var details = TestCaseDetailCodec.fromJson(json);

        assertThat(details).hasSize(1);
        Submission.TestCaseDetail detail = details.get(0);
        assertThat(detail.getStatus()).isEqualTo("Accepted");
        assertThat(detail.getCaseId()).isEqualTo("tc-1");
        assertThat(detail.getCaseScope()).isEqualTo(CaseScope.SAMPLE);
    }

    @Test
    @DisplayName("continues accepting legacy details without caseId or caseScope")
    void acceptsLegacyWireDetails() {
        var details = TestCaseDetailCodec.fromJson("[{\"status\":\"Accepted\",\"time\":10}]");

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getCaseId()).isNull();
        assertThat(details.get(0).getCaseScope()).isNull();
    }
}
