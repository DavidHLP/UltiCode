package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionService")
class CodeExecutionServiceTest {

    @Mock
    private DockerSandboxConfig sandboxConfig;

    private CodeExecutionService codeExecutionService;

    @BeforeEach
    void setUp() {
        codeExecutionService = new CodeExecutionService(sandboxConfig);
    }

    private RunSubmissionDTO.RunTestCase createTestCase(String id, String output) {
        RunSubmissionDTO.RunTestCase tc = new RunSubmissionDTO.RunTestCase();
        tc.setId(id);
        tc.setOutput(output);
        return tc;
    }

    private RunSubmissionDTO createRequest(String language, String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage(language);
        request.setCode(code);
        request.setTestCases(testCases);
        return request;
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("unsupported language throws SUBMISSION_LANGUAGE_UNSUPPORTED")
        void execute_unsupportedLanguage_throwsException() {
            RunSubmissionDTO request = createRequest("rust", "fn main() {}", List.of(createTestCase("tc-1", "expected")));

            assertThatThrownBy(() -> codeExecutionService.execute(request, 1L, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED));
        }

        @Test
        @DisplayName("sandbox disabled throws SANDBOX_ERROR")
        void execute_sandboxDisabled_throwsException() {
            when(sandboxConfig.enabled()).thenReturn(false);
            RunSubmissionDTO request = createRequest("java", "class Main {}", List.of(createTestCase("tc-1", "expected")));

            assertThatThrownBy(() -> codeExecutionService.execute(request, 1L, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SANDBOX_ERROR));
        }

        @Test
        @DisplayName("empty test cases returns accepted result with zero cases")
        void execute_emptyTestCases_returnsEmptyResult() {
            RunSubmissionDTO request = createRequest("python", "print('hello')", List.of());

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result).isNotNull();
            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getPassedCases()).isEqualTo(0);
            assertThat(result.getTotalCases()).isEqualTo(0);
            assertThat(result.getCases()).isEmpty();
        }

        @Test
        @DisplayName("null test cases returns accepted result with zero cases")
        void execute_nullTestCases_returnsEmptyResult() {
            RunSubmissionDTO request = new RunSubmissionDTO();
            request.setLanguage("python");
            request.setCode("print('hello')");
            request.setTestCases(null);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result).isNotNull();
            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getTotalCases()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("buildDockerCommand()")
    class BuildDockerCommand {

        private Method buildDockerCommandMethod;

        @BeforeEach
        void setUpReflection() throws Exception {
            buildDockerCommandMethod = CodeExecutionService.class.getDeclaredMethod(
                    "buildDockerCommand", String.class, String.class);
            buildDockerCommandMethod.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        private List<String> invokeBuildDockerCommand(String language, String code) throws Exception {
            return (List<String>) buildDockerCommandMethod.invoke(codeExecutionService, language, code);
        }

        @BeforeEach
        void configureSandboxDefaults() {
            when(sandboxConfig.memory()).thenReturn("128m");
            when(sandboxConfig.cpus()).thenReturn("1");
            when(sandboxConfig.pidsLimit()).thenReturn(64);
            when(sandboxConfig.seccompProfilePath()).thenReturn("/etc/seccomp/profile.json");
            when(sandboxConfig.image()).thenReturn("ulticode-sandbox:latest");
        }

        @Test
        @DisplayName("java command includes base64 compile-and-run wrapper")
        void buildDockerCommand_java_includesJavaWrapper() throws Exception {
            List<String> command = invokeBuildDockerCommand("java", "public class Solution { }");

            assertThat(command).contains("docker", "run", "--rm", "-i");
            assertThat(command).contains("ulticode-sandbox:latest");
            assertThat(command).contains("sh", "-c");
            assertThat(command.stream().anyMatch(s -> s.contains("javac") && s.contains("java -cp /tmp Main")))
                    .isTrue();
        }

        @Test
        @DisplayName("python command includes python3 -c wrapper")
        void buildDockerCommand_python_includesPythonWrapper() throws Exception {
            List<String> command = invokeBuildDockerCommand("python", "def solution(): pass");

            assertThat(command).contains("python3", "-c");
            assertThat(command).contains("ulticode-sandbox:latest");
        }

        @Test
        @DisplayName("javascript command includes node -e wrapper")
        void buildDockerCommand_javascript_includesNodeWrapper() throws Exception {
            List<String> command = invokeBuildDockerCommand("javascript", "function solution() {}");

            assertThat(command).contains("node", "-e");
            assertThat(command).contains("ulticode-sandbox:latest");
        }

        @Test
        @DisplayName("c command includes gcc compile step")
        void buildDockerCommand_c_includesGccCompile() throws Exception {
            List<String> command = invokeBuildDockerCommand("c", "#include <stdio.h>");

            assertThat(command).contains("sh", "-c");
            assertThat(command.stream().anyMatch(s -> s.contains("gcc") && s.contains("/tmp/solution.c")))
                    .isTrue();
            assertThat(command).contains("ulticode-sandbox:latest");
        }

        @Test
        @DisplayName("cpp command includes g++ compile step")
        void buildDockerCommand_cpp_includesGppCompile() throws Exception {
            List<String> command = invokeBuildDockerCommand("cpp", "#include <iostream>");

            assertThat(command).contains("sh", "-c");
            assertThat(command.stream().anyMatch(s -> s.contains("g++") && s.contains("/tmp/solution.cpp")))
                    .isTrue();
            assertThat(command).contains("ulticode-sandbox:latest");
        }

        @Test
        @DisplayName("all commands include Docker security flags")
        void buildDockerCommand_allIncludesSecurityFlags() throws Exception {
            List<String> command = invokeBuildDockerCommand("python", "print('hello')");

            assertThat(command).contains("--cap-drop", "ALL");
            assertThat(command).contains("--network", "none");
            assertThat(command).contains("--read-only");
            assertThat(command).contains("--user", "1000:1000");
            assertThat(command).contains("--security-opt", "no-new-privileges:true");
            assertThat(command.stream().anyMatch(s -> s.contains("seccomp=")))
                    .isTrue();
            assertThat(command).contains("--memory", "128m");
            assertThat(command).contains("--cpus", "1");
            assertThat(command).contains("--pids-limit", "64");
            assertThat(command).contains("--ulimit", "nofile=128:128");
            assertThat(command).contains("--tmpfs", "/tmp:rw,exec,size=64m");
        }
    }
}
