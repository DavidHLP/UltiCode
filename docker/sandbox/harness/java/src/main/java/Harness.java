import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

        JsonParser(String text) {
            this.text = text;
            this.pos = 0;
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
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
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
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
                    return map;
                }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == ']') {
                pos++;
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
            int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
            }
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = text.substring(start, pos);
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
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

    /** Serializes a value (produced by {@link #parseJson} or {@link #jsonable}) to JSON text. */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object value) {
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
        if (value instanceof String s) {
            writeString(sb, s);
            return;
        }
        if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                writeJson(sb, item);
                first = false;
            }
            sb.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeJson(sb, entry.getValue());
                first = false;
            }
            sb.append('}');
            return;
        }
        if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                writeJson(sb, Array.get(value, i));
            }
            sb.append(']');
            return;
        }
        writeString(sb, value.toString());
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isFinite(d) && d == Math.floor(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
                return;
            }
        }
        sb.append(n);
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
        return jsonableValue(value);
    }

    static Object jsonableValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ListNode node) {
            return fromListNode(node);
        }
        if (value instanceof TreeNode node) {
            return fromTreeNode(node);
        }
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value;
        }
        if (value instanceof Character c) {
            return String.valueOf(c);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(jsonableValue(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), jsonableValue(entry.getValue()));
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(jsonableValue(Array.get(value, i)));
            }
            return out;
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
        // LinkedList (not ArrayDeque) — must hold nulls representing absent
        // children so the LeetCode level-order encoding can be produced.
        Queue<TreeNode> queue = new java.util.LinkedList<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            if (node == null) {
                raw.add(null);
            } else {
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
