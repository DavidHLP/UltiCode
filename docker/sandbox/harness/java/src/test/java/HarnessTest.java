import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Harness — JSON parser/serializer, argument adapter,
 * ListNode/TreeNode conversion, and result jsonable. Pure functions, no IO.
 *
 * IMPORTANT: do not write the 6-char sequence (backslash, u, hex, hex, hex, hex)
 * anywhere in this source file — not even in comments. javac's Unicode
 * preprocessing transforms it into the corresponding code point BEFORE
 * lexing. Use char-cast + concatenation when you need to assert against a
 * hex escape.
 */
class HarnessTest {

    private static final char BS = (char) 92; // backslash

    // JSON parser

    @Test
    @DisplayName("parseJson handles primitives")
    void parseJson_primitives() {
        assertThat(Harness.parseJson("null")).isNull();
        assertThat(Harness.parseJson("true")).isEqualTo(Boolean.TRUE);
        assertThat(Harness.parseJson("false")).isEqualTo(Boolean.FALSE);
        assertThat(Harness.parseJson("42")).isEqualTo(42L);
        assertThat(Harness.parseJson("-7")).isEqualTo(-7L);
        assertThat(Harness.parseJson("3.14")).isEqualTo(3.14);
        assertThat(Harness.parseJson("\"hi\"")).isEqualTo("hi");
    }

    @Test
    @DisplayName("parseJson handles arrays including nested 2D and inline nulls")
    void parseJson_arrays() {
        assertThat(Harness.parseJson("[]")).isEqualTo(new ArrayList<>());
        assertThat(Harness.parseJson("[1,2,3]")).isEqualTo(List.of(1L, 2L, 3L));
        assertThat(Harness.parseJson("[[1,2],[3,4]]"))
                .isEqualTo(List.of(List.of(1L, 2L), List.of(3L, 4L)));
        List<?> withNulls = (List<?>) Harness.parseJson("[1,null,2,null]");
        assertThat(withNulls).hasSize(4);
        assertThat(withNulls.get(0)).isEqualTo(1L);
        assertThat(withNulls.get(1)).isNull();
    }

    @Test
    @DisplayName("parseJson handles objects")
    void parseJson_objects() {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Harness.parseJson("{\"a\":1,\"b\":\"hi\",\"c\":null}");
        assertThat(m).containsEntry("a", 1L).containsEntry("b", "hi").containsEntry("c", null);
    }

    @Test
    @DisplayName("parseJson handles string escapes")
    void parseJson_stringEscapes() {
        // Build the JSON document  "\n\t\"\\"  via char-cast + concat to
        // keep any backslash-letter pair from being touched by the lexer
        // preprocessor while authoring this test.
        String quoted = "\"" + BS + "n" + BS + "t" + BS + "\"" + BS + BS + "\"";
        Object parsed = Harness.parseJson(quoted);
        assertThat(parsed).isEqualTo("\n\t\"" + BS);
    }

    @Test
    @DisplayName("parseJson rejects malformed input with IllegalArgumentException")
    void parseJson_malformed() {
        assertThatThrownBy(() -> Harness.parseJson("{")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.parseJson("[1,2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.parseJson("\"unterminated")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Harness.parseJson("xyz")).isInstanceOf(IllegalArgumentException.class);
    }

    // JSON serializer

    @Test
    @DisplayName("toJson handles primitives")
    void toJson_primitives() {
        assertThat(Harness.toJson(null)).isEqualTo("null");
        assertThat(Harness.toJson(true)).isEqualTo("true");
        assertThat(Harness.toJson(42)).isEqualTo("42");
        assertThat(Harness.toJson(42L)).isEqualTo("42");
        assertThat(Harness.toJson("hi")).isEqualTo("\"hi\"");
    }

    @Test
    @DisplayName("toJson escapes newlines and quotes in strings")
    void toJson_stringEscapes() {
        // Input: a, newline, b, double-quote, c
        // Expected: opening quote, 'a', backslash, 'n', 'b', backslash, double-quote, 'c', closing quote
        String expected = "\"" + "a" + BS + "n" + "b" + BS + "\"" + "c" + "\"";
        assertThat(Harness.toJson("a\nb\"c")).isEqualTo(expected);
    }

