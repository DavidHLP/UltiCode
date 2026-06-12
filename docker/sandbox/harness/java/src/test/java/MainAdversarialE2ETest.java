import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the CR adversarial findings, exercising Main via
 * the full Solution.java → javac → java Main flow.
 */
class MainAdversarialE2ETest {

    private Path workDir;
    private Path harnessClassesDir;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws java.io.IOException {
        workDir = Files.createTempDirectory("harness-adv-");
        String injected = System.getProperty("harness.classes.dir");
        if (injected == null) {
            injected = Paths.get("target", "classes").toAbsolutePath().toString();
        }
        harnessClassesDir = Paths.get(injected);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws java.io.IOException {
        if (Files.exists(workDir)) {
            try (var stream = Files.walk(workDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (java.io.IOException ignored) {}
                });
            }
        }
    }

    private String runFlow(String solutionSrc, String inputJson) throws Exception {
        Path solution = workDir.resolve("Solution.java");
        Files.writeString(solution, solutionSrc, StandardCharsets.UTF_8);
        Path input = workDir.resolve("input.json");
        Files.writeString(input, inputJson, StandardCharsets.UTF_8);

        Process javacProc = new ProcessBuilder(
                "javac", "-cp", harnessClassesDir.toString(),
                "-d", workDir.toString(), solution.toString())
                .redirectErrorStream(true).start();
        assertThat(javacProc.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String javacOut = new String(javacProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(javacProc.exitValue()).as("javac failed:\n" + javacOut).isZero();

        Process javaProc = new ProcessBuilder(
                "java",
                // JDK 18+ rejects System.setSecurityManager() unless this flag is
                // present. The production sandbox image pins JDK 17 (where the
                // flag is a no-op) but tests run on the host's JDK (21), so
                // we pass it unconditionally.
                "-Djava.security.manager=allow",
                "-cp",
                harnessClassesDir.toString() + java.io.File.pathSeparator + workDir.toString(),
                "Main", input.toString())
                .redirectErrorStream(false).start();
        assertThat(javaProc.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String stdout = new String(javaProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(javaProc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(javaProc.exitValue())
                .as("Main exit non-zero. stderr:\n" + stderr + "\nstdout:\n" + stdout).isZero();
        // Help diagnose unexpected envelope issues by surfacing stderr in any
        // later assertion failure path (the helper below augments parseJson
        // failures).
        this.lastChildStderr = stderr;
        return stdout;
    }

    private String lastChildStderr;

    @SuppressWarnings("unchecked")
    private Map<String, Object> envelope(String stdout) {
        try {
            return (Map<String, Object>) Harness.parseJson(stdout);
        } catch (RuntimeException re) {
            throw new AssertionError(
                    "Failed to parse envelope. stdout=<" + stdout + ">; child stderr=<"
                            + (lastChildStderr == null ? "(none)" : lastChildStderr) + ">", re);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> env) {
        return (List<Map<String, Object>>) env.get("results");
    }

    @Test
    @DisplayName("System.exit attempted by user code surfaces as Runtime Error (envelope preserved)")
    void e2e_userSystemExitBlocked() throws Exception {
        String solution = """
                public class Solution {
                    public int bomb(int n) {
                        System.exit(0);
                        return 42;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"1"}],"expected_output":"0"}
                  ]
                }
                """;
        Map<String, Object> env = envelope(runFlow(solution, input));
        assertThat(env.get("exit_code")).isEqualTo(0L);
        Map<String, Object> r = results(env).get(0);
        assertThat(r.get("status")).isEqualTo("Runtime Error");
        @SuppressWarnings("unchecked")
        Map<String, Object> err = (Map<String, Object>) r.get("error");
        assertThat((String) err.get("message"))
                .contains("terminate the harness JVM");
    }

    @Test
    @DisplayName("Runtime.halt attempted by user code is also blocked")
    void e2e_userRuntimeHaltBlocked() throws Exception {
        String solution = """
                public class Solution {
                    public int bomb(int n) {
                        Runtime.getRuntime().halt(0);
                        return 42;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"1"}],"expected_output":"0"}
                  ]
                }
                """;
        Map<String, Object> r = results(envelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Runtime Error");
    }

    @Test
    @DisplayName("User returning a cyclic List surfaces as Runtime Error, not a harness panic")
    void e2e_cyclicResult() throws Exception {
        String solution = """
                import java.util.*;
                public class Solution {
                    public List<Object> cycle(int n) {
                        List<Object> a = new ArrayList<>();
                        a.add(1);
                        a.add(a);
                        return a;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"1"}],"expected_output":"[]"}
                  ]
                }
                """;
        Map<String, Object> env = envelope(runFlow(solution, input));
        assertThat(env.get("exit_code")).isEqualTo(0L);
        Map<String, Object> r = results(env).get(0);
        assertThat(r.get("status")).isEqualTo("Runtime Error");
    }

    @Test
    @DisplayName("User returning NaN surfaces as Runtime Error")
    void e2e_nanResult() throws Exception {
        String solution = """
                public class Solution {
                    public double bad(int n) {
                        return 0.0 / 0.0;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"1"}],"expected_output":"0"}
                  ]
                }
                """;
        Map<String, Object> r = results(envelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Runtime Error");
    }

    @Test
    @DisplayName("Solution with multiple public methods is rejected with a clear error")
    void e2e_overloadedSolutionWithoutHint() throws Exception {
        String solution = """
                public class Solution {
                    public int alpha(int n) { return n; }
                    public int beta(int n) { return n; }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"1"}],"expected_output":"1"}
                  ]
                }
                """;
        // The whole batch fails with a harness panic (exit 2) — runFlow asserts exit 0,
        // so use direct subprocess here.
        Path sol = workDir.resolve("Solution.java");
        Files.writeString(sol, solution, StandardCharsets.UTF_8);
        Path inp = workDir.resolve("input.json");
        Files.writeString(inp, input, StandardCharsets.UTF_8);

        Process javacProc = new ProcessBuilder(
                "javac", "-cp", harnessClassesDir.toString(),
                "-d", workDir.toString(), sol.toString())
                .redirectErrorStream(true).start();
        assertThat(javacProc.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(javacProc.exitValue()).isZero();

        Process javaProc = new ProcessBuilder(
                "java", "-cp",
                harnessClassesDir.toString() + java.io.File.pathSeparator + workDir.toString(),
                "Main", inp.toString()).start();
        assertThat(javaProc.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(javaProc.exitValue()).isEqualTo(2); // harness panic
        String stderr = new String(javaProc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(stderr).contains("multiple public instance methods");
    }

    @Test
    @DisplayName("method_name in input.json disambiguates an otherwise ambiguous Solution")
    void e2e_methodNameHint() throws Exception {
        String solution = """
                public class Solution {
                    public int alpha(int n) { return n * 10; }
                    public int beta(int n) { return n * 100; }
                }
                """;
        String input = """
                {
                  "method_name": "beta",
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"3"}],"expected_output":"300"}
                  ]
                }
                """;
        Map<String, Object> r = results(envelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Accepted");
        assertThat(r.get("result")).isEqualTo(300L);
    }
}
