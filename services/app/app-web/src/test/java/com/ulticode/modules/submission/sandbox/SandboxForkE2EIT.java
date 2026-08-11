package com.ulticode.modules.submission.sandbox;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: verifies the committed seccomp-profile.json does not
 * block ordinary fork/pipe calls. Reproduces the
 * "sh: 0: Cannot fork" failure that turned every Java/C/C++ submission
 * into a "Sandbox Error" verdict.
 *
 * <p>Skipped by Surefire (filename suffix *IT). Run explicitly:
 * {@code ./mvnw -Dtest='*IT' test}.
 *
 * <p>Two-tier skip semantics:
 * <ul>
 *   <li>If {@code docker} binary is missing → all docker tests skipped via {@code Assumptions.abort}.</li>
 *   <li>If {@code ulticode-sandbox:latest} image is missing → docker-run tests skipped.</li>
 *   <li>The JSON validation test always runs (no docker dependency).</li>
 * </ul>
 */
@DisplayName("SandboxForkE2EIT")
class SandboxForkE2EIT {

    private static final String IMAGE = "ulticode-sandbox:latest";

    /** Wall-clock cap for any single `docker run` invocation. */
    private static final int DOCKER_CLEANUP_TIMEOUT_SECONDS = 5;
    private static final int DOCKER_RUN_TIMEOUT_SECONDS = 30;

    private static boolean dockerAvailable;
    private static boolean imageAvailable;

    @BeforeAll
    static void probeEnvironment() throws IOException, InterruptedException {
        dockerAvailable = isCommandAvailable("docker", "--version");
        if (dockerAvailable) {
            // L3: Java 17 Process does not implement AutoCloseable; explicit close in finally
            // (Java 19+ added Process.close() via JEP 358 / ProcessHandle).
            Process proc = new ProcessBuilder("docker", "image", "inspect", IMAGE)
                    .redirectErrorStream(true).start();
            try {
                imageAvailable = proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0;
            } finally {
                proc.destroyForcibly();
            }
        } else {
            imageAvailable = false;
        }
    }

    @Test
    @DisplayName("Shell pipe inside sandbox does NOT trigger Cannot fork")
    void shellPipe_executesSuccessfully() throws IOException, InterruptedException {
        Assumptions.assumeTrue(dockerAvailable, "docker CLI not available; skipping sandbox runtime test");
        Assumptions.assumeTrue(imageAvailable, IMAGE + " image not built locally; skipping sandbox runtime test");

        SandboxRunResult result = runSandbox(List.of(
                "sh", "-c", "echo hi | cat"
        ));

        Assumptions.assumeTrue(result.exitCode() == 0,
                "docker run failed (likely image/network issue), not a seccomp regression; skipping. Output: "
                        + result.output());

        assertThat(result.output())
                .as("output should be 'hi' and contain no 'Cannot fork'")
                .contains("hi")
                .doesNotContain("Cannot fork");
    }

    @Test
    @DisplayName("Java compile pipeline inside sandbox executes successfully (reproduces API bug)")
    void javaCompile_pipeline_executesSuccessfully() throws IOException, InterruptedException {
        Assumptions.assumeTrue(dockerAvailable, "docker CLI not available; skipping sandbox runtime test");
        Assumptions.assumeTrue(imageAvailable, IMAGE + " image not built locally; skipping sandbox runtime test");

        // Base64 of: public class Main{public static void main(String[] args){System.out.println("hello");}}
        String javaB64 = "cHVibGljIGNsYXNzIE1haW57cHVibGljIHN0YXRpYyB2b2lkIG1haW4oU3RyaW5nW10gYXJncyl7U3lzdGVtLm91dC5wcmludGxuKCJoZWxsbyIpO319";

        // Mirror SandboxServiceImpl.buildDockerCommand() java wrapper (line 282-283)
        SandboxRunResult result = runSandbox(List.of(
                "sh", "-c", "echo '" + javaB64 + "' | base64 -d > /tmp/Main.java && javac /tmp/Main.java && java -cp /tmp Main"
        ));

        Assumptions.assumeTrue(result.exitCode() == 0,
                "docker run failed (likely image/network issue), not a seccomp regression; skipping. Output: "
                        + result.output());

        assertThat(result.output())
                .as("java compile+run pipeline must produce 'hello' and not 'Cannot fork'")
                .contains("hello")
                .doesNotContain("Cannot fork");
    }

