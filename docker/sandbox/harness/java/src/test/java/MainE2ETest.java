import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for {@link Main} — exercises the full submission flow
 * (Solution.java → javac → java Main → envelope JSON) via subprocess.
 *
 * <p>Drives the same code path the sandbox image will use, only without
 * Docker. Pre-built harness classes come from the Surefire-injected system
 * property {@code harness.classes.dir} (set in pom.xml).
 */
class MainE2ETest {

    private Path workDir;
    private Path harnessClassesDir;

    @BeforeEach
    void setUp() throws IOException {
        workDir = Files.createTempDirectory("harness-e2e-");
        String injected = System.getProperty("harness.classes.dir");
        if (injected == null) {
            injected = Paths.get("target", "classes").toAbsolutePath().toString();
        }
        harnessClassesDir = Paths.get(injected);
        assertThat(Files.isDirectory(harnessClassesDir))
                .as("Harness classes must be pre-built at: " + harnessClassesDir
                        + ". Run `mvn test-compile` first if invoking IDE tests.")
                .isTrue();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(workDir)) {
            try (var stream = Files.walk(workDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    /** Compile {@code Solution.java}, run {@code Main}, return its stdout (the envelope JSON). */
    private String runFlow(String solutionSrc, String inputJson) throws IOException, InterruptedException {
        Path solution = workDir.resolve("Solution.java");
        Files.writeString(solution, solutionSrc, StandardCharsets.UTF_8);
        Path input = workDir.resolve("input.json");
        Files.writeString(input, inputJson, StandardCharsets.UTF_8);

        Process javacProc = new ProcessBuilder(
                "javac", "-cp", harnessClassesDir.toString(),
                "-d", workDir.toString(), solution.toString())
                .redirectErrorStream(true)
                .start();
        boolean javacDone = javacProc.waitFor(30, TimeUnit.SECONDS);
        assertThat(javacDone).as("javac timed out").isTrue();
        String javacOut = new String(javacProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(javacProc.exitValue()).as("javac failed:\n" + javacOut).isZero();

        Process javaProc = new ProcessBuilder(
                "java", "-cp",
                harnessClassesDir.toString() + java.io.File.pathSeparator + workDir.toString(),
                "Main", input.toString())
                .redirectErrorStream(false)
                .start();
        boolean javaDone = javaProc.waitFor(30, TimeUnit.SECONDS);
        assertThat(javaDone).as("java Main timed out").isTrue();
        String stdout = new String(javaProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(javaProc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(javaProc.exitValue()).as("java Main exited non-zero. stderr:\n" + stderr).isZero();
        return stdout;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEnvelope(String stdout) {
        return (Map<String, Object>) Harness.parseJson(stdout);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> envelope) {
        return (List<Map<String, Object>>) envelope.get("results");
    }

    @Test
    @DisplayName("AC: int[] input, int return")
    void e2e_intArrayAccepted() throws Exception {
        String solution = """
                public class Solution {
                    public int sum(int[] nums) {
                        int total = 0;
                        for (int n : nums) total += n;
                        return total;
                    }
                }
                """;
        String input = """
                {
                  "per_case_timeout_ms": 1000,
                  "cases": [
                    {
                      "case_id": "c1",
                      "label": "Case 1",
                      "inputs": [{"name":"nums","value":"[1,2,3,4]"}],
                      "expected_output": "10"
                    }
                  ]
                }
                """;
        Map<String, Object> env = parseEnvelope(runFlow(solution, input));
        assertThat(env.get("harness_version")).isEqualTo("1.0");
        assertThat(env.get("language")).isEqualTo("java");
        assertThat(env.get("exit_code")).isEqualTo(0L);

        List<Map<String, Object>> results = results(env);
        assertThat(results).hasSize(1);
        Map<String, Object> r = results.get(0);
        assertThat(r.get("status")).isEqualTo("Accepted");
        assertThat(r.get("result")).isEqualTo(10L);
        assertThat(r.get("user_stdout")).isEqualTo("");
    }

    @Test
    @DisplayName("WA: wrong result yields Wrong Answer")
    void e2e_wrongAnswer() throws Exception {
        String solution = """
                public class Solution {
                    public int sum(int[] nums) { return 999; }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"nums","value":"[1,2]"}],"expected_output":"3"}
                  ]
                }
                """;
        Map<String, Object> r = results(parseEnvelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Wrong Answer");
        assertThat(r.get("result")).isEqualTo(999L);
    }

    @Test
    @DisplayName("RE: NullPointerException yields Runtime Error with stack")
    void e2e_runtimeError() throws Exception {
        String solution = """
                public class Solution {
                    public int crash(int n) {
                        String s = null;
                        return s.length();
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"5"}],"expected_output":"0"}
                  ]
                }
                """;
        Map<String, Object> r = results(parseEnvelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Runtime Error");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) r.get("error");
        assertThat(error).isNotNull();
        assertThat(error.get("type")).isEqualTo("java.lang.NullPointerException");
        @SuppressWarnings("unchecked")
        List<String> stack = (List<String>) error.get("stack");
        assertThat(stack).anyMatch(s -> s.contains("Solution.crash"));
        // Harness frames must NOT appear
        assertThat(stack).noneMatch(s -> s.startsWith("Main.") || s.startsWith("Harness."));
    }

    @Test
    @DisplayName("TLE: infinite loop yields Time Limit Exceeded, interrupted flag")
    void e2e_timeLimitExceeded() throws Exception {
        String solution = """
                public class Solution {
                    public int spin(int n) {
                        long x = 0;
                        while (!Thread.currentThread().isInterrupted()) {
                            x++;
                            if (x % 1_000_000 == 0 && Thread.currentThread().isInterrupted()) break;
                        }
                        return 0;
                    }
                }
                """;
        String input = """
                {
                  "per_case_timeout_ms": 200,
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"5"}],"expected_output":"0"}
                  ]
                }
                """;
        Map<String, Object> r = results(parseEnvelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Time Limit Exceeded");
        assertThat(r.get("interrupted")).isEqualTo(Boolean.TRUE);
        // elapsed_ms should be near the timeout, not zero
        assertThat(((Number) r.get("elapsed_ms")).longValue()).isGreaterThanOrEqualTo(150L);
    }

    @Test
    @DisplayName("AC: ListNode argument and return (problem #7 shape)")
    void e2e_listNodeRoundTrip() throws Exception {
        String solution = """
                public class Solution {
                    public ListNode mergeTwoLists(ListNode a, ListNode b) {
                        ListNode dummy = new ListNode(0);
                        ListNode tail = dummy;
                        while (a != null && b != null) {
                            if (a.val <= b.val) { tail.next = a; a = a.next; }
                            else { tail.next = b; b = b.next; }
                            tail = tail.next;
                        }
                        tail.next = (a != null) ? a : b;
                        return dummy.next;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {
                      "case_id":"c1",
                      "inputs":[
                        {"name":"a","value":"[1,2,4]"},
                        {"name":"b","value":"[1,3,4]"}
                      ],
                      "expected_output":"[1,1,2,3,4,4]"
                    }
                  ]
                }
                """;
        Map<String, Object> r = results(parseEnvelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Accepted");
        assertThat(r.get("result")).isEqualTo(List.of(1L, 1L, 2L, 3L, 4L, 4L));
    }

    @Test
    @DisplayName("AC: TreeNode argument, max-depth recursion")
    void e2e_treeNodeMaxDepth() throws Exception {
        String solution = """
                public class Solution {
                    public int maxDepth(TreeNode root) {
                        if (root == null) return 0;
                        return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"root","value":"[3,9,20,null,null,15,7]"}],"expected_output":"3"}
                  ]
                }
                """;
        Map<String, Object> r = results(parseEnvelope(runFlow(solution, input))).get(0);
        assertThat(r.get("status")).isEqualTo("Accepted");
        assertThat(r.get("result")).isEqualTo(3L);
    }

    @Test
    @DisplayName("user println is captured into user_stdout and does NOT contaminate envelope JSON")
    void e2e_userPrintlnCapture() throws Exception {
        String solution = """
                public class Solution {
                    public int sum(int[] nums) {
                        System.out.println("debug-line-1");
                        System.out.print("debug-line-2");
                        int total = 0;
                        for (int n : nums) total += n;
                        return total;
                    }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"nums","value":"[1,2]"}],"expected_output":"3"}
                  ]
                }
                """;
        Map<String, Object> env = parseEnvelope(runFlow(solution, input));
        Map<String, Object> r = results(env).get(0);
        assertThat(r.get("status")).isEqualTo("Accepted");
        assertThat((String) r.get("user_stdout"))
                .contains("debug-line-1")
                .contains("debug-line-2");
    }

    @Test
    @DisplayName("multiple cases run independently in one envelope")
    void e2e_multipleCasesMixedVerdicts() throws Exception {
        String solution = """
                public class Solution {
                    public int doubleIt(int n) { return n * 2; }
                }
                """;
        String input = """
                {
                  "cases": [
                    {"case_id":"c1","inputs":[{"name":"n","value":"3"}],"expected_output":"6"},
                    {"case_id":"c2","inputs":[{"name":"n","value":"5"}],"expected_output":"99"},
                    {"case_id":"c3","inputs":[{"name":"n","value":"-1"}],"expected_output":"-2"}
                  ]
                }
                """;
        List<Map<String, Object>> results = results(parseEnvelope(runFlow(solution, input)));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("status")).isEqualTo("Accepted");
        assertThat(results.get(1).get("status")).isEqualTo("Wrong Answer");
        assertThat(results.get(2).get("status")).isEqualTo("Accepted");
    }
}
