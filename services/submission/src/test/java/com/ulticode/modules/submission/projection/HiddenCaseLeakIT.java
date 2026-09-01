package com.ulticode.modules.submission.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-1: hidden case input/output/expected output must never appear in the
 * user-facing JSON emitted by the Submission owner projection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 hidden case leak guard (owner user-facing JSON)")
class HiddenCaseLeakIT {

    private static final String HIDDEN_SENTINEL = "HIDDEN_SECRET_SENTINEL_TOKEN_42";

    @Mock private SubmissionMapper submissionMapper;
    @Mock private com.ulticode.app.api.service.SubmissionUserReadPort userReadPort;
    @Mock private ProblemFactsPort problemFacts;

    private final ObjectMapper jackson = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("User-facing SubmissionVO JSON never contains hidden case input/output/expectedOutput")
    void hiddenCaseFieldsNotLeakedInJson() throws Exception {
        DefaultSubmissionProjection projection = projection();

        Submission submission = new Submission();
        submission.setId("sub-1");
        submission.setProblemId(100L);
        submission.setUserId("u-1");
        submission.setStatus("Wrong Answer");
        submission.setCreatedAt(java.time.LocalDateTime.now());
        submission.setTestDetails(Arrays.asList(
                detail("Accepted", CaseScope.SAMPLE, "tc-s-1",
                        "public out", "public exp", null),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1",
                        HIDDEN_SENTINEL + "_OUTPUT",
                        HIDDEN_SENTINEL + "_EXPECTED",
                        "diff: " + HIDDEN_SENTINEL + "_DETAIL_OK_TO_LEAK")
        ));

        String json = jackson.writeValueAsString(projection.toVO(submission));

        assertThat(json)
                .doesNotContain(HIDDEN_SENTINEL + "_OUTPUT")
                .doesNotContain(HIDDEN_SENTINEL + "_EXPECTED")
                .contains("\"tests\":[")
                .contains("\"status\":\"Accepted\"")
                .contains("errorDetail");
    }

    @Test
    @DisplayName("Multiple hidden cases and mixed legacy rows still redact user JSON")
    void multipleHiddenAndLegacyRowsRedact() throws Exception {
        DefaultSubmissionProjection projection = projection();

        Submission submission = new Submission();
        submission.setId("sub-2");
        submission.setProblemId(101L);
        submission.setUserId("u-2");
        submission.setStatus("Wrong Answer");
        submission.setCreatedAt(java.time.LocalDateTime.now());
        submission.setTestDetails(Arrays.asList(
                detail("Wrong Answer", null, null,
                        "legacy out", "legacy exp", "diff"),
                detail("Accepted", CaseScope.SAMPLE, "tc-s-1",
                        "public out", "public exp", null),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1",
                        HIDDEN_SENTINEL + "_A",
                        HIDDEN_SENTINEL + "_B",
                        "diff: " + HIDDEN_SENTINEL + "_C"),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-2",
                        HIDDEN_SENTINEL + "_D",
                        HIDDEN_SENTINEL + "_E",
                        "diff: " + HIDDEN_SENTINEL + "_F")
        ));

        String json = jackson.writeValueAsString(projection.toVO(submission));

        assertThat(json)
                .doesNotContain(HIDDEN_SENTINEL + "_A")
                .doesNotContain(HIDDEN_SENTINEL + "_B")
                .doesNotContain(HIDDEN_SENTINEL + "_D")
                .doesNotContain(HIDDEN_SENTINEL + "_E")
                .contains("legacy out");
    }

    private DefaultSubmissionProjection projection() {
        return new DefaultSubmissionProjection(submissionMapper, userReadPort, problemFacts, jackson);
    }

    private Submission.TestCaseDetail detail(String status, CaseScope scope, String caseId,
                                             String output, String expected, String error) {
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setStatus(status);
        detail.setCaseScope(scope);
        detail.setCaseId(caseId);
        detail.setOutput(output);
        detail.setExpectedOutput(expected);
        detail.setDetail(error);
        detail.setTime(50);
        detail.setMemory(10.0);
        return detail;
    }
}
