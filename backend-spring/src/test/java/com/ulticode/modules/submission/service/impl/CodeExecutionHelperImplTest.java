package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeExecutionHelperImpl (D-form)")
class CodeExecutionHelperImplTest {

    private final SandboxOutputFormatter formatter = new SandboxOutputFormatterImpl(new FixedUuidGenerator());
    private final DFormEnvelopeCodec codec =
        new DFormEnvelopeCodecImpl(new ObjectMapper(), formatter);
    private final CodeExecutionHelperImpl helper = new CodeExecutionHelperImpl(codec, formatter);

    // ── buildDInputsJson / buildDBatchInputsJson ────────────────────────────

    @Test
    @DisplayName("buildDInputsJson wraps a single test case in the harness input.json schema")
    void buildDInputsJson_singleCase() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        String json = helper.buildDInputsJson(tc, 1_000L, 0L);

        // snake_case keys, per_case_timeout_ms at top, cases[].inputs[]
        assertThat(json).contains("\"per_case_timeout_ms\":1000");
        assertThat(json).contains("\"cases\":[");
        assertThat(json).contains("\"case_id\":\"pe-002-1\"");
        assertThat(json).contains("\"expected_output\":\"[7,0,8]\"");
        assertThat(json).contains("\"name\":\"l1\"");
        assertThat(json).contains("\"value\":\"[2,4,3]\"");
        // no type field on RunInput yet
        assertThat(json).doesNotContain("\"type\"");
    }

    @Test
    @DisplayName("buildDInputsJson forwards the RunInput.type field when set and supported")
    void buildDInputsJson_typeFieldForwarded() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        tc.getInputs().get(0).setType("ListNode");
        tc.getInputs().get(1).setType("ListNode[]");
        String json = helper.buildDInputsJson(tc, 1_000L, 0L);
        assertThat(json).contains("\"type\":\"ListNode\"");
        assertThat(json).contains("\"type\":\"ListNode[]\"");
    }

    @Test
    @DisplayName("buildDInputsJson drops unsupported type silently (defense in depth)")
    void buildDInputsJson_unsupportedTypeDropped() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        tc.getInputs().get(0).setType("NotARealType");
        String json = helper.buildDInputsJson(tc, 1_000L, 0L);
        assertThat(json).doesNotContain("NotARealType");
        // but the field is omitted cleanly — no junk like "type":null
        assertThat(json).doesNotContain("\"type\":null");
    }

    @Test
    @DisplayName("buildDBatchInputsJson emits one cases[] entry per test case in order")
    void buildDBatchInputsJson_multipleCases() throws Exception {
        RunSubmissionDTO.RunTestCase tc1 = singleCase();
        RunSubmissionDTO.RunTestCase tc2 = singleCase();
        tc2.setId("pe-002-2");
        String json = helper.buildDBatchInputsJson(List.of(tc1, tc2), 2_000L, 0L);
        assertThat(json).contains("\"per_case_timeout_ms\":2000");
        // Both case_ids present, in input order
        int idx1 = json.indexOf("\"case_id\":\"pe-002-1\"");
        int idx2 = json.indexOf("\"case_id\":\"pe-002-2\"");
        assertThat(idx1).isGreaterThan(0);
        assertThat(idx2).isGreaterThan(idx1);
    }

    // ── parseDEnvelope ───────────────────────────────────────────────────────

    @Test
    @DisplayName("parseDEnvelope returns one Accepted result per case for a clean envelope")
    void parseDEnvelope_happyPath() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        String envelope = """
                {
                  "harness_version":"1.0",
                  "language":"java",
                  "exit_code":0,
                  "total_elapsed_ms":42,
                  "results":[
                    {"case_id":"pe-002-1","label":"Case 1","elapsed_ms":2,"status":"Accepted","result":7,"interrupted":false}
                  ]
                }
                """;
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope(envelope, List.of(tc), "run-1", "user-1");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getStatus()).isEqualTo("Accepted");
        assertThat(out.get(0).getOutput()).isEqualTo("7");
        assertThat(out.get(0).getRuntime()).isEqualTo("2ms");
    }

    @Test
    @DisplayName("parseDEnvelope surfaces per-case error as Runtime Error")
    void parseDEnvelope_perCaseError() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        String envelope = """
                {
                  "exit_code":0,
                  "results":[
                    {"case_id":"pe-002-1","label":"Case 1","elapsed_ms":5,
                     "status":"Runtime Error",
                     "error":{"type":"java.lang.NullPointerException",
                             "message":"user code blew up",
                             "stack":["Solution.java:14 in bomb"]}}
                  ]
                }
                """;
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope(envelope, List.of(tc), "run-1", "user-1");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getStatus()).isEqualTo("Runtime Error");
        assertThat(out.get(0).getDetail())
                .contains("[java.lang.NullPointerException]")
                .contains("user code blew up");
    }

    @Test
    @DisplayName("parseDEnvelope returns Runtime Error for every case when exit_code != 0 (harness panic)")
    void parseDEnvelope_harnessPanic() throws Exception {
        RunSubmissionDTO.RunTestCase tc1 = singleCase();
        RunSubmissionDTO.RunTestCase tc2 = singleCase();
        tc2.setId("pe-002-2");
        String envelope = """
                {"exit_code":2,"results":[]}
                """;
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope(envelope, List.of(tc1, tc2), "run-1", null);
        assertThat(out).hasSize(2);
        assertThat(out).allMatch(r -> "Runtime Error".equals(r.getStatus()));
        assertThat(out).allMatch(r -> r.getDetail().contains("D-form harness panic"));
    }

    @Test
    @DisplayName("parseDEnvelope returns Runtime Error for every case when stdout is empty")
    void parseDEnvelope_emptyStdout() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope("", List.of(tc), "run-1", "user-1");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getStatus()).isEqualTo("Runtime Error");
        assertThat(out.get(0).getDetail()).contains("no envelope");
    }

    @Test
    @DisplayName("parseDEnvelope returns Runtime Error for every case when stdout is unparseable JSON")
    void parseDEnvelope_unparseableJson() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope("not json at all", List.of(tc), "run-1", "user-1");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getStatus()).isEqualTo("Runtime Error");
        assertThat(out.get(0).getDetail()).contains("envelope unparseable");
    }

    @Test
    @DisplayName("parseDEnvelope maps a wrong-answer verdict to Wrong Answer with output")
    void parseDEnvelope_wrongAnswer() throws Exception {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        String envelope = """
                {"exit_code":0,"results":[
                  {"case_id":"pe-002-1","label":"Case 1","elapsed_ms":3,
                   "status":"Wrong Answer","result":"[9,9,9]"}
                ]}
                """;
        List<RunResultDTO.RunCaseResult> out = helper.parseDEnvelope(envelope, List.of(tc), "run-1", null);
        assertThat(out.get(0).getStatus()).isEqualTo("Wrong Answer");
        assertThat(out.get(0).getOutput()).isEqualTo("[9,9,9]");
    }

    // ── utility helpers (kept from Form A, regression) ───────────────────────

    @Test
    @DisplayName("parseRuntimeMs handles 'ms' suffix, 's' suffix, and bare numbers")
    void parseRuntimeMs_formats() {
        assertThat(helper.parseRuntimeMs("42ms")).isEqualTo(42L);
        assertThat(helper.parseRuntimeMs("  100ms  ")).isEqualTo(100L);
        assertThat(helper.parseRuntimeMs("0.5s")).isEqualTo(500L);
        assertThat(helper.parseRuntimeMs("3")).isEqualTo(3L);
        assertThat(helper.parseRuntimeMs(null)).isEqualTo(0L);
        assertThat(helper.parseRuntimeMs("garbage")).isEqualTo(0L);
    }

    @Test
    @DisplayName("sanitizeSandboxOutput returns 'Runtime error' for null input")
    void sanitizeSandboxOutput_nullInput() {
        assertThat(helper.sanitizeSandboxOutput(null)).isEqualTo("Runtime error");
    }

    @Test
    @DisplayName("emptyResult is a 'System Error' envelope with no cases")
    void emptyResult_isSystemErrorEnvelope() {
        RunResultDTO r = helper.emptyResult(42L, "user-1");
        assertThat(r.getVerdict()).isEqualTo("System Error");
        assertThat(r.getCases()).isEmpty();
        assertThat(r.getProblemId()).isEqualTo(42L);
    }

    private RunSubmissionDTO.RunTestCase singleCase() {
        RunSubmissionDTO.RunTestCase testCase = new RunSubmissionDTO.RunTestCase();
        testCase.setId("pe-002-1");
        testCase.setLabel("Case 1");
        testCase.setOutput("[7,0,8]");

        RunSubmissionDTO.RunInput l1 = new RunSubmissionDTO.RunInput();
        l1.setName("l1");
        l1.setValue("[2,4,3]");

        RunSubmissionDTO.RunInput l2 = new RunSubmissionDTO.RunInput();
        l2.setName("l2");
        l2.setValue("[5,6,4]");

        testCase.setInputs(List.of(l1, l2));
        return testCase;
    }
}
