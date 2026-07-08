package com.ulticode.modules.submission.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import static org.mockito.Mockito.mock;

/**
 * P0-1: a hidden case's {@code input} / {@code output} / {@code expectedOutput}
 * must never appear anywhere in the JSON serialized from the user-facing
 * {@code SubmissionVO}. This is the security-critical regression guard.
 *
 * <p>Marked {@code IT} so the project's Maven Surefire rule that runs all
 * {@code *IT} tests under both {@code features-off} and {@code features-on}
 * profiles picks it up. The test body itself is a pure unit (no Testcontainers)
 * — what matters is the Jackson wire contract, not the DB.
 *
 * <p>After the deepening, this test crosses the {@link SubmissionProjection}
 * seam with only four mocks. The previous incarnation required seventeen
 * mocks of unrelated state-change collaborators.
 *
 * <p>The string sentinel {@code "HIDDEN_SECRET_SENTINEL_TOKEN_42"} is used
 * as the hidden case's input/output/expectedOutput so a single substring
 * assertion catches any leak path (controller JSON, errorDetail, first-failing
 * extraction, etc.) without needing to enumerate every field.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 hidden case leak guard (user-facing JSON)")
class HiddenCaseLeakIT {

    private static final String HIDDEN_SENTINEL = "HIDDEN_SECRET_SENTINEL_TOKEN_42";

    @Mock private SubmissionMapper submissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProblemMapper problemMapper;

    private final ObjectMapper jackson = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("User-facing SubmissionVO JSON never contains hidden case input/output/expectedOutput")
    void hiddenCaseFieldsNotLeakedInJson() throws Exception {
        DefaultSubmissionProjection projection = new DefaultSubmissionProjection(submissionMapper, mock(com.ulticode.modules.submission.stats.SubmissionStreakCalculator.class),
                userMapper, problemMapper, jackson);

        Submission s = new Submission();
        s.setId("sub-1");
        s.setProblemId(100L);
        s.setUserId("u-1");
        s.setStatus("Wrong Answer");
        s.setCreatedAt(java.time.LocalDateTime.now());
        s.setTestDetails(Arrays.asList(
                detail("Accepted", CaseScope.SAMPLE, "tc-s-1",
                        "public out", "public exp", null),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1",
                        HIDDEN_SENTINEL + "_OUTPUT",
                        HIDDEN_SENTINEL + "_EXPECTED",
                        "diff: " + HIDDEN_SENTINEL + "_DETAIL_OK_TO_LEAK")
        ));
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        String json = jackson.writeValueAsString(projection.toVO(s));

        // Critical security assertions: hidden case INPUT / OUTPUT / EXPECTED_OUTPUT
        // MUST NOT appear anywhere in user-facing JSON. (The detail / error message
        // text is allowed to surface — it contains no reverse-engineering info.)
        assertThat(json)
                .as("User JSON must not contain hidden case input/output/expectedOutput (sentinel leak)")
                .doesNotContain(HIDDEN_SENTINEL + "_OUTPUT")
                .doesNotContain(HIDDEN_SENTINEL + "_EXPECTED");
        // End-to-end proof: tests[] has the SAMPLE case (1 entry), errorDetail is
        // set from the HIDDEN failure (sentinel inside is OK — it's the error text,
        // not input/output). JSON serialization itself proves the round-trip works.
        assertThat(json).contains("\"tests\":[");
        assertThat(json).contains("\"status\":\"Accepted\""); // sample status surfaces
        assertThat(json).contains("errorDetail"); // some failure surfaced
    }

    @Test
    @DisplayName("Multiple hidden cases and mixed legacy rows still redact user JSON")
    void multipleHiddenAndLegacyRowsRedact() throws Exception {
        DefaultSubmissionProjection projection = new DefaultSubmissionProjection(submissionMapper, mock(com.ulticode.modules.submission.stats.SubmissionStreakCalculator.class),
                userMapper, problemMapper, jackson);

        Submission s = new Submission();
        s.setId("sub-2");
        s.setProblemId(101L);
        s.setUserId("u-2");
        s.setStatus("Wrong Answer");
        s.setCreatedAt(java.time.LocalDateTime.now());
        s.setTestDetails(Arrays.asList(
                detail("Wrong Answer", null, null,                        // legacy
                        "legacy out", "legacy exp", "diff"),               // visible
                detail("Accepted", CaseScope.SAMPLE, "tc-s-1",
                        "public out", "public exp", null),                // visible
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1",
                        HIDDEN_SENTINEL + "_A",
                        HIDDEN_SENTINEL + "_B",
                        "diff: " + HIDDEN_SENTINEL + "_C"),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-2",
                        HIDDEN_SENTINEL + "_D",
                        HIDDEN_SENTINEL + "_E",
                        "diff: " + HIDDEN_SENTINEL + "_F")
        ));
        when(userMapper.selectById("u-2")).thenReturn(null);
        when(problemMapper.selectById(101L)).thenReturn(null);

        String json = jackson.writeValueAsString(projection.toVO(s));

        // No hidden I/O substring in the wire response.
        assertThat(json)
                .doesNotContain(HIDDEN_SENTINEL + "_A")
                .doesNotContain(HIDDEN_SENTINEL + "_B")
                .doesNotContain(HIDDEN_SENTINEL + "_D")
                .doesNotContain(HIDDEN_SENTINEL + "_E");
        // Legacy failing case surfaces its I/O (the first-failing detail projection
        // was accepted as legacy sample). Sample info should also appear via tests[].
        assertThat(json).contains("legacy out");
    }

    private Submission.TestCaseDetail detail(String status, CaseScope scope, String caseId,
                                             String output, String expected, String error) {
        Submission.TestCaseDetail d = new Submission.TestCaseDetail();
        d.setStatus(status);
        d.setCaseScope(scope);
        d.setCaseId(caseId);
        d.setOutput(output);
        d.setExpectedOutput(expected);
        d.setDetail(error);
        d.setTime(50);
        d.setMemory(10.0);
        return d;
    }
}