    @Test
    @DisplayName("toJson escapes control characters with hex escape")
    void toJson_controlChar() {
        // SOH (0x01) must come back as the 8-char sequence (quote, backslash, u, 0, 0, 0, 1, quote).
        String soh = String.valueOf((char) 1);
        String expected = "\"" + BS + "u0001" + "\"";
        assertThat(Harness.toJson(soh)).isEqualTo(expected);
    }

    @Test
    @DisplayName("toJson elides .0 for whole-valued doubles to match LeetCode expected outputs")
    void toJson_wholeDouble() {
        assertThat(Harness.toJson(10.0)).isEqualTo("10");
        assertThat(Harness.toJson(2.5)).isEqualTo("2.5");
    }

    @Test
    @DisplayName("toJson handles List and Map")
    void toJson_collections() {
        assertThat(Harness.toJson(List.of(1, 2, 3))).isEqualTo("[1,2,3]");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", "hi");
        assertThat(Harness.toJson(m)).isEqualTo("{\"a\":1,\"b\":\"hi\"}");
    }

    @Test
    @DisplayName("toJson handles primitive and reference arrays")
    void toJson_arrays() {
        assertThat(Harness.toJson(new int[] {1, 2, 3})).isEqualTo("[1,2,3]");
        assertThat(Harness.toJson(new int[][] {{1, 2}, {3, 4}})).isEqualTo("[[1,2],[3,4]]");
        assertThat(Harness.toJson(new String[] {"a", "b"})).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    @DisplayName("normalizeJson canonicalizes whitespace")
    void normalizeJson_canonicalizes() {
        assertThat(Harness.normalizeJson("[ 1 , 2 , 3 ]")).isEqualTo("[1,2,3]");
        assertThat(Harness.normalizeJson("{\"a\": 10}")).isEqualTo("{\"a\":10}");
    }

    // ListNode helpers

    @Test
    @DisplayName("toListNode builds a chain in order")
    void toListNode_buildsChain() {
        ListNode head = Harness.toListNode(List.of(1L, 2L, 3L));
        assertThat(head).isNotNull();
        assertThat(head.val).isEqualTo(1);
        assertThat(head.next.val).isEqualTo(2);
        assertThat(head.next.next.val).isEqualTo(3);
        assertThat(head.next.next.next).isNull();
    }

    @Test
    @DisplayName("toListNode handles empty / null input")
    void toListNode_emptyOrNull() {
        assertThat(Harness.toListNode(List.of())).isNull();
        assertThat(Harness.toListNode(null)).isNull();
    }

    @Test
    @DisplayName("fromListNode serializes a chain")
    void fromListNode_serializesChain() {
        ListNode head = new ListNode(1, new ListNode(2, new ListNode(3)));
        assertThat(Harness.fromListNode(head)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fromListNode returns empty for null head")
    void fromListNode_null() {
        assertThat(Harness.fromListNode(null)).isEmpty();
    }

    @Test
    @DisplayName("fromListNode caps traversal to guard against cycles")
    void fromListNode_cycleCap() {
        ListNode a = new ListNode(1);
        ListNode b = new ListNode(2);
        a.next = b;
        b.next = a;
        assertThat(Harness.fromListNode(a)).hasSize(Harness.LIST_NODE_TRAVERSAL_CAP);
    }

    // TreeNode helpers

    @Test
    @DisplayName("toTreeNode builds tree from level-order with null placeholders")
    void toTreeNode_buildsTree() {
        List<Object> input = new ArrayList<>();
        input.add(1L);
        input.add(2L);
        input.add(3L);
        input.add(null);
        input.add(4L);
        TreeNode root = Harness.toTreeNode(input);
        assertThat(root).isNotNull();
        assertThat(root.val).isEqualTo(1);
        assertThat(root.left.val).isEqualTo(2);
        assertThat(root.right.val).isEqualTo(3);
        assertThat(root.left.left).isNull();
        assertThat(root.left.right.val).isEqualTo(4);
    }

    @Test
    @DisplayName("toTreeNode handles empty / null root")
    void toTreeNode_emptyOrNullRoot() {
        assertThat(Harness.toTreeNode(List.of())).isNull();
        assertThat(Harness.toTreeNode(null)).isNull();
        List<Object> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(Harness.toTreeNode(withNull)).isNull();
    }

    @Test
    @DisplayName("fromTreeNode serializes balanced tree")
    void fromTreeNode_balanced() {
        TreeNode root = new TreeNode(1, new TreeNode(2), new TreeNode(3));
        assertThat(Harness.fromTreeNode(root)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fromTreeNode round-trips with toTreeNode for sparse trees")
    void fromTreeNode_roundTripSparse() {
        List<Object> input = new ArrayList<>();
        input.add(1L);
        input.add(2L);
        input.add(3L);
        input.add(null);
        input.add(4L);
        TreeNode tree = Harness.toTreeNode(input);
        List<Integer> reserialized = Harness.fromTreeNode(tree);
        assertThat(reserialized).hasSize(5);
        assertThat(reserialized.get(0)).isEqualTo(1);
        assertThat(reserialized.get(1)).isEqualTo(2);
        assertThat(reserialized.get(2)).isEqualTo(3);
        assertThat(reserialized.get(3)).isNull();
        assertThat(reserialized.get(4)).isEqualTo(4);
    }

    // adaptArg

    @Test
    @DisplayName("adaptArg unboxes JSON Long to primitive int")
    void adaptArg_intPrimitive() {
        assertThat(Harness.adaptArg(42L, int.class)).isEqualTo(42);
    }

    @Test
    @DisplayName("adaptArg converts JSON List to int[]")
    void adaptArg_intArray() {
        int[] arr = (int[]) Harness.adaptArg(List.of(1L, 2L, 3L), int[].class);
        assertThat(arr).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("adaptArg converts JSON nested List to int[][]")
    void adaptArg_int2DArray() {
        int[][] arr = (int[][]) Harness.adaptArg(
                List.of(List.of(1L, 2L), List.of(3L, 4L)), int[][].class);
        assertThat(arr.length).isEqualTo(2);
        assertThat(arr[0]).containsExactly(1, 2);
        assertThat(arr[1]).containsExactly(3, 4);
    }

    @Test
    @DisplayName("adaptArg converts JSON List to ListNode chain")
    void adaptArg_listNode() {
        ListNode node = (ListNode) Harness.adaptArg(List.of(1L, 2L), ListNode.class);
        assertThat(node.val).isEqualTo(1);
        assertThat(node.next.val).isEqualTo(2);
    }

    @Test
    @DisplayName("adaptArg converts nested List to ListNode[] (problem #7 shape)")
    void adaptArg_listNodeArray() {
        ListNode[] arr = (ListNode[]) Harness.adaptArg(
                List.of(List.of(1L, 4L, 5L), List.of(1L, 3L, 4L)), ListNode[].class);
        assertThat(arr).hasSize(2);
        assertThat(arr[0].val).isEqualTo(1);
        assertThat(arr[0].next.val).isEqualTo(4);
        assertThat(arr[1].val).isEqualTo(1);
        assertThat(arr[1].next.val).isEqualTo(3);
    }

    @Test
    @DisplayName("adaptArg returns null for null reference argument")
    void adaptArg_nullReference() {
        assertThat(Harness.adaptArg(null, String.class)).isNull();
    }

    @Test
    @DisplayName("adaptArg rejects null for primitive parameter")
    void adaptArg_nullPrimitive() {
        assertThatThrownBy(() -> Harness.adaptArg(null, int.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // jsonable

    @Test
    @DisplayName("jsonable returns null for void method")
    void jsonable_voidMethod() throws Exception {
        java.lang.reflect.Method m = SampleSolution.class.getMethod("voidMethod");
        assertThat(Harness.jsonable("ignored", m)).isNull();
    }

    @Test
    @DisplayName("jsonable converts ListNode return to List<Integer>")
    void jsonable_listNodeReturn() throws Exception {
        java.lang.reflect.Method m = SampleSolution.class.getMethod("listNodeMethod");
        ListNode head = new ListNode(7, new ListNode(0, new ListNode(8)));
        Object out = Harness.jsonable(head, m);
        assertThat(out).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Integer> outList = (List<Integer>) out;
        assertThat(outList).containsExactly(7, 0, 8);
    }

    @Test
    @DisplayName("jsonable converts int[] return to List<Integer>")
    void jsonable_intArrayReturn() throws Exception {
        java.lang.reflect.Method m = SampleSolution.class.getMethod("intArrayMethod");
        Object out = Harness.jsonable(new int[] {1, 2, 3}, m);
        assertThat(out).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Integer> outList = (List<Integer>) out;
        assertThat(outList).containsExactly(1, 2, 3);
    }

    /** Fixture used only to obtain method return types via reflection. */
    public static class SampleSolution {
        public void voidMethod() {}
        public ListNode listNodeMethod() { return null; }
        public int[] intArrayMethod() { return null; }
    }
}
