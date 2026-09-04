package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.runtime.JudgeRunRequest;
import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SandboxOutputFormatter (D-form display seam)")
class SandboxOutputFormatterImplTest {

    private final SandboxOutputFormatter formatter =
            new SandboxOutputFormatterImpl((UuidGenerator) () -> "fixed-test-uuid");

    @Test
    void parseRuntimeMs_formats() {
        assertThat(formatter.parseRuntimeMs("42ms")).isEqualTo(42L);
        assertThat(formatter.parseRuntimeMs("  100ms  ")).isEqualTo(100L);
        assertThat(formatter.parseRuntimeMs("0.5s")).isEqualTo(500L);
        assertThat(formatter.parseRuntimeMs("3")).isEqualTo(3L);
        assertThat(formatter.parseRuntimeMs(null)).isEqualTo(0L);
        assertThat(formatter.parseRuntimeMs("garbage")).isEqualTo(0L);
    }

    @Test
    void sanitizeSandboxOutput_stripsInfraNoise() {
        String noisy = "answer\nOCI runtime error: foo\ndocker: bar\nreal output";
        assertThat(formatter.sanitizeSandboxOutput(noisy)).isEqualTo("answer\nreal output");
    }

    @Test
    void emptyResult_isSystemErrorEnvelope() {
        JudgeRunResponse result = formatter.emptyResult(42L, "user-1");
        assertThat(result.getVerdict()).isEqualTo("System Error");
        assertThat(result.getCases()).isEmpty();
        assertThat(result.getProblemId()).isEqualTo(42L);
    }

    @Test
    void buildCaseResult_runtimeFormatting() {
        JudgeRunRequest.TestCase testCase = singleCase();
        JudgeRunResponse.RunCaseResult withUs = formatter.buildCaseResult(
                testCase, "run-1", "user-1", "Accepted", 12L, "7", null,
                0.0, 2500L, 0L);
        assertThat(withUs.getRuntime()).isEqualTo("2.50ms");
        assertThat(withUs.getRuntimeUs()).isEqualTo(2500L);

        JudgeRunResponse.RunCaseResult legacyMs = formatter.buildCaseResult(
                testCase, "run-1", "user-1", "Accepted", 12L, "7", null,
                0.0, 0L, 0L);
        assertThat(legacyMs.getRuntime()).isEqualTo("12ms");
        assertThat(legacyMs.getRuntimeUs()).isNull();
    }

    private JudgeRunRequest.TestCase singleCase() {
        JudgeRunRequest.TestCase testCase = new JudgeRunRequest.TestCase();
        testCase.setId("pe-002-1");
        testCase.setLabel("Case 1");
        testCase.setOutput("[7,0,8]");

        JudgeRunRequest.Input input = new JudgeRunRequest.Input();
        input.setName("l1");
        input.setValue("[2,4,3]");
        testCase.setInputs(List.of(input));
        return testCase;
    }
}
