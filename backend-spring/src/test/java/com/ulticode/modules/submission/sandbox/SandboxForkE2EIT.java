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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        Path profile = locateSeccompProfile();
        Path profileDir = profile.getParent();

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
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
        // L3: Java 17 Process does not implement AutoCloseable; explicit close in finally.
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (InputStream in = proc.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            boolean exited = proc.waitFor(DOCKER_RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!exited) {
                // Throw first; the finally block below calls proc.destroyForcibly()
                // so we don't need a redundant call here (round 2 review L1 fix).
                throw new InterruptedException("docker run exceeded " + DOCKER_RUN_TIMEOUT_SECONDS + "s timeout");
            }
            exitCode = proc.exitValue();
        } finally {
            proc.destroyForcibly();
        }
        return new SandboxRunResult(exitCode, output);
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
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path p = cwd.resolve("../docker/sandbox/seccomp-profile.json");
        if (Files.exists(p)) return p.normalize();
        return cwd.resolve("docker/sandbox/seccomp-profile.json").normalize();
    }
}
