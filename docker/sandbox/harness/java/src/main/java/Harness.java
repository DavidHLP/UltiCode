import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Self-contained utility class for the UltiCode sandbox harness.
 *
 * <p>Contains four functional groups:
 * <ul>
 *   <li><b>JSON parser/serializer</b> — recursive-descent over a {@code String}.
 *       No third-party dependency (sandbox image has none).</li>
 *   <li><b>Argument adapter</b> — converts a parsed JSON value to the target
 *       Java type declared by the user's method signature.</li>
 *   <li><b>Result jsonable</b> — converts a user-returned value (including
 *       {@link ListNode}, {@link TreeNode}, arrays) into a JSON-serializable
 *       shape.</li>
 *   <li><b>Data-structure helpers</b> — {@link #toListNode}, {@link #fromListNode},
 *       {@link #toTreeNode}, {@link #fromTreeNode} for OJ-style structures.</li>
 * </ul>
 *
 * <p>All public methods are deterministic and side-effect free.
 */
public final class Harness {

    /** Cycle guard for malicious or buggy linked lists. */
    static final int LIST_NODE_TRAVERSAL_CAP = 100_000;
    /** Hard cap on JSON parse / serialize / jsonable nesting (defense vs. stack overflow). */
    static final int MAX_NESTING_DEPTH = 512;
    /** Hard cap on result jsonable node count (defense vs. memory exhaustion). */
    static final int MAX_JSONABLE_NODES = 1_000_000;
    /** Hard cap on serialized envelope size (defense vs. unbounded String growth). */
    static final int MAX_JSON_OUTPUT_BYTES = 8 * 1024 * 1024;

    private Harness() {}

    // ─── JSON parser ────────────────────────────────────────────────────────

    /**
     * Parses a JSON document into one of:
     * {@code null}, {@link Boolean}, {@link Long}, {@link Double},
     * {@link String}, {@code List<Object>}, {@code Map<String, Object>}.
     *
     * @throws IllegalArgumentException on malformed input
     */
    public static Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        JsonParser parser = new JsonParser(json);
        try {
            parser.skipWhitespace();
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (parser.pos < json.length()) {
                throw new IllegalArgumentException(
                        "Trailing data at position " + parser.pos);
            }
            return value;
        } catch (StringIndexOutOfBoundsException eof) {
            // Bubble up as the documented exception type so callers don't
            // need to know about charAt(pos) implementation details.
            throw new IllegalArgumentException("Unexpected EOF while parsing JSON", eof);
        }
    }

    private static final class JsonParser {
        private final String text;
        private int pos;
        private int depth;

        JsonParser(String text) {
            this.text = text;
            this.pos = 0;
            this.depth = 0;
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            if (depth > MAX_NESTING_DEPTH) {
                throw new IllegalArgumentException(
                        "JSON nesting exceeds limit " + MAX_NESTING_DEPTH + " at position " + pos);
            }
            skipWhitespace();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unexpected EOF");
            }
            char c = text.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("Unexpected character " + c + " at position " + pos);
        }

        Map<String, Object> parseObject() {
            depth++;
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' at position " + pos);
                }
                pos++;
                map.put(key, parseValue());
                skipWhitespace();
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("Unexpected EOF inside object");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    depth--;
                    return map;
                }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }

        List<Object> parseArray() {
            depth++;
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("Unexpected EOF inside array");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    depth--;
                    return list;
                }
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }

        String parseString() {
            if (text.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected '\"' at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new IllegalArgumentException(
                            "Unescaped control character " + (int) c + " in string at position " + pos);
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= text.length()) {
                        throw new IllegalArgumentException("Unexpected EOF after '\\'");
                    }
                    char esc = text.charAt(pos);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 >= text.length()) {
                                throw new IllegalArgumentException("Incomplete \\u escape at " + pos);
                            }
                            int code = Integer.parseInt(text.substring(pos + 1, pos + 5), 16);
                            sb.append((char) code);
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape '\\" + esc + "'");
                    }
                    pos++;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Object parseNumber() {
            // Strict JSON.org grammar: -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?
            // Rejects: leading zeros (01), naked dot (1.), missing exponent digits,
            // Infinity/NaN literals (which JSON forbids).
            int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("Lone '-' at position " + start);
                }
            }
            // Integer part
            char c = text.charAt(pos);
            if (c == '0') {
                pos++;
                // After leading 0, next must be '.', 'e', 'E', or end of number
                if (pos < text.length()) {
                    char nc = text.charAt(pos);
                    if (nc >= '0' && nc <= '9') {
                        throw new IllegalArgumentException(
                                "Leading zero in number at position " + start);
                    }
                }
            } else if (c >= '1' && c <= '9') {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            } else {
                throw new IllegalArgumentException("Expected digit at position " + pos);
            }
            boolean isFloat = false;
            // Fraction part
            if (pos < text.length() && text.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                int fracStart = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == fracStart) {
                    throw new IllegalArgumentException(
                            "Naked decimal point at position " + (pos - 1));
                }
            }
            // Exponent part
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                int expStart = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == expStart) {
                    throw new IllegalArgumentException(
                            "Empty exponent at position " + (pos - 1));
                }
            }
            String num = text.substring(start, pos);
            if (isFloat) {
                double d = Double.parseDouble(num);
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException(
                            "Non-finite number " + num + " (parsed to " + d + ")");
                }
                return d;
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException nfe) {
                // > Long.MAX_VALUE — reject rather than silently truncating to Double.
                throw new IllegalArgumentException(
                        "Integer out of range (>Long.MAX_VALUE) at position " + start + ": " + num, nfe);
            }
        }

        Boolean parseBoolean() {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Expected true/false at position " + pos);
        }

        Object parseNull() {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at position " + pos);
        }
    }

    // ─── JSON serializer ────────────────────────────────────────────────────

    /** Serializes a value (produced by {@link #parseJson} or {@link #jsonable}) to JSON text.
     *  Detects identity cycles and enforces {@link #MAX_NESTING_DEPTH},
     *  {@link #MAX_JSON_OUTPUT_BYTES}.
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        SerCtx ctx = new SerCtx();
        writeJson(sb, value, ctx);
        return sb.toString();
    }

    private static final class SerCtx {
        final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        int depth = 0;
    }

    private static void checkOutputBudget(StringBuilder sb) {
        // Approximate: 1 char ≈ 1-3 UTF-8 bytes worst case; check char count as a cheap upper bound.
        if (sb.length() > MAX_JSON_OUTPUT_BYTES) {
            throw new IllegalArgumentException(
                    "Serialized output exceeds limit " + MAX_JSON_OUTPUT_BYTES + " bytes");
        }
    }

    /**
     * Strict check helper for use AFTER appending. writeString / writeNumber /
     * the main writeJson dispatch call this on the way out so a single large
     * scalar cannot blow past {@link #MAX_JSON_OUTPUT_BYTES} in one shot
     * before the recursive pre-check would re-evaluate.
     */
    private static void assertWithinBudget(StringBuilder sb) {
        if (sb.length() > MAX_JSON_OUTPUT_BYTES) {
            throw new IllegalArgumentException(
                    "Serialized output exceeds limit " + MAX_JSON_OUTPUT_BYTES + " bytes");
        }
    }

    private static void writeJson(StringBuilder sb, Object value, SerCtx ctx) {
        if (ctx.depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException(
                    "Serialization nesting exceeds limit " + MAX_NESTING_DEPTH);
        }
        checkOutputBudget(sb);
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Boolean) {
            sb.append(value);
            return;
        }
        if (value instanceof Number n) {
            writeNumber(sb, n);
            return;
        }
        if (value instanceof Character c) {
            writeString(sb, String.valueOf(c));
            return;
        }
        if (value instanceof String str) {
            writeString(sb, str);
            return;
        }
        if (value instanceof List<?> list) {
            if (ctx.seen.put(list, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (List)");
            }
            try {
                ctx.depth++;
                sb.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) sb.append(',');
                    writeJson(sb, item, ctx);
                    first = false;
                }
                sb.append(']');
            } finally {
                ctx.depth--;
                ctx.seen.remove(list);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (ctx.seen.put(map, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (Map)");
            }
            try {
                ctx.depth++;
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) sb.append(',');
                    writeString(sb, String.valueOf(entry.getKey()));
                    sb.append(':');
                    writeJson(sb, entry.getValue(), ctx);
                    first = false;
                }
                sb.append('}');
            } finally {
                ctx.depth--;
                ctx.seen.remove(map);
            }
            return;
        }
        if (value.getClass().isArray()) {
            if (ctx.seen.put(value, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (array)");
            }
            try {
                ctx.depth++;
                sb.append('[');
                int len = Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(',');
                    writeJson(sb, Array.get(value, i), ctx);
                }
                sb.append(']');
            } finally {
                ctx.depth--;
                ctx.seen.remove(value);
            }
            return;
        }
        writeString(sb, value.toString());
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException(
                        "Cannot serialize non-finite number " + d);
            }
            if (d == Math.floor(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
                assertWithinBudget(sb);
                return;
            }
        }
        sb.append(n);
        assertWithinBudget(sb);
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        assertWithinBudget(sb);
    }

    /** Canonicalizes a JSON document by parse → serialize. Used for output comparison. */
    public static String normalizeJson(String json) {
        return toJson(parseJson(json));
    }

    // ─── Argument adapter ───────────────────────────────────────────────────

    /**
     * Adapts a parsed JSON value to the declared Java parameter type. Boxed
     * numerics, primitives, arrays, ListNode and TreeNode are all handled.
     *
     * @throws IllegalArgumentException if the target type is unsupported
     */
    public static Object adaptArg(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot pass null to primitive " + targetType.getName());
            }
            return null;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((Number) value).longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value;
        }
        if (targetType == char.class || targetType == Character.class) {
            String s = value.toString();
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Cannot adapt empty string to char");
            }
            return s.charAt(0);
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == ListNode.class) {
            if (!(value instanceof List<?> list)) {
                throw new IllegalArgumentException("ListNode argument requires JSON array, got " + value.getClass());
            }
            return toListNode(list);
        }
        if (targetType == TreeNode.class) {
            if (!(value instanceof List<?> list)) {
                throw new IllegalArgumentException("TreeNode argument requires JSON array, got " + value.getClass());
            }
            return toTreeNode(list);
        }
        if (targetType.isArray()) {
            if (!(value instanceof List<?> list)) {
                throw new IllegalArgumentException("Array argument requires JSON array, got " + value.getClass());
            }
            return toArray(list, targetType.getComponentType());
        }
        if (List.class.isAssignableFrom(targetType)) {
            // Generics are erased at runtime; pass the parsed List through unchanged.
            // User code that needs List<Integer> must accept Number widening manually.
            return value;
        }
        throw new IllegalArgumentException("Unsupported parameter type: " + targetType.getName());
    }

    private static Object toArray(List<?> list, Class<?> componentType) {
        int len = list.size();
        Object array = Array.newInstance(componentType, len);
        for (int i = 0; i < len; i++) {
            Array.set(array, i, adaptArg(list.get(i), componentType));
        }
        return array;
    }

    // ─── Result jsonable ────────────────────────────────────────────────────

    /**
     * Converts a user-returned value into a JSON-serializable shape.
     * The reflected return type of {@code method} is used as a hint but the
     * conversion is value-driven (works even if user returns a subtype).
     */
    public static Object jsonable(Object value, Method method) {
        Class<?> returnType = (method != null) ? method.getReturnType() : Object.class;
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        // OJ policy (matches LeetCode-style judges): a null return from a
        // list-like method (ListNode / TreeNode / array / List<T>) is
        // reported as the empty representation [] rather than JSON null,
        // because the OJ test data for "empty input" cases uses [] as
        // the canonical answer. Without this, LeetCode-style code that
        // returns null on empty input fails the empty-case with a
        // Wrong Answer verdict (null vs [] mismatch).
        if (value == null && isListLike(returnType)) {
            return new java.util.ArrayList<>();
        }
        return jsonableValue(value);
    }

    private static boolean isListLike(Class<?> t) {
        if (t.isArray()) return true;
        if (List.class.isAssignableFrom(t)) return true;
        return ListNode.class.isAssignableFrom(t) || TreeNode.class.isAssignableFrom(t);
    }

    static Object jsonableValue(Object value) {
        return jsonableValue(value, new JsonableCtx());
    }

    static final class JsonableCtx {
        final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        int depth = 0;
        int nodeCount = 0;
    }

    static Object jsonableValue(Object value, JsonableCtx ctx) {
        if (++ctx.nodeCount > MAX_JSONABLE_NODES) {
            throw new IllegalArgumentException(
                    "Result exceeds node limit " + MAX_JSONABLE_NODES);
        }
        if (ctx.depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException(
                    "Result nesting exceeds limit " + MAX_NESTING_DEPTH);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof ListNode node) {
            return fromListNode(node);
        }
        if (value instanceof TreeNode node) {
            return fromTreeNode(node);
        }
        if (value instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException(
                            "Non-finite number in result: " + d);
                }
            }
            return value;
        }
        if (value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Character c) {
            return String.valueOf(c);
        }
        if (value instanceof List<?> list) {
            if (ctx.seen.put(list, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (List)");
            }
            try {
                ctx.depth++;
                List<Object> out = new ArrayList<>(list.size());
                for (Object item : list) {
                    out.add(jsonableValue(item, ctx));
                }
                return out;
            } finally {
                ctx.depth--;
                ctx.seen.remove(list);
            }
        }
        if (value instanceof Map<?, ?> map) {
            if (ctx.seen.put(map, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (Map)");
            }
            try {
                ctx.depth++;
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.put(String.valueOf(entry.getKey()), jsonableValue(entry.getValue(), ctx));
                }
                return out;
            } finally {
                ctx.depth--;
                ctx.seen.remove(map);
            }
        }
        if (value.getClass().isArray()) {
            if (ctx.seen.put(value, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference in result (array)");
            }
            try {
                ctx.depth++;
                int len = Array.getLength(value);
                List<Object> out = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    out.add(jsonableValue(Array.get(value, i), ctx));
                }
                return out;
            } finally {
                ctx.depth--;
                ctx.seen.remove(value);
            }
        }
        return value.toString();
    }

    // ─── ListNode helpers ───────────────────────────────────────────────────

    /** Builds a linked list from a JSON-parsed list of numbers; returns null for empty/null input. */
    public static ListNode toListNode(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        ListNode head = new ListNode(((Number) list.get(0)).intValue());
        ListNode cur = head;
        for (int i = 1; i < list.size(); i++) {
            cur.next = new ListNode(((Number) list.get(i)).intValue());
            cur = cur.next;
        }
        return head;
    }

    /** Serializes a linked list to {@code List<Integer>}; cycle-safe via {@link #LIST_NODE_TRAVERSAL_CAP}. */
    public static List<Integer> fromListNode(ListNode head) {
        List<Integer> out = new ArrayList<>();
        ListNode cur = head;
        while (cur != null && out.size() < LIST_NODE_TRAVERSAL_CAP) {
            out.add(cur.val);
            cur = cur.next;
        }
        return out;
    }

    // ─── TreeNode helpers (LeetCode level-order BFS) ────────────────────────

    /** Builds a binary tree from level-order array with null placeholders. */
    public static TreeNode toTreeNode(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Object root = list.get(0);
        if (root == null) {
            return null;
        }
        TreeNode rootNode = new TreeNode(((Number) root).intValue());
        Queue<TreeNode> queue = new ArrayDeque<>();
        queue.offer(rootNode);
        int i = 1;
        while (!queue.isEmpty() && i < list.size()) {
            TreeNode node = queue.poll();
            if (i < list.size()) {
                Object leftVal = list.get(i++);
                if (leftVal != null) {
                    node.left = new TreeNode(((Number) leftVal).intValue());
                    queue.offer(node.left);
                }
            }
            if (i < list.size()) {
                Object rightVal = list.get(i++);
                if (rightVal != null) {
                    node.right = new TreeNode(((Number) rightVal).intValue());
                    queue.offer(node.right);
                }
            }
        }
        return rootNode;
    }

    /** Serializes a binary tree to LeetCode level-order list with null placeholders, trailing nulls trimmed. */
    public static List<Integer> fromTreeNode(TreeNode root) {
        List<Integer> raw = new ArrayList<>();
        if (root == null) {
            return raw;
        }
        // CR fix: identity-based cycle guard + shared MAX_JSONABLE_NODES cap so a
        // user-returned cyclic tree (e.g. root.left = root) gets converted to a
        // per-case Runtime Error via the IAE, not an OOM that takes the whole
        // harness down. Same contract as fromListNode.
        IdentityHashMap<TreeNode, Boolean> visited = new IdentityHashMap<>();
        // LinkedList (not ArrayDeque) — must hold nulls representing absent
        // children so the LeetCode level-order encoding can be produced.
        Queue<TreeNode> queue = new java.util.LinkedList<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            if (node == null) {
                raw.add(null);
            } else {
                if (visited.put(node, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(
                            "Cyclic reference in TreeNode result");
                }
                if (raw.size() > MAX_JSONABLE_NODES) {
                    throw new IllegalArgumentException(
                            "TreeNode exceeds node limit " + MAX_JSONABLE_NODES);
                }
                raw.add(node.val);
                queue.offer(node.left);
                queue.offer(node.right);
            }
        }
        int end = raw.size();
        while (end > 0 && raw.get(end - 1) == null) {
            end--;
        }
        return new ArrayList<>(raw.subList(0, end));
    }
}