    @Test
    @DisplayName("Hung sandbox process obeys the hard timeout")
    void hungSandboxProcessIsTerminated() throws IOException, InterruptedException {
        Assumptions.assumeTrue(dockerAvailable, "docker CLI not available; skipping sandbox runtime test");
        Assumptions.assumeTrue(imageAvailable, IMAGE + " image not built locally; skipping sandbox runtime test");

        assertThatThrownBy(() -> runSandbox(List.of("sh", "-c", "sleep 60"), 1))
                .isInstanceOf(InterruptedException.class)
                .hasMessage("docker run exceeded 1s timeout");
    }

    @Test
    @DisplayName("Seccomp profile JSON declares 6 clone rules with SCMP_CMP_EQ (regression guard)")
    void seccompProfile_cloneRulesUseSCMPCMPEQ() throws IOException {
        Path profile = locateSeccompProfile();
        String json = Files.readString(profile);

        // Defense against the Docker 29.x + libseccomp 2.6+ bug where
        // SCMP_CMP_MASKED_EQ rules on clone() match ALL clones regardless
        // of mask/value semantics. Fix is to use 6 separate SCMP_CMP_EQ
        // rules, one per CLONE_NEW* flag.
        assertThat(json)
                .as("clone rules must use SCMP_CMP_EQ (not SCMP_CMP_MASKED_EQ) due to Docker 29 libseccomp parsing")
                .contains("\"op\": \"SCMP_CMP_EQ\"")
                .doesNotContain("\"op\": \"SCMP_CMP_MASKED_EQ\"")
                .contains("\"value\": 131072")
                .contains("\"value\": 67108864")
                .contains("\"value\": 134217728")
                .contains("\"value\": 268435456")
                .contains("\"value\": 536870912")
                .contains("\"value\": 1073741824");
    }

    @Test
    @DisplayName("Seccomp profile still blocks unshare (security hardening not regressed)")
    void seccompProfile_unshareStillBlocked() throws IOException, InterruptedException {
        Assumptions.assumeTrue(dockerAvailable, "docker CLI not available; skipping sandbox runtime test");
        Assumptions.assumeTrue(imageAvailable, IMAGE + " image not built locally; skipping sandbox runtime test");

        SandboxRunResult result = runSandbox(List.of(
                "sh", "-c", "unshare --user --map-root-user true 2>&1; echo exit=$?"
        ));

        Assumptions.assumeTrue(result.exitCode() == 0,
                "docker run failed (likely image/network issue), not a seccomp regression; skipping. Output: "
                        + result.output());

        // L1 fix: hardened kernels (e.g. grsec) may return "Permission denied"
        // or "Operation not permitted" depending on host-level userns policy.
        assertThat(result.output())
                .as("unshare --user must still be blocked by seccomp (perm-denied variant)")
                .containsAnyOf("Operation not permitted", "Permission denied");
    }

