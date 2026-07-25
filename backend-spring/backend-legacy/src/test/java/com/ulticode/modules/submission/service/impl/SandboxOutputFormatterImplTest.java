package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the display/DTO seam directly (C05 facade collapse). The D-form
 * protocol coverage lives in {@code DFormEnvelopeContractTest}; this file
 * owns the formatter-only behavior that used to be verified through the
 * deleted {@code CodeExecutionHelper} facade.
 */
@DisplayName("SandboxOutputFormatter (D-form display seam)")
class SandboxOutputFormatterImplTest {

    private final SandboxOutputFormatter formatter = new SandboxOutputFormatterImpl(new FixedUuidGenerator());

    @Test
    @DisplayName("parseRuntimeMs handles 'ms' suffix, 's' suffix, and bare numbers")
    void parseRuntimeMs_formats() {
        assertThat(formatter.parseRuntimeMs("42ms")).isEqualTo(42L);
        assertThat(formatter.parseRuntimeMs("  100ms  ")).isEqualTo(100L);
        assertThat(formatter.parseRuntimeMs("0.5s")).isEqualTo(500L);
        assertThat(formatter.parseRuntimeMs("3")).isEqualTo(3L);
        assertThat(formatter.parseRuntimeMs(null)).isEqualTo(0L);
        assertThat(formatter.parseRuntimeMs("garbage")).isEqualTo(0L);
    }

    @Test
    @DisplayName("sanitizeSandboxOutput returns 'Runtime error' for null input")
    void sanitizeSandboxOutput_nullInput() {
        assertThat(formatter.sanitizeSandboxOutput(null)).isEqualTo("Runtime error");
    }

    @Test
    @DisplayName("sanitizeSandboxOutput strips docker/OCI runtime infrastructure lines")
    void sanitizeSandboxOutput_stripsInfraNoise() {
        String noisy = "answer\nOCI runtime error: foo\ndocker: bar\nreal output";
        assertThat(formatter.sanitizeSandboxOutput(noisy)).isEqualTo("answer\nreal output");
    }

    @Test
    @DisplayName("emptyResult is a 'System Error' envelope with no cases")
    void emptyResult_isSystemErrorEnvelope() {
        RunResultDTO r = formatter.emptyResult(42L, "user-1");
        assertThat(r.getVerdict()).isEqualTo("System Error");
        assertThat(r.getCases()).isEmpty();
        assertThat(r.getProblemId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("buildCaseResult formats runtime from precise microseconds when present, else legacy ms")
    void buildCaseResult_runtimeFormatting() {
        RunSubmissionDTO.RunTestCase tc = singleCase();
        RunResultDTO.RunCaseResult withUs = formatter.buildCaseResult(tc, "run-1", "user-1",
                "Accepted", 12L, "7", null, 0.0, 2500L, 0L);
        assertThat(withUs.getRuntime()).isEqualTo("2.50ms");
        assertThat(withUs.getRuntimeUs()).isEqualTo(2500L);

        RunResultDTO.RunCaseResult legacyMs = formatter.buildCaseResult(tc, "run-1", "user-1",
                "Accepted", 12L, "7", null, 0.0, 0L, 0L);
        assertThat(legacyMs.getRuntime()).isEqualTo("12ms");
        assertThat(legacyMs.getRuntimeUs()).isNull();
    }

    private RunSubmissionDTO.RunTestCase singleCase() {
        RunSubmissionDTO.RunTestCase testCase = new RunSubmissionDTO.RunTestCase();
        testCase.setId("pe-002-1");
        testCase.setLabel("Case 1");
        testCase.setOutput("[7,0,8]");

        RunSubmissionDTO.RunInput l1 = new RunSubmissionDTO.RunInput();
        l1.setName("l1");
        l1.setValue("[2,4,3]");

        testCase.setInputs(List.of(l1));
        return testCase;
    }
}
