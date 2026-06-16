package com.ulticode.modules.submission.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析题目 starter_code,推断 {@code Solution} 方法每个参数的 OJ 数据结构类型
 * (LeetCode 风格的 {@code ListNode} / {@code TreeNode} 及其数组)。
 *
 * <p>背景:D-form 沙箱 harness 的 {@code adapt_arg} 只有在拿到类型提示时,
 * 才会把数组形式的输入(如 {@code [2,4,3]})反序列化成真正的 {@code ListNode}
 * / {@code TreeNode}。类型提示来自两种信号 —— 用户代码的方法注解,或
 * input.json 里 input spec 的 {@code type} 字段。当用户代码不带注解
 * (LeetCode 惯例常见,如 {@code def addTwoNumbers(self, l1, l2)})时,只有
 * 后端权威地从题目方法签名补上的 {@code type} 才能让链表/树题正确判题。
 *
 * <p>本工具把题目的 starter_code(题目级权威方法签名)解析成「按参数位置顺序」
 * 的 OJ 类型列表:元素为 {@code "ListNode"} / {@code "TreeNode"} /
 * {@code "ListNode[]"} / {@code "TreeNode[]"},或 {@code null}(该参数不是
 * OJ 数据结构,如 {@code int} / {@code String},harness 透传即可)。
 *
 * <h2>支持的语言</h2>
 * <ul>
 *   <li><b>python</b>:{@code class Solution: def m(self, l1: Optional[ListNode], ...)}</li>
 *   <li><b>java</b>:{@code class Solution { ListNode m(ListNode l1, ...) }}</li>
 *   <li><b>cpp</b>:{@code class Solution { ListNode* m(ListNode* l1, ...) }}</li>
 * </ul>
 * 其他语言(如 javascript / c)直接返回空列表(它们不在 D-form 支持集内)。
 *
 * <h2>健壮性契约</h2>
 * <p>本类对 starter_code 的格式<strong>不做任何假设</strong>:注释、缺省值、
 * 多个方法、缺类型注解、甚至完全无法识别的内容,都必须<strong>安全退化</strong>
 * —— 返回空列表或全 {@code null} 列表,<strong>绝不抛异常</strong>。退化后等价于
 * 「不给 type 提示」,与修复前的行为一致,不会让现状变差。
 *
 * <p>纯静态工具类,无 Spring 依赖,便于单测。
 */
public final class OJSignatureParser {

    private OJSignatureParser() {
    }

    /** 定位 {@code class Solution} 声明(允许 {@code class Solution:} 或 {@code class Solution {}})。 */
    private static final Pattern CLASS_SOLUTION = Pattern.compile("class\\s+Solution\\b");

    /** python 方法签名:{@code def <name>(<params>)}。参数内无圆括号,故 {@code [^)]*} 安全。 */
    private static final Pattern PYTHON_DEF = Pattern.compile("def\\s+\\w+\\s*\\(([^)]*)\\)");

    /**
     * java / cpp 方法签名:{@code <name>(<params>) \{}。
     * 取首个「标识符 + 圆括号参数 + 花括号」的匹配;{@code class Solution \{} 本身
     * 因 {@code Solution} 后是 {@code \{} 而非 {@code (} 不会命中。注释里的方法
     * 定义在 {@code class Solution} 之前,substring 之后已剔除。
     */
    private static final Pattern BRACE_METHOD = Pattern.compile("\\b\\w+\\s*\\(([^)]*)\\)\\s*\\{");

    /**
     * 解析 starter_code,返回按参数位置顺序的 OJ 类型列表。
     *
     * @param starterCode 题目的 starter code(可能为 null / 空)
     * @param language    语言标识(python / java / cpp;大小写/空白不敏感)
     * @return 不可变的 OJ 类型列表(元素可含 null);解析不到时返回空列表
     */
    public static List<String> parse(String starterCode, String language) {
        if (starterCode == null || starterCode.isBlank() || language == null) {
            return List.of();
        }
        String lang = language.trim().toLowerCase();
        List<String> parsed = switch (lang) {
            case "python", "python3" -> parsePython(starterCode);
            case "java" -> parseBraced(starterCode, "java");
            case "cpp", "c++" -> parseBraced(starterCode, "cpp");
            default -> List.of();
        };
        return parsed;
    }

