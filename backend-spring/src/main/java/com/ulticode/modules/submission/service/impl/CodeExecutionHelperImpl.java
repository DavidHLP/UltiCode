package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of CodeExecutionHelper.
 * All per-language logic, result parsing, and utilities -- no Docker, no security.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionHelperImpl implements CodeExecutionHelper {

    private final ObjectMapper objectMapper;

    // ── Sandbox-embedded Java source constants ──────────────────────────────
    // These are concatenated into /tmp/Main.java inside the Docker sandbox.
    // The sandbox has JDK 17 but NO external libraries (no Jackson, no Gson),
    // so all JSON handling must be self-contained.

    /** Standard LeetCode-style singly-linked list node. */
    private static final String LIST_NODE_CLASS =
            "class ListNode {\n" +
            "    int val;\n" +
            "    ListNode next;\n" +
            "    ListNode() {}\n" +
            "    ListNode(int val) { this.val = val; }\n" +
            "    ListNode(int val, ListNode next) { this.val = val; this.next = next; }\n" +
            "}\n";

    /**
     * Minimal recursive-descent JSON parser embedded in the sandbox Main class.
     *
     * <p>Implemented as three sibling static methods ({@code parseJson}, {@code _j_ws},
     * {@code _j_parse}) plus three static fields ({@code _j_s}, {@code _j_p}, {@code _j_len})
     * for shared cursor state. Java does not allow nested function declarations inside a
     * method body, so {@code ws} and {@code _parse} must live at the same level as
     * {@code parseJson}.
     */
    private static final String JAVA_JSON_PARSER =
            "static String _j_s;\n" +
            "static int _j_p, _j_len;\n" +
            "static void _j_ws() { while (_j_p < _j_len && _j_s.charAt(_j_p) <= ' ') _j_p++; }\n" +
            "static Object _j_parse() {\n" +
            "    _j_ws(); if (_j_p >= _j_len) return null;\n" +
            "    char c = _j_s.charAt(_j_p);\n" +
            "    if (c == '\"') {\n" +
            "        _j_p++; StringBuilder sb = new StringBuilder();\n" +
            "        while (_j_p < _j_len && _j_s.charAt(_j_p) != '\"') {\n" +
            "            if (_j_s.charAt(_j_p) == '\\\\') { _j_p++; if (_j_p < _j_len) sb.append(_j_s.charAt(_j_p)); }\n" +
            "            else sb.append(_j_s.charAt(_j_p));\n" +
            "            _j_p++;\n" +
            "        }\n" +
            "        if (_j_p < _j_len) _j_p++;\n" +
            "        return sb.toString();\n" +
            "    }\n" +
            "    if (c == '[') {\n" +
            "        _j_p++; List<Object> r = new ArrayList<>(); _j_ws();\n" +
            "        if (_j_p < _j_len && _j_s.charAt(_j_p) != ']') {\n" +
            "            r.add(_j_parse()); _j_ws();\n" +
            "            while (_j_p < _j_len && _j_s.charAt(_j_p) == ',') { _j_p++; r.add(_j_parse()); _j_ws(); }\n" +
            "        }\n" +
            "        if (_j_p < _j_len) _j_p++;\n" +
            "        return r;\n" +
            "    }\n" +
            "    if (c == '{') {\n" +
            "        _j_p++; Map<String,Object> r = new LinkedHashMap<>(); _j_ws();\n" +
            "        if (_j_p < _j_len && _j_s.charAt(_j_p) != '}') {\n" +
            "            String k = (String)_j_parse(); _j_ws(); _j_p++;\n" +
            "            r.put(k, _j_parse()); _j_ws();\n" +
            "            while (_j_p < _j_len && _j_s.charAt(_j_p) == ',') {\n" +
            "                _j_p++; k = (String)_j_parse(); _j_ws(); _j_p++;\n" +
            "                r.put(k, _j_parse()); _j_ws();\n" +
            "            }\n" +
            "        }\n" +
            "        if (_j_p < _j_len) _j_p++;\n" +
            "        return r;\n" +
            "    }\n" +
            "    if (c == 't') { _j_p += 4; return Boolean.TRUE; }\n" +
            "    if (c == 'f') { _j_p += 5; return Boolean.FALSE; }\n" +
            "    if (c == 'n') { _j_p += 4; return null; }\n" +
            "    int st = _j_p;\n" +
            "    if (_j_p < _j_len && _j_s.charAt(_j_p) == '-') _j_p++;\n" +
            "    boolean hasExp = false;\n" +
            "    while (_j_p < _j_len) {\n" +
            "        char ch = _j_s.charAt(_j_p);\n" +
            "        if (Character.isDigit(ch) || ch == '.') { _j_p++; continue; }\n" +
            "        if ((ch == 'e' || ch == 'E') && !hasExp) { hasExp = true; _j_p++; continue; }\n" +
            "        if (hasExp && (ch == '+' || ch == '-')) { _j_p++; continue; }\n" +
            "        break;\n" +
            "    }\n" +
            "    String n = _j_s.substring(st, _j_p);\n" +
            "    return n.contains(\".\") || hasExp ? (Object)Double.parseDouble(n) : (Object)Long.parseLong(n);\n" +
            "}\n" +
            "static Object parseJson(String s) {\n" +
            "    _j_s = s; _j_p = 0; _j_len = s.length();\n" +
            "    return _j_parse();\n" +
            "}\n";

    /** JSON serializer that handles null, String, Number, Boolean, List, Map, and Java arrays. */
    private static final String JAVA_JSON_SERIALIZER =
            "static String toJson(Object o) {\n" +
            "    if (o == null) return \"null\";\n" +
            "    if (o instanceof String) {\n" +
            "        String s = (String)o;\n" +
            "        StringBuilder sb = new StringBuilder(\"\\\"\");\n" +
            "        for (int i = 0; i < s.length(); i++) {\n" +
            "            char ch = s.charAt(i);\n" +
            "            if (ch == '\\\\') sb.append(\"\\\\\\\\\");\n" +
            "            else if (ch == '\"') sb.append(\"\\\\\\\"\");\n" +
            "            else if (ch == '\\n') sb.append(\"\\\\n\");\n" +
            "            else sb.append(ch);\n" +
            "        }\n" +
            "        sb.append('\"'); return sb.toString();\n" +
            "    }\n" +
            "    if (o instanceof Number || o instanceof Boolean) return o.toString();\n" +
            "    if (o instanceof List) {\n" +
            "        List<?> l = (List<?>)o; StringBuilder sb = new StringBuilder(\"[\");\n" +
            "        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(','); sb.append(toJson(l.get(i))); }\n" +
            "        sb.append(']'); return sb.toString();\n" +
            "    }\n" +
            "    if (o instanceof Map) {\n" +
            "        Map<?,?> m = (Map<?,?>)o; StringBuilder sb = new StringBuilder(\"{\");\n" +
            "        boolean f = true;\n" +
            "        for (Map.Entry<?,?> e : m.entrySet()) {\n" +
            "            if (!f) sb.append(','); f = false;\n" +
            "            sb.append(toJson(e.getKey())).append(':').append(toJson(e.getValue())); }\n" +
            "        sb.append('}'); return sb.toString();\n" +
            "    }\n" +
            "    if (o.getClass().isArray()) {\n" +
            "        int len = java.lang.reflect.Array.getLength(o);\n" +
            "        StringBuilder sb = new StringBuilder(\"[\");\n" +
            "        for (int i = 0; i < len; i++) { if (i > 0) sb.append(','); sb.append(toJson(java.lang.reflect.Array.get(o, i))); }\n" +
            "        sb.append(']'); return sb.toString();\n" +
            "    }\n" +
            "    return o.toString();\n" +
            "}\n";

    /**
     * Argument adapter and ListNode conversion utilities (embedded in Main class).
     * Uses reflection parameter types to convert JSON-parsed values.
     */
    private static final String JAVA_ARG_ADAPTER =
            "static ListNode toListNode(Object val) {\n" +
            "    if (val == null) return null;\n" +
            "    List<?> list = (List<?>) val;\n" +
            "    ListNode dummy = new ListNode(0), cur = dummy;\n" +
            "    for (Object item : list) cur = cur.next = new ListNode(((Number)item).intValue());\n" +
            "    return dummy.next;\n" +
            "}\n" +
            "static Object fromListNode(ListNode node) {\n" +
            "    List<Integer> r = new ArrayList<>();\n" +
            "    int seen = 0;\n" +
            "    while (node != null && seen++ < 10000) { r.add(node.val); node = node.next; }\n" +
            "    return r;\n" +
            "}\n" +
            "static Object adaptArg(Object val, Class<?> type) {\n" +
            "    if (val == null) {\n" +
            "        if (type.isPrimitive()) {\n" +
            "            if (type == int.class) return 0;\n" +
            "            if (type == long.class) return 0L;\n" +
            "            if (type == double.class) return 0.0;\n" +
            "            if (type == boolean.class) return false;\n" +
            "            if (type == char.class) return '\\0';\n" +
            "        }\n" +
            "        return null;\n" +
            "    }\n" +
            "    if (type == int.class || type == Integer.class) return ((Number)val).intValue();\n" +
            "    if (type == long.class || type == Long.class) return ((Number)val).longValue();\n" +
            "    if (type == double.class || type == Double.class) return ((Number)val).doubleValue();\n" +
            "    if (type == float.class || type == Float.class) return ((Number)val).floatValue();\n" +
            "    if (type == boolean.class || type == Boolean.class) return val;\n" +
            "    if (type == char.class || type == Character.class) return val.toString().charAt(0);\n" +
            "    if (type == String.class) return val.toString();\n" +
            "    if (type == ListNode.class) return toListNode(val);\n" +
            "    if (type.isArray()) {\n" +
            "        if (type.getComponentType() == int.class) {\n" +
            "            List<?> l = (List<?>)val; int[] a = new int[l.size()];\n" +
            "            for (int i = 0; i < l.size(); i++) a[i] = ((Number)l.get(i)).intValue(); return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == long.class) {\n" +
            "            List<?> l = (List<?>)val; long[] a = new long[l.size()];\n" +
            "            for (int i = 0; i < l.size(); i++) a[i] = ((Number)l.get(i)).longValue(); return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == double.class) {\n" +
            "            List<?> l = (List<?>)val; double[] a = new double[l.size()];\n" +
            "            for (int i = 0; i < l.size(); i++) a[i] = ((Number)l.get(i)).doubleValue(); return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == String.class) {\n" +
            "            List<?> l = (List<?>)val; String[] a = new String[l.size()];\n" +
            "            for (int i = 0; i < l.size(); i++) a[i] = l.get(i).toString(); return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == ListNode.class) {\n" +
            "            List<?> l = (List<?>)val; ListNode[] a = new ListNode[l.size()];\n" +
            "            for (int i = 0; i < l.size(); i++) a[i] = toListNode(l.get(i)); return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == int[].class) {\n" +
            "            List<?> l = (List<?>)val; int[][] a = new int[l.size()][];\n" +
            "            for (int i = 0; i < l.size(); i++) {\n" +
            "                List<?> row = (List<?>)l.get(i); int[] r = new int[row.size()];\n" +
            "                for (int j = 0; j < row.size(); j++) r[j] = ((Number)row.get(j)).intValue(); a[i] = r;\n" +
            "            }\n" +
            "            return a;\n" +
            "        }\n" +
            "        if (type.getComponentType() == String[].class) {\n" +
            "            List<?> l = (List<?>)val; String[][] a = new String[l.size()][];\n" +
            "            for (int i = 0; i < l.size(); i++) {\n" +
            "                List<?> row = (List<?>)l.get(i); a[i] = row.toArray(new String[0]);\n" +
            "            }\n" +
            "            return a;\n" +
            "        }\n" +
            "    }\n" +
            "    if (val instanceof List && type.isAssignableFrom(List.class)) return val;\n" +
            "    return val;\n" +
            "}\n";

    /** Result jsonifier: converts method return values to JSON-serializable form. */
    private static final String JAVA_RESULT_JSONABLE =
            "static Object jsonable(Object result, Method m) {\n" +
            "    if (result == null) return null;\n" +
            "    Class<?> rt = m.getReturnType();\n" +
            "    if (rt == void.class) return null;\n" +
            "    if (rt == ListNode.class) return fromListNode((ListNode) result);\n" +
            "    if (rt.isArray()) {\n" +
            "        if (rt.getComponentType() == int.class) {\n" +
            "            int[] a = (int[]) result; List<Integer> r = new ArrayList<>();\n" +
            "            for (int v : a) r.add(v); return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == long.class) {\n" +
            "            long[] a = (long[]) result; List<Long> r = new ArrayList<>();\n" +
            "            for (long v : a) r.add(v); return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == double.class) {\n" +
            "            double[] a = (double[]) result; List<Double> r = new ArrayList<>();\n" +
            "            for (double v : a) r.add(v); return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == boolean.class) {\n" +
            "            boolean[] a = (boolean[]) result; List<Boolean> r = new ArrayList<>();\n" +
            "            for (boolean v : a) r.add(v); return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == ListNode.class) {\n" +
            "            ListNode[] a = (ListNode[]) result; List<Object> r = new ArrayList<>();\n" +
            "            for (ListNode n : a) r.add(fromListNode(n)); return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == int[].class) {\n" +
            "            int[][] a = (int[][]) result; List<Object> r = new ArrayList<>();\n" +
            "            for (int[] row : a) { List<Integer> rl = new ArrayList<>();\n" +
            "                for (int v : row) rl.add(v); r.add(rl); }\n" +
            "            return r;\n" +
            "        }\n" +
            "        if (rt.getComponentType() == String[].class) {\n" +
            "            String[][] a = (String[][]) result; List<Object> r = new ArrayList<>();\n" +
            "            for (String[] row : a) { List<String> rl = Arrays.asList(row); r.add(rl); }\n" +
            "            return r;\n" +
            "        }\n" +
            "    }\n" +
            "    return result;\n" +
            "}\n";

    @Override
    public String buildWrapperScript(String language, String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        return switch (language) {
            case "javascript" -> buildJavaScriptBatchWrapper(code, testCases);
            case "python" -> buildPythonBatchWrapper(code, testCases);
            case "java" -> buildJavaBatchWrapper(code, testCases);
            case "c" -> buildCBatchWrapper(code, testCases);
            case "cpp" -> buildCppBatchWrapper(code, testCases);
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    public String buildJavaScriptBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String funcName = extractFunctionName(code, "function ");
        return code + "\n" +
                "const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));\n" +
                "const results = input.map(args => {\n" +
                "  const start = Date.now();\n" +
                "  try {\n" +
                "    const result = " + funcName + "(...args);\n" +
                "    const mem = require('fs').readFileSync('/sys/fs/cgroup/memory.current', 'utf8').trim();\n" +
                "    return {output: JSON.stringify(result), runtime: Date.now() - start, status: 'ok', memory: parseInt(mem)};\n" +
                "  } catch(e) {\n" +
                "    return {output: e.message, runtime: Date.now() - start, status: 'error', memory: 0};\n" +
                "  }\n" +
                "});\n" +
                "process.stdout.write(JSON.stringify(results));\n";
    }

    public String buildPythonBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String invocation = buildPythonInvocation(code);
        boolean usesListNode = code.contains("ListNode");
        String argsExpression = usesListNode
                ? "[__ulticode_adapt_arg(arg) for arg in args]"
                : "args";
        return "from __future__ import annotations\n" +
                "import json, sys, time\n" +
                "from typing import List, Optional\n" +
                "class ListNode:\n" +
                "    def __init__(self, val=0, next=None):\n" +
                "        self.val = val\n" +
                "        self.next = next\n" +
                "def __ulticode_to_list_node(value):\n" +
                "    if value is None:\n" +
                "        return None\n" +
                "    dummy = ListNode(0)\n" +
                "    cur = dummy\n" +
                "    for item in value:\n" +
                "        cur.next = ListNode(item)\n" +
                "        cur = cur.next\n" +
                "    return dummy.next\n" +
                "def __ulticode_from_list_node(node):\n" +
                "    result = []\n" +
                "    seen = 0\n" +
                "    while node is not None and seen < 10000:\n" +
                "        result.append(node.val)\n" +
                "        node = node.next\n" +
                "        seen += 1\n" +
                "    return result\n" +
                "def __ulticode_adapt_arg(arg):\n" +
                "    if isinstance(arg, list) and (not arg or all(isinstance(item, int) for item in arg)):\n" +
                "        return __ulticode_to_list_node(arg)\n" +
                "    if isinstance(arg, list) and all(isinstance(item, list) for item in arg):\n" +
                "        return [__ulticode_to_list_node(item) for item in arg]\n" +
                "    return arg\n" +
                "def __ulticode_jsonable(result):\n" +
                "    if isinstance(result, ListNode):\n" +
                "        return __ulticode_from_list_node(result)\n" +
                "    if isinstance(result, list):\n" +
                "        return [__ulticode_from_list_node(item) if isinstance(item, ListNode) else item for item in result]\n" +
                "    return result\n" +
                "def __ulticode_memory():\n" +
                "    try:\n" +
                "        with open('/sys/fs/cgroup/memory.current') as f:\n" +
                "            return int(f.read().strip())\n" +
                "    except Exception:\n" +
                "        return 0\n" +
                code + "\n" +
                "input_data = json.loads(sys.stdin.read())\n" +
                "results = []\n" +
                "for args in input_data:\n" +
                "    start = time.time() * 1000\n" +
                "    try:\n" +
                "        call_args = " + argsExpression + "\n" +
                "        result = " + invocation + "\n" +
                "        elapsed = time.time() * 1000 - start\n" +
                "        mem = __ulticode_memory()\n" +
                "        results.append({'output': json.dumps(__ulticode_jsonable(result)), 'runtime': int(elapsed), 'status': 'ok', 'memory': mem})\n" +
                "    except Exception as e:\n" +
                "        elapsed = time.time() * 1000 - start\n" +
                "        results.append({'output': str(e), 'runtime': int(elapsed), 'status': 'error', 'memory': 0})\n" +
                "print(json.dumps(results))\n";
    }

    private String buildPythonInvocation(String code) {
        if (code.contains("class Solution")) {
            return "Solution()." + extractSolutionMethodName(code) + "(*call_args)";
        }
        return extractFunctionName(code, "def ") + "(*call_args)";
    }

    private String extractSolutionMethodName(String code) {
        int classIdx = code.indexOf("class Solution");
        int methodIdx = code.indexOf("def ", classIdx >= 0 ? classIdx : 0);
        if (methodIdx < 0) {
            return "solution";
        }
        return extractFunctionName(code.substring(methodIdx), "def ");
    }

    public String buildCBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = resolvePerCaseTimeoutSeconds(testCases.size());
        return "cat > /tmp/solution.c && gcc -o /tmp/solution /tmp/solution.c && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['/tmp/solution'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    public String buildCppBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = resolvePerCaseTimeoutSeconds(testCases.size());
        return "cat > /tmp/solution.cpp && g++ -o /tmp/solution /tmp/solution.cpp && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['/tmp/solution'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    public String buildJavaBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String mainSource = buildJavaMainSource(code);
        String b64 = Base64.getEncoder().encodeToString(mainSource.getBytes(StandardCharsets.UTF_8));
        int perCaseTimeout = resolvePerCaseTimeoutSeconds(testCases.size());
        return "echo '" + b64 + "' | base64 -d > /tmp/Main.java && javac /tmp/Main.java && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['java','-cp','/tmp','Main'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    /**
     * Build the full {@code /tmp/Main.java} source for a Java user submission. If the user code
     * defines a {@code class Solution}, the generated {@code main} method uses reflection to find
     * and invoke its first public method. Otherwise it falls back to stdin pass-through.
     *
     * <p>All reflection-based helpers ({@code ListNode} class, {@code adaptArg}, {@code jsonable})
     * are always included regardless of whether the user references {@code ListNode}, so the
     * generated main is a single uniform shape. The user never needs to declare
     * {@code class ListNode} themselves.
     *
     * <p>Java files allow at most one {@code public} top-level class, so the user's
     * {@code public} modifiers on top-level class declarations are stripped via
     * {@link #stripPublicModifier(String)} before assembly.
     */
    private String buildJavaMainSource(String code) {
        boolean hasSolutionClass = code.contains("class Solution");
        String userCode = stripPublicModifier(code);

        StringBuilder sb = new StringBuilder();
        sb.append("import java.util.*;\n");
        sb.append("import java.lang.reflect.*;\n\n");
        sb.append(LIST_NODE_CLASS).append("\n");
        sb.append(userCode).append("\n");
        sb.append("public class Main {\n");
        sb.append(JAVA_JSON_PARSER).append("\n");
        sb.append(JAVA_JSON_SERIALIZER).append("\n");
        sb.append(JAVA_ARG_ADAPTER).append("\n");
        sb.append(JAVA_RESULT_JSONABLE).append("\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        sb.append("        String input = new java.util.Scanner(System.in).useDelimiter(\"\\\\A\").next();\n");
        sb.append("        Object parsed = parseJson(input);\n");
        if (hasSolutionClass) {
            // Reflection-based invocation: find the first public method on Solution, adapt
            // parsed JSON args to declared parameter types, invoke, JSON-serialize the result.
            sb.append("        Class<?> sc = Class.forName(\"Solution\");\n");
            sb.append("        Method m = null;\n");
            sb.append("        for (Method mt : sc.getDeclaredMethods()) {\n");
            sb.append("            if (java.lang.reflect.Modifier.isPublic(mt.getModifiers())) { m = mt; break; }\n");
            sb.append("        }\n");
            sb.append("        if (m == null) throw new RuntimeException(\"No public method found on Solution class\");\n");
            sb.append("        Class<?>[] pt = m.getParameterTypes();\n");
            sb.append("        List<Object> argList = (parsed instanceof List) ? (List<Object>)parsed : List.of(parsed);\n");
            sb.append("        Object[] adapted = new Object[pt.length];\n");
            sb.append("        for (int i = 0; i < pt.length; i++)\n");
            sb.append("            adapted[i] = adaptArg(i < argList.size() ? argList.get(i) : null, pt[i]);\n");
            sb.append("        Object result = m.invoke(sc.getDeclaredConstructor().newInstance(), adapted);\n");
            sb.append("        System.out.print(toJson(jsonable(result, m)));\n");
        } else {
            // Free-form code: no Solution class, just echo the input back unchanged.
            sb.append("        System.out.print(input);\n");
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Strips the {@code public} modifier from top-level class declarations in user code to avoid
     * conflicts with the generated {@code public class Main}. Java allows only one public class
     * per .java file. Recognises optional intervening modifiers like {@code final}, {@code abstract},
     * {@code static}, and {@code sealed}/{@code non-sealed}.
     */
    private String stripPublicModifier(String code) {
        return code.replaceAll("(?m)^public\\s+(static\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*class\\s+", "class ");
    }

    @Override
    public String buildBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCases.size(); i++) {
            if (i > 0) json.append(",");
            json.append(buildInputsJson(testCases.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Per-case timeout budget for batch wrappers.
     *
     * <p>Total sandbox wall-time for a batch run is bounded by the outer
     * {@code DockerSandboxConfig.timeout}; we evenly split that budget
     * across the supplied test cases. To keep {@code subprocess.run(timeout=0)}
     * valid (and meaningful for at least one case) we floor the result at 1
     * second. An empty case list gets the full 30s budget, which is harmless
     * because the wrapper script emits no work in that case.
     */
    static int resolvePerCaseTimeoutSeconds(int testCaseCount) {
        if (testCaseCount <= 0) {
            return 30;
        }
        return Math.max(1, 30 / testCaseCount);
    }

    @Override
    public List<RunResultDTO.RunCaseResult> parseBatchResults(
            String stdout, List<RunSubmissionDTO.RunTestCase> testCases,
            String runId, String userId) {
        try {
            String jsonArray = extractBatchResultsJson(stdout);
            if (jsonArray == null) {
                return testCases.stream()
                        .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                                0, null, "Failed to parse batch results: " + sanitizeSandboxOutput(stdout), 0.0))
                        .collect(Collectors.toList());
            }
            List<Map<String, Object>> results = objectMapper.readValue(jsonArray,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<RunResultDTO.RunCaseResult> caseResults = new ArrayList<>();
            for (int i = 0; i < testCases.size() && i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                RunSubmissionDTO.RunTestCase testCase = testCases.get(i);
                String output = result.get("output") != null ? result.get("output").toString() : "";
                long runtime = result.get("runtime") != null ? ((Number) result.get("runtime")).longValue() : 0;
                String status = result.get("status") != null ? result.get("status").toString() : "error";
                long memoryBytes = result.get("memory") != null
                        ? ((Number) result.get("memory")).longValue() : 0;
                double memoryMb = memoryBytes / (1024.0 * 1024.0);
                if ("timeout".equals(status)) {
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            "Time Limit Exceeded", runtime, null, "Per-case timeout exceeded", 0.0));
                } else if ("error".equals(status)) {
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            "Runtime Error", runtime, null, sanitizeSandboxOutput(output), 0.0));
                } else {
                    String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
                    boolean passed = normalizeOutput(output).equals(normalizeOutput(expected));
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            passed ? "Accepted" : "Wrong Answer", runtime, output, null, memoryMb));
                }
            }
            return caseResults;
        } catch (Exception e) {
            log.error("Failed to parse batch results", e);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0, null, "Result parsing failed: " + e.getMessage(), 0.0))
                    .collect(Collectors.toList());
        }
    }

    private String extractBatchResultsJson(String stdout) {
        if (stdout == null) {
            return null;
        }
        int jsonEnd = stdout.lastIndexOf(']');
        if (jsonEnd < 0) {
            return null;
        }
        for (int i = 0; i < stdout.length(); i++) {
            if (stdout.charAt(i) != '[') {
                continue;
            }
            String candidate = stdout.substring(i, jsonEnd + 1);
            try {
                List<?> parsed = objectMapper.readValue(candidate, List.class);
                boolean allMaps = parsed.stream().allMatch(Map.class::isInstance);
                if (allMaps) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Keep scanning: stdout can contain user output before the wrapper JSON.
            }
        }
        return null;
    }

    @Override
    public String wrapJavaScript(String code) {
        String funcName = extractFunctionName(code, "function ");
        return """
                %s
                const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
                const result = %s(...input);
                process.stdout.write(JSON.stringify(result));
                """.formatted(code, funcName);
    }

    @Override
    public String wrapPython(String code) {
        String funcName = extractFunctionName(code, "def ");
        return """
                import json, sys
                %s
                input_data = json.loads(sys.stdin.read())
                result = %s(*input_data)
                print(json.dumps(result))
                """.formatted(code, funcName);
    }

    @Override
    public String wrapJava(String code) {
        String mainSource = buildJavaMainSource(code);
        String b64 = Base64.getEncoder().encodeToString(mainSource.getBytes(StandardCharsets.UTF_8));
        return "echo '" + b64 + "' | base64 -d > /tmp/Main.java && javac /tmp/Main.java && java -cp /tmp Main";
    }

    @Override
    public String extractFunctionName(String code, String keyword) {
        int idx = code.indexOf(keyword);
        if (idx < 0) return "solution";
        int start = idx + keyword.length();
        while (start < code.length() && Character.isWhitespace(code.charAt(start))) start++;
        int end = start;
        while (end < code.length() && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) end++;
        return end == start ? "solution" : code.substring(start, end);
    }

    @Override
    public String buildInputsJson(RunSubmissionDTO.RunTestCase testCase) {
        if (testCase.getInputs() == null || testCase.getInputs().isEmpty()) return "[]";
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCase.getInputs().size(); i++) {
            if (i > 0) json.append(",");
            json.append(parseInputValue(testCase.getInputs().get(i).getValue()));
        }
        json.append("]");
        return json.toString();
    }

    @Override
    public String parseInputValue(String value) {
        if (value == null) return "null";
        value = value.trim();
        if (value.equals("true") || value.equals("false")) return value;
        if (value.startsWith("[") && value.endsWith("]")) return value;
        try { Double.parseDouble(value); return value; }
        catch (NumberFormatException e) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }

    @Override
    public String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim().replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ",")
                .replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
    }

    @Override
    public long parseRuntimeMs(String runtime) {
        if (runtime == null || !runtime.endsWith("ms")) return 0;
        try { return Long.parseLong(runtime.replace("ms", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override
    public String sanitizeSandboxOutput(String output) {
        if (output == null) return "Runtime error";
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.contains("OCI runtime") || trimmed.contains("docker")) continue;
            sb.append(trimmed).append("\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Runtime error" : result;
    }

    @Override
    public RunResultDTO emptyResult(Long problemId, String userId) {
        return RunResultDTO.builder()
                .id(UUID.randomUUID().toString())
                .problemId(problemId)
                .userId(userId)
                .verdict("System Error")
                .runtime("0ms")
                .memory("0.0MB")
                .cases(List.of())
                .passedCases(0)
                .totalCases(0)
                .build();
    }

    @Override
    public RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId,
                                                      String status, long runtimeMs,
                                                      String output, String detail,
                                                      double memoryMb) {
        List<RunResultDTO.RunCaseResult.InputParam> inputs = null;
        if (testCase.getInputs() != null) {
            inputs = testCase.getInputs().stream()
                    .map(i -> RunResultDTO.RunCaseResult.InputParam.builder()
                            .id(i.getId()).label(i.getLabel()).name(i.getName()).value(i.getValue())
                            .build())
                    .toList();
        }
        return RunResultDTO.RunCaseResult.builder()
                .id(UUID.randomUUID().toString()).runId(runId)
                .submissionTestId(testCase.getId()).testCaseId(testCase.getId())
                .caseLabel(testCase.getLabel() != null ? testCase.getLabel() : testCase.getId())
                .status(status)
                .runtime(runtimeMs + "ms")
                .runtimeMs(runtimeMs)
                .memory(String.format("%.1fMB", memoryMb))
                .memoryMb(memoryMb)
                .output(output).expectedOutput(testCase.getOutput()).detail(detail).inputs(inputs)
                .build();
    }

    // ── D-form (LeetCode/HackerRank harness) ─────────────────────────────────
    // These three methods replace the per-request Form A bash wrapper with a
    // static input.json contract. The harness image (docker/sandbox/Dockerfile
    // Phase 2 build) has the pre-compiled harness at /opt/harness/{lang}/.
    //
    // Schema reference: docker/sandbox/harness/{java,python}/ — see
    // .claude/PRPs/plans/oj-sandbox-d-form-refactor.plan.md (D3 + D4 + D9).

    private static final java.util.Set<String> DFORM_TYPES = java.util.Set.of(
            "int", "long", "double", "boolean",
            "String", "int[]", "int[][]", "long[]", "String[]",
            "ListNode", "ListNode[]", "TreeNode", "TreeNode[]"
    );

    @Override
    public String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase, long perCaseTimeoutMs) {
        java.util.List<RunSubmissionDTO.RunTestCase> one = java.util.List.of(testCase);
        return buildDBatchInputsJson(one, perCaseTimeoutMs);
    }

    @Override
    public String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases, long perCaseTimeoutMs) {
        java.util.LinkedHashMap<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("per_case_timeout_ms", perCaseTimeoutMs);
        java.util.List<java.util.Map<String, Object>> cases = new java.util.ArrayList<>();
        for (RunSubmissionDTO.RunTestCase tc : testCases) {
            java.util.LinkedHashMap<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("case_id", String.valueOf(tc.getId() != null ? tc.getId() : ""));
            c.put("label", tc.getLabel() != null ? tc.getLabel() : c.get("case_id"));
            c.put("expected_output", tc.getOutput() != null ? tc.getOutput() : "");
            c.put("inputs", buildDInputSpecs(tc));
            cases.add(c);
        }
        root.put("cases", cases);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize D-form input.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<java.util.Map<String, Object>> buildDInputSpecs(RunSubmissionDTO.RunTestCase tc) {
        List<RunSubmissionDTO.RunInput> inputs = tc.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<java.util.Map<String, Object>> specs = new java.util.ArrayList<>();
        for (RunSubmissionDTO.RunInput in : inputs) {
            java.util.LinkedHashMap<String, Object> spec = new java.util.LinkedHashMap<>();
            spec.put("name", in.getName());
            // value is stored on the backend as a JSON-encoded literal; ship as-is
            spec.put("value", in.getValue() == null ? "null" : in.getValue());
            // CR fix: forward the OJ data-type hint when set. The harness
            // honors spec["type"] over a Java annotation or Python type hint
            // on the Solution method's argument, which is the only signal
            // for unannotated user code (the typical LeetCode/HackerRank
            // style). Empty / null / unknown types are omitted so the
            // harness falls back to whatever the annotation says.
            String type = in.getType();
            if (type != null && !type.isBlank() && DFORM_TYPES.contains(type)) {
                spec.put("type", type);
            }
            specs.add(spec);
        }
        return specs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                          List<RunSubmissionDTO.RunTestCase> testCases,
                                                          String runId, String userId) {
        if (stdout == null || stdout.isBlank()) {
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, "D-form harness emitted no envelope (process killed mid-run?)", 0.0))
                    .collect(Collectors.toList());
        }
        java.util.Map<String, Object> env;
        try {
            env = objectMapper.readValue(stdout, java.util.Map.class);
        } catch (Exception parseFail) {
            String detail = "D-form envelope unparseable: " + sanitizeSandboxOutput(stdout);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, detail, 0.0))
                    .collect(Collectors.toList());
        }
        com.ulticode.modules.submission.dto.EnvelopeDTO envelope =
                com.ulticode.modules.submission.dto.EnvelopeDTO.fromMap(env);
        if (envelope.exitCode() != 0) {
            // Harness itself panicked (parse failure, javac failure, ambiguous
            // Solution, etc.). Surface a single Runtime Error for the whole batch.
            String detail = "D-form harness panic (exit_code=" + envelope.exitCode() + "): "
                    + sanitizeSandboxOutput(stdout);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, detail, 0.0))
                    .collect(Collectors.toList());
        }
        java.util.List<com.ulticode.modules.submission.dto.PerCaseResultDTO> parsed = envelope.results();
        java.util.List<RunResultDTO.RunCaseResult> out = new java.util.ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            RunSubmissionDTO.RunTestCase tc = testCases.get(i);
            com.ulticode.modules.submission.dto.PerCaseResultDTO pr = i < parsed.size() ? parsed.get(i) : null;
            if (pr == null) {
                out.add(buildCaseResult(tc, runId, userId, "Runtime Error",
                        0L, null, "D-form envelope missing per-case result for index " + i, 0.0));
                continue;
            }
            String status = pr.status() == null ? "Runtime Error" : pr.status();
            // TLE in D-form manifests as "Time Limit Exceeded" + interrupted=true.
            // Match the legacy verdict spellings to keep API consumers happy.
            String detail = null;
            if (pr.error() != null && pr.error().message() != null) {
                detail = "[" + (pr.error().type() == null ? "Error" : pr.error().type()) + "] "
                        + pr.error().message();
            }
            out.add(buildCaseResult(
                    tc, runId, userId,
                    status,
                    pr.elapsedMs(),
                    pr.result() == null ? null : String.valueOf(pr.result()),
                    detail,
                    0.0));
        }
        return out;
    }
}
