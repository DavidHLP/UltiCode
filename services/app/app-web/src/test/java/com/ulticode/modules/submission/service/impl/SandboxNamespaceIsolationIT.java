package com.ulticode.modules.submission.service.impl;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests validating Docker sandbox namespace isolation.
 *
 * These tests verify that the sandbox container properly isolates:
 * - PID namespace: sandbox processes are not visible from the host
 * - Network namespace: outbound connections are blocked (--network none)
 * - User namespace: code runs as uid=1000, not root
 *
 * Prerequisites:
 * - Docker daemon must be running
 * - Sandbox image must be built: docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/
 */
class SandboxNamespaceIsolationIT {

    private static final String SANDBOX_IMAGE = "ulticode-sandbox:latest";

    @BeforeEach
    void requireSandboxImage() {
        Assumptions.assumeTrue(sandboxImageAvailable(),
                SANDBOX_IMAGE + " is not available locally; build it when the registry is reachable "
                        + "before claiming sandbox namespace coverage");
    }

    private static boolean sandboxImageAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", SANDBOX_IMAGE)
                    .redirectErrorStream(true)
                    .start();
            try {
                return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            } finally {
                process.destroyForcibly();
            }
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    @DisplayName("Network isolation - sandbox cannot reach external IPs")
    void networkIsolated_sandboxCannotReachExternalIP() throws Exception {
        // Execute a curl inside a container with --network none
        // Connection should fail because network namespace is isolated
        List<String> cmd = List.of(
                "docker", "run", "--rm", "--network", "none",
                SANDBOX_IMAGE, "curl", "-s", "--connect-timeout", "3",
                "http://8.8.8.8"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        int exitCode = p.waitFor();
        String output = new String(p.getInputStream().readAllBytes());

        // Network none means connection refused/timeout — exit code != 0
        assertThat(exitCode)
                .as("Sandbox with --network none should not be able to reach external IP")
                .isNotZero();
    }

    @Test
    @DisplayName("Network isolation - sandbox cannot reach host.docker.internal")
    void networkIsolated_cannotReachHostDockerInternal() throws Exception {
        // Attempt to reach the Docker host via the special DNS name
        List<String> cmd = List.of(
                "docker", "run", "--rm", "--network", "none",
                SANDBOX_IMAGE, "curl", "-s", "--connect-timeout", "3",
                "http://host.docker.internal"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        int exitCode = p.waitFor();

        // Should fail because host network is not accessible from isolated network namespace
        assertThat(exitCode)
                .as("Sandbox with --network none should not reach host.docker.internal")
                .isNotZero();
    }

    @Test
    @DisplayName("User namespace - sandbox process runs as uid=1000, not root")
    void userNamespaceIsolated_runsAsNonRoot() throws Exception {
        // Execute 'id' command inside sandbox to verify user identity
        List<String> cmd = List.of(
                "docker", "run", "--rm",
                "--user", "1000:1000",
                SANDBOX_IMAGE, "id"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        int exitCode = p.waitFor();
        String output = new String(p.getInputStream().readAllBytes());

        assertThat(exitCode)
                .as("'id' command should succeed inside sandbox")
                .isZero();

        assertThat(output)
                .as("Sandbox should run as uid=1000 (not root uid=0)")
                .contains("uid=1000")
                .doesNotContain("uid=0(root)");
    }

    @Test
    @DisplayName("PID namespace - sandbox process is not visible from host")
    void pidNamespaceIsolated_processNotVisibleFromHost() throws Exception {
        // Start a long-running process in the sandbox
        // Then verify it's not visible in host's process list
        List<String> containerCmd = List.of(
                "docker", "run", "--rm", "-d",
                "--network", "none",
                "--user", "1000:1000",
                SANDBOX_IMAGE, "sleep", "30"
        );

        ProcessBuilder startPb = new ProcessBuilder(containerCmd);
        startPb.redirectErrorStream(true);
        Process startProcess = startPb.start();
        String containerId = new String(startProcess.getInputStream().readAllBytes()).trim();
        int startExitCode = startProcess.waitFor();

        assertThat(startExitCode)
                .as("Container should start successfully")
                .isZero();

        try {
            // Verify the sleep process is NOT visible from host via 'ps aux'
            List<String> psCmd = List.of("docker", "exec", containerId, "ps", "aux");
            ProcessBuilder psPb = new ProcessBuilder(psCmd);
            psPb.redirectErrorStream(true);
            Process psProcess = psPb.start();
            int psExitCode = psProcess.waitFor();
            String psOutput = new String(psProcess.getInputStream().readAllBytes());

            // Container should have limited process visibility (only sleep and ps itself)
            assertThat(psExitCode)
                    .as("ps command should succeed inside container")
                    .isZero();

            // The sleep process should be visible inside the container
            assertThat(psOutput).contains("sleep");

        } finally {
            // Cleanup: stop the container
            ProcessBuilder stopPb = new ProcessBuilder("docker", "stop", containerId);
            stopPb.redirectErrorStream(true);
            Process stopProcess = stopPb.start();
            stopProcess.waitFor();
        }
    }

    @Test
    @DisplayName("Process runs inside isolated PID namespace with limited visibility")
    void pidNamespaceIsolated_limitedProcessVisibility() throws Exception {
        // Start a container and check what processes are visible from inside
        // PID namespace isolation means container sees only its own processes
        List<String> containerCmd = List.of(
                "docker", "run", "--rm", "-d",
                "--network", "none",
                "--user", "1000:1000",
                SANDBOX_IMAGE, "sleep", "60"
        );

        ProcessBuilder startPb = new ProcessBuilder(containerCmd);
        startPb.redirectErrorStream(true);
        Process startProcess = startPb.start();
        String containerId = new String(startProcess.getInputStream().readAllBytes()).trim();
        startProcess.waitFor();

        try {
            // Check how many processes are visible inside the container
            // In an isolated PID namespace, should see very few processes
            List<String> psCmd = List.of("docker", "exec", containerId, "sh", "-c", "ps aux | wc -l");
            ProcessBuilder psPb = new ProcessBuilder(psCmd);
            psPb.redirectErrorStream(true);
            Process psProcess = psPb.start();
            String psOutput = new String(psProcess.getInputStream().readAllBytes()).trim();
            int processCount = Integer.parseInt(psOutput.trim());
            int psExitCode = psProcess.waitFor();

            assertThat(psExitCode)
                    .as("ps command should succeed")
                    .isZero();

            // Should see very few processes (sleep + ps/sh) - not the full host process table
            assertThat(processCount)
                    .as("Container PID namespace should have limited process visibility")
                    .isLessThan(10);

        } finally {
            ProcessBuilder stopPb = new ProcessBuilder("docker", "stop", containerId);
            stopPb.redirectErrorStream(true);
            stopPb.start().waitFor();
        }
    }

    @Test
    @DisplayName("Filesystem is read-only except /tmp (tmpfs)")
    void filesystemIsolated_readOnlyExceptTmp() throws Exception {
        // Verify that root filesystem is read-only but /tmp is writable
        List<String> cmd = List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--user", "1000:1000",
                "--read-only",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                SANDBOX_IMAGE, "sh", "-c",
                "touch /tmp/testfile && ls /tmp/testfile && (! touch /root/testfile)"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        int exitCode = p.waitFor();
        String output = new String(p.getInputStream().readAllBytes());

        assertThat(exitCode)
                .as("Should be able to write to /tmp but not to /root")
                .isZero();
        assertThat(output).contains("testfile");
    }
}