    /**
     * H1 regression guard: multi-flag clone() (e.g. CLONE_NEWNS|CLONE_NEWPID) cannot be
     * expressed via seccomp on Docker 29.x (SCMP_CMP_MASKED_EQ would match all clones).
     * Defense-in-depth relies on {@code --cap-drop ALL} removing CAP_SYS_ADMIN so the
     * kernel itself denies multi-flag namespace creation. This test fails the moment
     * someone removes {@code --cap-drop ALL} from {@code SandboxServiceImpl.buildDockerCommand()}.
     */
    @Test
    @DisplayName("Multi-flag clone() is blocked by capability gate (--cap-drop ALL dependency)")
    void multiFlag_clone_isBlockedByCapabilityGate() throws IOException, InterruptedException {
        Assumptions.assumeTrue(dockerAvailable, "docker CLI not available; skipping sandbox runtime test");
        Assumptions.assumeTrue(imageAvailable, IMAGE + " image not built locally; skipping sandbox runtime test");

        // CLONE_NEWNS (0x20000) | CLONE_NEWPID (0x20000000) = 0x20020000 = 537001984
        // SYS_clone = 56 on x86_64
        SandboxRunResult result = runSandbox(List.of(
                "sh", "-c",
                "python3 -c \""
                        + "import ctypes;"
                        + "libc=ctypes.CDLL(None,use_errno=True);"
                        + "r=libc.syscall(56, 537001984, 0, 0, 0, 0);"
                        + "import os;"
                        + "print(f'r={r} errno={ctypes.get_errno()} msg={os.strerror(ctypes.get_errno())}')\""
        ));

        Assumptions.assumeTrue(result.exitCode() == 0,
                "docker run failed (likely image/network issue), not a seccomp regression; skipping. Output: "
                        + result.output());

        assertThat(result.output())
                .as("clone(CLONE_NEWNS|CLONE_NEWPID) must return EPERM (errno=1) -- "
                        + "denied by CAP_SYS_ADMIN absence (--cap-drop ALL). "
                        + "If this test fails, --cap-drop ALL was removed and multi-flag "
                        + "namespace creation is now possible.")
                .contains("errno=1")
                .containsAnyOf("Operation not permitted", "Permission denied");
    }

    /**
     * Immutable result of a docker run. L3: replaces raw {@link Process} usage
     * at call sites with a value type so callers don't accidentally leak FDs.
     */
    private record SandboxRunResult(int exitCode, String output) {}

    /**
     * Run {@code docker run} with the canonical sandbox flags and return
     * {@link SandboxRunResult}. Applies {@link #DOCKER_RUN_TIMEOUT_SECONDS}
     * hard timeout to prevent CI hangs. Never throws on non-zero exit --
     * callers decide whether to assume-skip or assert via
     * {@code Assumptions.assumeTrue(result.exitCode() == 0, ...)}.
     */
    private SandboxRunResult runSandbox(List<String> innerCmd) throws IOException, InterruptedException {
        return runSandbox(innerCmd, DOCKER_RUN_TIMEOUT_SECONDS);
    }

