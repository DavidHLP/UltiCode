package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeExecutionHelperImpl")
class CodeExecutionHelperImplTest {

    private final CodeExecutionHelperImpl helper = new CodeExecutionHelperImpl(new ObjectMapper());

    @Test
    @DisplayName("Python batch wrapper runs Solution.addTwoNumbers with list-node inputs")
    void buildPythonBatchWrapper_solutionClassListNodeInputs_returnsSerializedList() throws Exception {
        RunSubmissionDTO.RunTestCase testCase = createTestCase();
        String script = helper.buildPythonBatchWrapper("""
                class Solution:
                    def addTwoNumbers(self, l1, l2):
                        dummy = ListNode(0)
                        cur, carry = dummy, 0
                        while l1 or l2 or carry:
                            v1 = l1.val if l1 else 0
                            v2 = l2.val if l2 else 0
                            s = v1 + v2 + carry
                            cur.next = ListNode(s % 10)
                            carry = s // 10
                            cur = cur.next
                            l1 = l1.next if l1 else None
                            l2 = l2.next if l2 else None
                        return dummy.next
                """, List.of(testCase));

        String stdout = runPython(script, helper.buildBatchInputsJson(List.of(testCase)));

        List<RunResultDTO.RunCaseResult> results = helper.parseBatchResults(
                stdout, List.of(testCase), "run-1", null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOutput()).isEqualTo("[7, 0, 8]");
        assertThat(results.get(0).getStatus()).isEqualTo("Accepted");
    }

    private RunSubmissionDTO.RunTestCase createTestCase() {
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

    private String runPython(String script, String input) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("python3", "-c", script).start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(process.exitValue()).as(stderr).isZero();
        return stdout;
    }
}
