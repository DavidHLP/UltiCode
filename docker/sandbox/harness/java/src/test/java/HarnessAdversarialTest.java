import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial tests covering the CR findings (post-Codex review).
 *
 * <p>Each test maps to a specific CR item:
 * - cycle detection in writeJson / jsonable
 * - JSON parser strict grammar (no leading zero, no naked dot, no trailing data,
 *   no Infinity/NaN, max depth, integer overflow rejection)
 * - resolveSolutionMethod ambiguity / missing / overload behaviour
 * - non-finite result rejection
 */
class HarnessAdversarialTest {

    // ── Strict JSON parser (CR #3) ─────────────────────────────────────────

    @Test
    @DisplayName("parseJson rejects trailing data")
    void parser_trailingData() {
        assertThatThrownBy(() -> Harness.parseJson("[1,2,3]junk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects leading zero in number")
    void parser_leadingZero() {
        assertThatThrownBy(() -> Harness.parseJson("01"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects naked decimal point")
    void parser_nakedDot() {
        assertThatThrownBy(() -> Harness.parseJson("1."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects empty exponent")
    void parser_emptyExponent() {
        assertThatThrownBy(() -> Harness.parseJson("1e"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects unquoted Infinity / NaN literals")
    void parser_infinityNanLiterals() {
        assertThatThrownBy(() -> Harness.parseJson("Infinity"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.parseJson("NaN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.parseJson("-Infinity"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects exponent that overflows Double to Infinity")
    void parser_exponentOverflow() {
        assertThatThrownBy(() -> Harness.parseJson("1e500"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson rejects integer > Long.MAX_VALUE rather than silently casting to Double")
    void parser_integerOverflow() {
        // 9_223_372_036_854_775_808 = Long.MAX_VALUE + 1
        assertThatThrownBy(() -> Harness.parseJson("9223372036854775808"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson accepts negative zero as Long 0")
    void parser_negativeZero() {
        assertThat(Harness.parseJson("-0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("parseJson rejects unescaped control character in string")
    void parser_unescapedControl() {
        char bs = (char) 92;
        String input = "\"" + (char) 1 + "\"";
        assertThatThrownBy(() -> Harness.parseJson(input))
                .isInstanceOf(IllegalArgumentException.class);
        // sanity: escaped control is still accepted via backslash u XXXX path
        String okInput = "\"" + bs + "u0001\"";
        assertThat(Harness.parseJson(okInput)).isEqualTo(String.valueOf((char) 1));
    }

    @Test
    @DisplayName("parseJson rejects unquoted bareword keys")
    void parser_barewordKey() {
        assertThatThrownBy(() -> Harness.parseJson("{a:1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseJson enforces MAX_NESTING_DEPTH for deeply nested arrays")
    void parser_maxNesting() {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < Harness.MAX_NESTING_DEPTH + 5; i++) {
            open.append('[');
            close.append(']');
        }
        assertThatThrownBy(() -> Harness.parseJson(open + "1" + close))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Serializer cycle detection (CR #2) ─────────────────────────────────

    @Test
    @DisplayName("toJson rejects self-referential List with IAE")
    void toJson_cyclicList() {
        List<Object> a = new ArrayList<>();
        a.add(1);
        a.add(a);
        assertThatThrownBy(() -> Harness.toJson(a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    @DisplayName("toJson rejects self-referential Map with IAE")
    void toJson_cyclicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", m);
        assertThatThrownBy(() -> Harness.toJson(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    @DisplayName("toJson rejects mutually-referential List + Map cycle")
    void toJson_mutualCycle() {
        List<Object> a = new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        a.add(m);
        m.put("loop", a);
        assertThatThrownBy(() -> Harness.toJson(a))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toJson rejects non-finite Double")
    void toJson_nonFiniteDouble() {
        assertThatThrownBy(() -> Harness.toJson(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.toJson(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("jsonable rejects cyclic List with IAE (so the per-case path can convert to RE)")
    void jsonable_cyclicList() {
        List<Object> a = new ArrayList<>();
        a.add(a);
        assertThatThrownBy(() -> Harness.jsonableValue(a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    @DisplayName("jsonable rejects non-finite float in result")
    void jsonable_nonFiniteFloat() {
        assertThatThrownBy(() -> Harness.jsonableValue(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Non-finite");
    }

    // ── Deterministic method selection (CR #4) ─────────────────────────────

    @Test
    @DisplayName("resolveSolutionMethod returns the single public instance method")
    void resolve_single() throws NoSuchMethodException {
        Method m = Main.resolveSolutionMethod(SingleMethodFixture.class, null);
        assertThat(m.getName()).isEqualTo("only");
    }

    @Test
    @DisplayName("resolveSolutionMethod throws on Solution with multiple public methods (no hint)")
    void resolve_multipleNoHint() {
        assertThatThrownBy(() -> Main.resolveSolutionMethod(TwoMethodFixture.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple public instance methods");
    }

    @Test
    @DisplayName("resolveSolutionMethod picks the named method when methodHint is given")
    void resolve_byHint() {
        Method m = Main.resolveSolutionMethod(TwoMethodFixture.class, "alpha");
        assertThat(m.getName()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("resolveSolutionMethod rejects overloads (multiple methods with the same name)")
    void resolve_overload() {
        assertThatThrownBy(() -> Main.resolveSolutionMethod(OverloadFixture.class, "doIt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple public instance methods named 'doIt'");
    }

    @Test
    @DisplayName("resolveSolutionMethod throws when class has no public instance method")
    void resolve_noPublic() {
        assertThatThrownBy(() -> Main.resolveSolutionMethod(NoPublicFixture.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No public instance method");
    }

    @Test
    @DisplayName("resolveSolutionMethod throws when methodHint names a non-existent method")
    void resolve_missingHint() {
        assertThatThrownBy(() -> Main.resolveSolutionMethod(SingleMethodFixture.class, "nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    public static class SingleMethodFixture {
        public int only() { return 0; }
    }

    public static class TwoMethodFixture {
        public int alpha() { return 0; }
        public int beta() { return 0; }
    }

    public static class OverloadFixture {
        public int doIt() { return 0; }
        public int doIt(int n) { return n; }
    }

    public static class NoPublicFixture {
        private int hidden() { return 0; }
        public static int staticMethod() { return 0; }
    }

    // ── TreeNode cycle guard (CR #2) ──────────────────────────────────────────

    @Test
    @DisplayName("fromTreeNode rejects self-referential TreeNode with IAE")
    void fromTreeNode_selfCycle() {
        TreeNode root = new TreeNode(1);
        root.left = root; // self-loop
        assertThatThrownBy(() -> Harness.fromTreeNode(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    @DisplayName("fromTreeNode rejects mutually-referential TreeNode cycle")
    void fromTreeNode_mutualCycle() {
        TreeNode a = new TreeNode(1);
        TreeNode b = new TreeNode(2);
        a.left = b;
        b.right = a;
        assertThatThrownBy(() -> Harness.fromTreeNode(a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    @DisplayName("fromTreeNode respects shared MAX_JSONABLE_NODES cap")
    void fromTreeNode_nodeCap() {
        // Build a perfectly balanced tree of depth 21 (≈ 2M nodes), well over
        // the 1M cap. Constructor + manual wiring is cheap enough for a unit test.
        TreeNode root = new TreeNode(0);
        java.util.Deque<TreeNode> q = new java.util.ArrayDeque<>();
        q.offer(root);
        int built = 1;
        while (built <= Harness.MAX_JSONABLE_NODES + 5 && !q.isEmpty()) {
            TreeNode n = q.poll();
            n.left = new TreeNode(built++);
            n.right = new TreeNode(built++);
            q.offer(n.left);
            q.offer(n.right);
        }
        assertThatThrownBy(() -> Harness.fromTreeNode(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("node limit");
    }

    // ── Output-size cap enforced incrementally (CR #3) ──────────────────────

    @Test
    @DisplayName("toJson rejects a single oversized string scalar")
    void toJson_oversizedString() {
        // 1 MiB over the cap should trip the post-append check inside writeString.
        StringBuilder big = new StringBuilder(Harness.MAX_JSON_OUTPUT_BYTES + 1024);
        for (int i = 0; i < Harness.MAX_JSON_OUTPUT_BYTES + 1024; i++) {
            big.append('x');
        }
        assertThatThrownBy(() -> Harness.toJson(big.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("toJson rejects a map with one giant key when post-append kicks in")
    void toJson_oversizedKey() {
        StringBuilder big = new StringBuilder(Harness.MAX_JSON_OUTPUT_BYTES + 1024);
        for (int i = 0; i < Harness.MAX_JSON_OUTPUT_BYTES + 1024; i++) {
            big.append('k');
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put(big.toString(), 1);
        assertThatThrownBy(() -> Harness.toJson(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds limit");
    }
}