    // ── python ───────────────────────────────────────────────────────────────

    private static List<String> parsePython(String code) {
        String after = afterClassSolution(code);
        if (after == null) {
            return List.of();
        }
        Matcher m = PYTHON_DEF.matcher(after);
        if (!m.find()) {
            return List.of();
        }
        return parseParams(m.group(1), "python");
    }

    // ── java / cpp ───────────────────────────────────────────────────────────

    private static List<String> parseBraced(String code, String language) {
        String after = afterClassSolution(code);
        if (after == null) {
            return List.of();
        }
        Matcher m = BRACE_METHOD.matcher(after);
        if (!m.find()) {
            return List.of();
        }
        return parseParams(m.group(1), language);
    }

    // ── 参数解析 ──────────────────────────────────────────────────────────────

    /**
     * 把方法签名的参数串解析成按位置的 OJ 类型列表。
     *
     * <p>python 会跳过首个 {@code self};java / cpp 取「最后一个空白左侧」作为
     * 类型子串(LeetCode 风格类型前置且不含空白,如 {@code ListNode[]}/{@code vector<ListNode*>})。
     */
    private static List<String> parseParams(String params, String language) {
        List<String> result = new ArrayList<>();
        if (params == null || params.isBlank()) {
            return result;
        }
        for (String token : splitTopLevelCommas(params)) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            if ("python".equals(language)) {
                // 形如 name: annotation 或 name(无注解) 或 name: ann = default
                String name = t.contains(":") ? t.split(":", 2)[0].trim() : t.split("=", 2)[0].trim();
                if ("self".equalsIgnoreCase(name)) {
                    continue;
                }
                String ann = "";
                if (t.contains(":")) {
                    ann = t.split(":", 2)[1].trim();
                    if (ann.contains("=")) {
                        ann = ann.split("=", 2)[0].trim();
                    }
                }
                result.add(classifyOjType(ann, language));
            } else {
                // java / cpp:类型前置,取最后一个空白左侧
                String typeStr = t.contains(" ") ? t.substring(0, t.lastIndexOf(' ')).trim() : t;
                result.add(classifyOjType(typeStr, language));
            }
        }
        return result;
    }

    /**
     * 判定类型子串对应的 OJ 类型。
     *
     * <p>只关心 {@code ListNode} / {@code TreeNode} 及其数组;其余一律返回 {@code null}
     * (让 harness 按值透传)。
     *
     * @param typeStr 类型注解/声明子串(python 注解或 java/cpp 类型)
     * @param language python / java / cpp
     * @return {@code "ListNode"} / {@code "TreeNode"} / {@code "ListNode[]"} /
     *         {@code "TreeNode[]"},或 {@code null}
     */
    private static String classifyOjType(String typeStr, String language) {
        if (typeStr == null || typeStr.isBlank()) {
            return null;
        }
        boolean hasList = typeStr.contains("ListNode");
        boolean hasTree = typeStr.contains("TreeNode");
        if (!hasList && !hasTree) {
            return null;
        }
        String leaf = hasList ? "ListNode" : "TreeNode";
        boolean isArray = switch (language) {
            case "python" -> typeStr.contains("List[") || typeStr.contains("list[");
            case "cpp" -> typeStr.contains("vector") || typeStr.contains("[]");
            default /* java */ -> typeStr.contains("[]");
        };
        return isArray ? leaf + "[]" : leaf;
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    /** 返回 {@code class Solution} 之后的子串;找不到返回 {@code null}。 */
    private static String afterClassSolution(String code) {
        Matcher m = CLASS_SOLUTION.matcher(code);
        return m.find() ? code.substring(m.end()) : null;
    }

    /**
     * 按顶层逗号分割(忽略 {@code [] () <>} 内的逗号),保证
     * {@code List[Optional[ListNode]]} / {@code vector<ListNode*>} 这类含
     * 嵌套分隔符的类型不被误切。
     */
    private static List<String> splitTopLevelCommas(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '<') {
                depth++;
            } else if (c == ']' || c == ')' || c == '>') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }
}
