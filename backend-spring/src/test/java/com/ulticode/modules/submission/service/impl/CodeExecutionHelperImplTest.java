package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Test
    @DisplayName("per-case timeout floor prevents subprocess.run(timeout=0) when many cases")
    void resolvePerCaseTimeoutSeconds_largeCaseCount_floorsAtOneSecond() {
        // Regression for review H2: 30 cases splits the budget to 1s
        // per case; 31+ would previously degrade to 0s which is invalid for
        // subprocess.run and would crash the wrapper.
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(30)).isEqualTo(1);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(31)).isEqualTo(1);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(100)).isEqualTo(1);
    }

    @Test
    @DisplayName("per-case timeout returns the full 30s budget when no cases")
    void resolvePerCaseTimeoutSeconds_empty_returnsFullBudget() {
        // Harmless edge case: an empty case list still produces a
        // well-formed wrapper, and the full budget means no spurious
        // timeout if the wrapper does emit any work.
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(0)).isEqualTo(30);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(-1)).isEqualTo(30);
    }

    @Test
    @DisplayName("per-case timeout splits evenly for small case counts")
    void resolvePerCaseTimeoutSeconds_smallCount_splitsEvenly() {
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(1)).isEqualTo(30);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(2)).isEqualTo(15);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(3)).isEqualTo(10);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(6)).isEqualTo(5);
        assertThat(CodeExecutionHelperImpl.resolvePerCaseTimeoutSeconds(15)).isEqualTo(2);
    }

    // ── Java sandbox wrapper regression tests ──────────────────────────────
    // These tests guard review findings C1 (illegal local-method syntax in
    // generated Main.java), H1 (helper extraction), H2 (zero coverage), and
    // M1/M2 (regex + silent-null). The single most important check is that
    // the assembled Main.java is *syntactically valid Java* — sandbox javac
    // will fail on any uncaught syntax error in the generated constants.

    @Test
    @DisplayName("Java batch wrapper decodes to syntactically valid Main.java (ListNode path)")
    void buildJavaBatchWrapper_listNode_decodesToValidJava() throws Exception {
        String code = """
                public class Solution {
                    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
                        return l1;
                    }
                }
                """;
        String mainSource = decodeMainSource(helper.buildJavaBatchWrapper(code, List.of()));
        Path tmp = writeMainSource(mainSource);
        try {
            assertCompiles(tmp);
            assertThat(mainSource)
                    .as("Generated source should declare ListNode class and conversion helpers")
                    .contains("class ListNode")
                    .contains("static ListNode toListNode")
                    .contains("static Object fromListNode");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Java batch wrapper decodes to syntactically valid Main.java (no ListNode path)")
    void buildJavaBatchWrapper_simpleClass_decodesToValidJava() throws Exception {
        String code = """
                public class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        return new int[]{0, 1};
                    }
                }
                """;
        String mainSource = decodeMainSource(helper.buildJavaBatchWrapper(code, List.of()));
        Path tmp = writeMainSource(mainSource);
        try {
            assertCompiles(tmp);
            // Helpers are always included now so the generated main is a single uniform shape.
            assertThat(mainSource)
                    .as("Helpers are always present (uniform generated main shape)")
                    .contains("class ListNode")
                    .contains("static ListNode toListNode")
                    .contains("static Object adaptArg")
                    .contains("static Object jsonable");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Java batch wrapper decodes to valid Main.java even with no class Solution (free-form pass-through)")
    void buildJavaBatchWrapper_freeForm_decodesToValidJava() throws Exception {
        String code = """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """;
        String mainSource = decodeMainSource(helper.buildJavaBatchWrapper(code, List.of()));
        Path tmp = writeMainSource(mainSource);
        try {
            assertCompiles(tmp);
            assertThat(mainSource)
                    .as("Free-form code path echoes input unchanged")
                    .contains("System.out.print(input)");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Java batch wrapper strips public from class Solution to avoid clash with public class Main")
    void buildJavaBatchWrapper_stripsPublicFromUserClass() throws Exception {
        String code = """
                public class Solution {
                    public int answer() { return 42; }
                }
                """;
        String mainSource = decodeMainSource(helper.buildJavaBatchWrapper(code, List.of()));
        // Only one public top-level class per .java file. After stripping,
        // the file's public class must be Main, not the user's Solution.
        long publicClassCount = mainSource.lines()
                .filter(line -> line.trim().startsWith("public class "))
                .count();
        assertThat(publicClassCount)
                .as("Exactly one public top-level class (Main) must remain after stripping")
                .isEqualTo(1L);
        assertThat(mainSource)
                .as("User class must lose its 'public' modifier")
                .contains("class Solution {")
                .doesNotContainPattern("(?m)^public\\s+class\\s+Solution");
    }

    @Test
    @DisplayName("stripPublicModifier handles final, abstract, sealed, and non-sealed modifiers")
    void stripPublicModifier_handlesAllModifierCombinations() throws Exception {
        Method m = CodeExecutionHelperImpl.class.getDeclaredMethod("stripPublicModifier", String.class);
        m.setAccessible(true);
        String input = String.join("\n",
                "public final class FinalSolution { void f() {} }",
                "public abstract class AbstractSolution { void f(); }",
                "public sealed class SealedSolution permits Child { }",
                "public non-sealed class NonSealedSolution extends Parent { }",
                "public static class StaticSolution { void f() {} }",
                "public class PlainSolution { void f() {} }");
        String stripped = (String) m.invoke(helper, input);
        // All leading 'public ... class' must be reduced to 'class '.
        assertThat(stripped)
                .contains("class FinalSolution")
                .contains("class AbstractSolution")
                .contains("class SealedSolution")
                .contains("class NonSealedSolution")
                .contains("class StaticSolution")
                .contains("class PlainSolution")
                .doesNotContain("public final class")
                .doesNotContain("public abstract class")
                .doesNotContain("public sealed class")
                .doesNotContain("public non-sealed class")
                .doesNotContain("public static class")
                .doesNotContain("public class PlainSolution");
    }

    @Test
    @DisplayName("Java batch wrapper throws a RuntimeException (not silent null) when Solution has no public method")
    void buildJavaBatchWrapper_noPublicMethod_throwsRuntimeException() throws Exception {
        // A Solution class where every method is private — the reflective
        // selector must throw, not silently emit "null" and pass.
        String code = """
                public class Solution {
                    private int hidden() { return 0; }
                }
                """;
        String mainSource = decodeMainSource(helper.buildJavaBatchWrapper(code, List.of()));
        assertThat(mainSource)
                .as("Generated main must throw, not emit 'null' silently")
                .contains("throw new RuntimeException")
                .doesNotContainPattern("System\\.out\\.print\\(\"null\"\\)");
    }

    @Test
    @DisplayName("wrapJava single-execution path also decodes to valid Main.java")
    void wrapJava_decodesToValidJava() throws Exception {
        String code = """
                public class Solution {
                    public int answer() { return 42; }
                }
                """;
        String mainSource = decodeMainSource(helper.wrapJava(code));
        Path tmp = writeMainSource(mainSource);
        try {
            assertCompiles(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Pulls the base64-encoded Main.java source out of the shell wrapper the
     * helper produces. The wrapper is shaped like
     * {@code echo '<b64>' | base64 -d > /tmp/Main.java && javac ...}, so a single
     * regex recovers the payload.
     */
    private String decodeMainSource(String wrapper) {
        Matcher m = Pattern.compile("echo '([A-Za-z0-9+/=]+)' \\| base64").matcher(wrapper);
        assertThat(m.find()).as("Wrapper should contain a base64 payload: " + wrapper).isTrue();
        return new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8);
    }

    /**
     * Writes the decoded Main.java source to a temp file named {@code Main.java} in a fresh
     * directory. Java requires the file name to match the public class name, so the temp file
     * cannot be a random UUID-suffixed name. The returned path is the file itself; the
     * directory is intentionally left in place (one-shot test).
     */
    private Path writeMainSource(String mainSource) throws IOException {
        Path dir = Files.createTempDirectory("UltiCodeMain_");
        Path file = dir.resolve("Main.java");
        Files.writeString(file, mainSource, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Compile the given Java source with the system {@code javac}. Throws an
     * {@link AssertionError} with the compiler's stderr if compilation fails.
     * This is the regression net for review finding C1: any future string
     * constant that produces illegal local-method syntax will be caught here
     * instead of in the sandbox.
     */
    private void assertCompiles(Path javaFile) throws IOException, InterruptedException {
        Path outDir = Files.createTempDirectory("UltiCodeMainOut_");
        try {
            Process javac = new ProcessBuilder("javac", "-d", outDir.toString(), javaFile.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(javac.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = javac.waitFor(30, TimeUnit.SECONDS);
            assertThat(finished).as("javac timed out: " + output).isTrue();
            assertThat(javac.exitValue())
                    .as("javac failed for " + javaFile + ":\n" + output)
                    .isZero();
        } finally {
            // Best-effort cleanup; ignore failures
        }
    }
}
