package com.ulticode.modules.submission.service.impl;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SandboxServiceImpl")
class SandboxServiceImplTest {

    private final SandboxServiceImpl sandboxService = new SandboxServiceImpl(
            new DockerSandboxConfig(
                    true,
                    "ulticode-sandbox:latest",
                    "128m",
                    "1.0",
                    30,
                    128,
                    "/tmp/seccomp",
                    Map.of()
            ),
            mock(CodeExecutionHelper.class)
    );

    @Test
    @DisplayName("Python batch command runs wrapper through python3")
    void buildBatchDockerCommand_python_usesPythonInterpreter() {
        List<String> command = sandboxService.buildBatchDockerCommand("python", "print('ok')");

        assertThat(command).endsWith("ulticode-sandbox:latest", "python3", "-c", "print('ok')");
    }

    @Test
    @DisplayName("JavaScript batch command runs wrapper through node")
    void buildBatchDockerCommand_javascript_usesNodeInterpreter() {
        List<String> command = sandboxService.buildBatchDockerCommand("javascript", "console.log('ok')");

        assertThat(command).endsWith("ulticode-sandbox:latest", "node", "-e", "console.log('ok')");
    }

    @Test
    @DisplayName("Default seccomp path resolves from backend working directory")
    void buildBatchDockerCommand_defaultSeccompPath_findsRepositoryDockerSandbox() {
        SandboxServiceImpl service = new SandboxServiceImpl(
                new DockerSandboxConfig(
                        true,
                        "ulticode-sandbox:latest",
                        "128m",
                        "1.0",
                        30,
                        128,
                        null,
                        Map.of()
                ),
                mock(CodeExecutionHelper.class)
        );

        List<String> command = service.buildBatchDockerCommand("python", "print('ok')");

        assertThat(command).contains("--volume");
        assertThat(command).anySatisfy(arg ->
                assertThat(arg).endsWith("/docker/sandbox:/seccomp-profile:ro"));
        assertThat(command).anySatisfy(arg ->
                assertThat(arg).endsWith("/docker/sandbox/seccomp-profile.json"));
    }

    @Test
    @DisplayName("Relative seccomp file config resolves from repository root")
    void buildBatchDockerCommand_relativeSeccompFile_findsRepositoryDockerSandbox() {
        SandboxServiceImpl service = new SandboxServiceImpl(
                new DockerSandboxConfig(
                        true,
                        "ulticode-sandbox:latest",
                        "128m",
                        "1.0",
                        30,
                        128,
                        "docker/sandbox/seccomp-profile.json",
                        Map.of()
                ),
                mock(CodeExecutionHelper.class)
        );

        List<String> command = service.buildBatchDockerCommand("python", "print('ok')");

        assertThat(command).anySatisfy(arg ->
                assertThat(arg).endsWith("/docker/sandbox/seccomp-profile.json"));
        assertThat(command).anySatisfy(arg ->
                assertThat(arg).endsWith("/docker/sandbox:/seccomp-profile:ro"));
    }
}