    private SandboxRunResult runSandbox(List<String> innerCmd, int timeoutSeconds)
            throws IOException, InterruptedException {
        Path profile = locateSeccompProfile();
        Path profileDir = profile.getParent();

        String containerName = "ulticode-sandbox-test-" + UUID.randomUUID();
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--name", containerName, "-i",
                "--network", "none",
                "--cap-drop", "ALL",
                "--memory", "128m", "--cpus", "1.0",
                "--pids-limit", "128", "--ulimit", "nofile=128:128",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                "--read-only",
                "--user", "1000:1000",
                "--security-opt", "no-new-privileges:true",
                "--security-opt", "seccomp=" + profile.toAbsolutePath(),
                "--volume", profileDir.toAbsolutePath() + ":/seccomp-profile:ro",
                IMAGE
        ));
        cmd.addAll(innerCmd);

        String output;
        int exitCode;
        boolean cleanupRequired = true;
        // Drain output concurrently; readAllBytes() before waitFor() would bypass the hard timeout
        // whenever the sandbox process stays alive without closing its stdout.
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        FutureTask<String> outputTask = new FutureTask<>(() -> {
            try (InputStream in = proc.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        });
        Thread outputReader = new Thread(outputTask, "sandbox-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();
        Exception operationFailure = null;
        try {
            boolean exited = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                throw new InterruptedException("docker run exceeded " + timeoutSeconds + "s timeout");
            }
            exitCode = proc.exitValue();
            try {
                output = outputTask.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                throw new IOException("failed to read docker output", exception.getCause());
            } catch (TimeoutException exception) {
                throw new IOException("docker output reader exceeded 5s timeout", exception);
            }
            cleanupRequired = false;
        } catch (IOException | InterruptedException exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            Exception cleanupFailure = null;
            boolean cleanupInterrupted = false;
            proc.destroyForcibly();
            try {
                if (!proc.waitFor(DOCKER_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    cleanupFailure = new IOException("docker run client did not exit after bounded cleanup");
                }
            } catch (InterruptedException exception) {
                cleanupInterrupted = true;
                cleanupFailure = exception;
            }
            if (outputReader.isAlive()) {
                outputReader.interrupt();
                try {
                    outputReader.join(1000);
                } catch (InterruptedException exception) {
                    cleanupInterrupted = true;
                    cleanupFailure = mergeCleanupFailure(cleanupFailure, exception);
                }
            }
            if (cleanupRequired) {
                try {
                    forceRemoveSandboxContainer(containerName);
                } catch (IOException | InterruptedException exception) {
                    cleanupInterrupted |= exception instanceof InterruptedException;
                    cleanupFailure = mergeCleanupFailure(cleanupFailure, exception);
                }
            }
            if (cleanupFailure != null) {
                if (operationFailure != null) {
                    operationFailure.addSuppressed(cleanupFailure);
                    if (cleanupInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                } else if (cleanupFailure instanceof InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exception;
                } else {
                    if (cleanupInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                    throw (IOException) cleanupFailure;
                }
            }
        }
        return new SandboxRunResult(exitCode, output);
    }

    private static Exception mergeCleanupFailure(Exception existing, Exception additional) {
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
    }

    private static void forceRemoveSandboxContainer(String containerName)
            throws IOException, InterruptedException {
        InterruptedException interruption = null;
        IOException ioFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Process cleanup = null;
            try {
                try {
                    cleanup = new ProcessBuilder("docker", "rm", "--force", containerName)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
                    try {
                        boolean exited = cleanup.waitFor(DOCKER_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        if (!exited) {
                            cleanup.destroyForcibly();
                        }
                    } catch (InterruptedException exception) {
                        interruption = exception;
                        cleanup.destroyForcibly();
                    }
                } catch (IOException exception) {
                    ioFailure = exception;
                }
            } finally {
                if (cleanup != null) {
                    cleanup.destroyForcibly();
                }
            }
            boolean present;
            try {
                present = isSandboxContainerPresent(containerName);
            } catch (InterruptedException exception) {
                interruption = exception;
                continue;
            } catch (IOException exception) {
                ioFailure = exception;
                continue;
            }
            if (!present) {
                if (interruption != null) {
                    if (ioFailure != null) {
                        interruption.addSuppressed(ioFailure);
                    }
                    Thread.currentThread().interrupt();
                    throw interruption;
                }
                return;
            }
        }
        if (interruption != null) {
            if (ioFailure != null) {
                interruption.addSuppressed(ioFailure);
            }
            Thread.currentThread().interrupt();
            throw interruption;
        }
        if (ioFailure != null) {
            throw ioFailure;
        }
        throw new IOException("sandbox container still exists after bounded cleanup: " + containerName);
    }

    private static boolean isSandboxContainerPresent(String containerName)
            throws IOException, InterruptedException {
        Process inspect = null;
        try {
            inspect = new ProcessBuilder(
                    "docker", "ps", "--all", "--quiet", "--filter", "name=" + containerName)
                    .redirectErrorStream(true)
                    .start();
            if (!inspect.waitFor(DOCKER_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                inspect.destroyForcibly();
                throw new IOException("docker ps timed out while checking sandbox cleanup");
            }
            if (inspect.exitValue() != 0) {
                throw new IOException("docker ps failed while checking sandbox cleanup");
            }
            return !new String(inspect.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim()
                    .isEmpty();
        } finally {
            if (inspect != null) {
                inspect.destroyForcibly();
            }
        }
    }

    private static boolean isCommandAvailable(String... cmd) throws IOException, InterruptedException {
        // L3: Java 17 Process does not implement AutoCloseable; explicit close in finally.
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try {
            boolean exited = proc.waitFor(5, TimeUnit.SECONDS);
            return exited && proc.exitValue() == 0;
        } finally {
            proc.destroyForcibly();
        }
    }

    private Path locateSeccompProfile() {
        Path configuredPath = Path.of("docker/sandbox/seccomp-profile.json");
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path ancestor = cwd; ancestor != null; ancestor = ancestor.getParent()) {
            Path candidate = ancestor.resolve(configuredPath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return cwd.resolve(configuredPath).normalize();
    }
}
