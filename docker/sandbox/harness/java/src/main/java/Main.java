import java.io.ByteArrayOutputStream;
import java.security.Permission;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * UltiCode sandbox harness entry point — Java.
 *
 * <p>Contract (see backend {@code HarnessEnvelope}):
 * <ul>
 *   <li>argv[0] = path to input.json (default: {@code /job/input.json})</li>
 *   <li>stdout = single JSON envelope, even on error; never user output</li>
 *   <li>stderr = harness-panic stack traces (parse failure, missing Solution
 *       class, etc.); never user code's runtime exception (those go into the
 *       per-case {@code error} field of the envelope)</li>
 *   <li>exit code: 0 = envelope is well-formed; 2 = harness itself crashed
 *       before it could emit an envelope</li>
 * </ul>
 *
 * <p>The class is intentionally small. All heavy lifting (JSON parsing,
 * argument adaptation, ListNode/TreeNode conversion) lives in
 * {@link Harness} which is unit-tested independently.
 */
public final class Main {

    static final String HARNESS_VERSION = "1.0";
    static final String LANGUAGE = "java";
    /** Cap on captured per-case user stdout. Anything over is truncated with a marker. */
    static final int MAX_USER_STDOUT_BYTES = 64 * 1024;
    /** Default per-case timeout when input.json omits the field. */
    static final long DEFAULT_PER_CASE_TIMEOUT_MS = 1000L;
    /** Default input.json path used when no argv is given. */
    static final String DEFAULT_INPUT_PATH = "/job/input.json";

    private Main() {}

    public static void main(String[] args) {
        // Install a SecurityManager that BLOCKS user code from calling
        // System.exit / Runtime.halt. Without this, a malicious or buggy
        // user method skips per-case error handling and prevents the
        // envelope from being emitted (CR finding #1 — process control).
        //
        // SecurityManager is deprecated for removal in future JDKs but
        // still functional in JDK 17. When the JDK eventually removes it,
        // Phase 2+ will need to switch to per-case child-process isolation.
        NoExitSecurityManager sm = new NoExitSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException ignored) {
            // JDK 18+ without -Djava.security.manager=allow: fall through and
            // rely on the backend's ProcessBuilder destroyForcibly() as the
            // outer kill. Image build pins this flag explicitly.
        }

        PrintStream realOut = System.out;
        int exitCode = 0;
        try {
            String inputPath = (args.length > 0) ? args[0] : DEFAULT_INPUT_PATH;
            Map<String, Object> input = readInput(inputPath);

            long perCaseTimeoutMs = ((Number) input.getOrDefault(
                    "per_case_timeout_ms", DEFAULT_PER_CASE_TIMEOUT_MS)).longValue();
            // ADR-002 §8 (P0-2): per-run memory ceiling forwarded by the
            // backend so the harness can self-report Memory Limit Exceeded.
            // Absent / 0 disables the harness-level MLE check.
            long memoryLimitBytes = ((Number) input.getOrDefault("memory_limit_bytes", 0L)).longValue();
            List<?> cases = asList(input.get("cases"));
            String methodHint = stringOrNull(input.get("method_name"));

            Class<?> solutionClass = Class.forName("Solution");
            Method method = resolveSolutionMethod(solutionClass, methodHint);

            List<Object> results = new ArrayList<>(cases.size());
            long totalStartNs = System.nanoTime();
            for (Object caseObj : cases) {
                results.add(runCase(solutionClass, method, asMap(caseObj),
                        perCaseTimeoutMs, memoryLimitBytes));
            }
            long totalElapsedMs = (System.nanoTime() - totalStartNs) / 1_000_000;

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("harness_version", HARNESS_VERSION);
            envelope.put("language", LANGUAGE);
            envelope.put("exit_code", 0);
            envelope.put("total_elapsed_ms", totalElapsedMs);
            envelope.put("results", results);

            realOut.print(Harness.toJson(envelope));
            realOut.flush();
        } catch (Throwable t) {
            // Harness-level panic. By contract: stderr = stack, exit != 0.
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) {
                // stderr may itself be blocked by the SecurityManager.
            }
            exitCode = 2;
        } finally {
            // Permit the harness's own clean exit (the only legal exit path).
            sm.permitExit();
        }
        System.exit(exitCode);
    }

    /**
     * Blocks user code from terminating the JVM. Installed before any
     * reflective Solution invocation; the only legal exit is the harness's
     * own {@code System.exit} after permitExit().
     *
     * <p>Blocks both {@code System.exit} (which goes through {@code checkExit})
     * and {@code Runtime.halt} (which goes through {@code checkPermission}
     * with the {@code exitVM.*} permission). JDK 17's SecurityManager is
     * deprecated for removal but still functional; Phase 2+ will switch to
     * per-case child-process isolation.
     */
    static final class NoExitSecurityManager extends SecurityManager {
        private volatile boolean allowExit = false;

        void permitExit() {
            this.allowExit = true;
        }

        @Override
        public void checkPermission(Permission perm) {
            // Block Runtime.halt / Runtime.exit-style VM termination that
            // bypasses checkExit. checkPermission is the SM hook for the
            // RuntimePermission("exitVM.<n>") that halt() requests.
            if (!allowExit && perm != null && "exitVM".equals(perm.getName())) {
                throw new SecurityException(
                        "User code attempted to terminate the harness JVM (halt via "
                                + perm.getName() + ")");
            }
            // Allow all other permissions. Harness needs file IO, reflection, etc.
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }

        @Override
        public void checkExit(int status) {
            if (!allowExit) {
                throw new SecurityException(
                        "User code attempted to terminate the harness JVM (exit " + status + ")");
            }
        }
    }

    /** Resolve which method on Solution to invoke. Deterministic, fails loudly on ambiguity. */
    static Method resolveSolutionMethod(Class<?> cls, String methodHint) {
        if (methodHint != null && !methodHint.isEmpty()) {
            Method match = null;
            for (Method m : cls.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (Modifier.isPublic(mod) && !Modifier.isStatic(mod) && m.getName().equals(methodHint)) {
                    if (match != null) {
                        throw new IllegalStateException(
                                "Multiple public instance methods named '" + methodHint
                                        + "' on " + cls.getName() + " (overloads not supported)");
                    }
                    match = m;
                }
            }
            if (match == null) {
                throw new IllegalStateException(
                        "Method '" + methodHint + "' not found on " + cls.getName()
                                + ". Must be public, non-static, and exist on Solution.");
            }
            return match;
        }
        // No hint: require exactly one public instance method.
        Method singleton = null;
        for (Method m : cls.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
                if (singleton != null) {
                    throw new IllegalStateException(
                            "Solution has multiple public instance methods (" + singleton.getName()
                                    + ", " + m.getName() + ", ...); supply 'method_name' in input.json"
                                    + " to disambiguate.");
                }
                singleton = m;
            }
        }
        if (singleton == null) {
            throw new IllegalStateException(
                    "No public instance method found on " + cls.getName()
                            + ". User code must declare 'class Solution { public ReturnType methodName(...) ... }'.");
        }
        return singleton;
    }

    private static String stringOrNull(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    static Map<String, Object> readInput(String path) throws java.io.IOException {
        String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Object parsed = Harness.parseJson(content);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("input.json root must be an object");
        }
        return asMap(parsed);
    }

    /** Backwards-compatible no-hint resolver. New code should call {@link #resolveSolutionMethod}. */
    static Method findFirstPublicInstanceMethod(Class<?> cls) {
        return resolveSolutionMethod(cls, null);
    }

    private static Map<String, Object> runCase(Class<?> solutionClass, Method method,
                                               Map<String, Object> testCase,
                                               long timeoutMs, long memoryLimitBytes) {
        String caseId = String.valueOf(testCase.getOrDefault("case_id", ""));
        String label = String.valueOf(testCase.getOrDefault("label", caseId));
        List<?> inputSpecs = asList(testCase.get("inputs"));
        Object expectedOutput = testCase.get("expected_output");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("case_id", caseId);
        result.put("label", label);

        // --- argument adaptation -------------------------------------------
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] adapted = new Object[paramTypes.length];
        try {
            for (int i = 0; i < paramTypes.length; i++) {
                Object raw = (i < inputSpecs.size()) ? extractInputValue(inputSpecs.get(i)) : null;
                adapted[i] = Harness.adaptArg(raw, paramTypes[i]);
            }
        } catch (Throwable t) {
            return finishCase(result, "Runtime Error", 0L, 0L, 0L, 0L, null, t, "");
        }

        // --- invoke under timeout, capturing user stdout -------------------
        ByteArrayOutputStream userOut = new ByteArrayOutputStream();
        PrintStream userStream = new PrintStream(userOut, true, StandardCharsets.UTF_8);
        PrintStream realOut = System.out;
        System.setOut(userStream);

        long startNs = System.nanoTime();
        // ADR-002 §8: reset heap peak before user code so the sampled peak
        // reflects only this case (the harness JVM runs all cases in one
        // process; without a reset the peak would be cumulative).
        resetHeapPeakUsage();
        Object methodResult = null;
        boolean timedOut = false;
        Throwable userException = null;
        final long[] cpuHolder = {0L};

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "harness-worker");
            t.setDaemon(true);
            return t;
        });
        Future<Object> future = executor.submit(() -> {
            // ADR-002 §8: sample CPU time of the worker thread only so the
            // harness's own reflection overhead is excluded.
            ThreadMXBean tb = ManagementFactory.getThreadMXBean();
            long cpu0 = tb.getCurrentThreadCpuTime();
            try {
                Object instance = solutionClass.getDeclaredConstructor().newInstance();
                return method.invoke(instance, adapted);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                if (cause instanceof RuntimeException re) throw re;
                if (cause instanceof Error err) throw err;
                throw new RuntimeException(cause);
            } finally {
                cpuHolder[0] = tb.getCurrentThreadCpuTime() - cpu0;
            }
        });
        try {
            methodResult = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            timedOut = true;
            future.cancel(true);
        } catch (ExecutionException ee) {
            userException = ee.getCause() != null ? ee.getCause() : ee;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            userException = ie;
        } finally {
            executor.shutdownNow();
            System.setOut(realOut);
        }
        long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
        long elapsedMs = elapsedUs / 1_000L;
        long cpuMs = Math.max(0L, cpuHolder[0]) / 1_000_000L;
        // ADR-002 §8: true heap peak (was totalMemory()-freeMemory(), a
        // single-point sample that missed spikes GC'd away before sampling).
        long peakBytes = peakHeapUsageBytes();
        String userStdout = truncateUserOutput(userOut.toString(StandardCharsets.UTF_8));

        if (timedOut) {
            result.put("elapsed_ms", elapsedMs);
            result.put("peak_memory_bytes", peakBytes);
            result.put("elapsed_us", elapsedUs);
            result.put("cpu_ms", cpuMs);
            result.put("status", "Time Limit Exceeded");
            result.put("result", null);
            result.put("interrupted", true);
            result.put("user_stdout", userStdout);
            result.put("user_stderr", "");
            return result;
        }
        if (userException != null) {
            return finishCase(result, "Runtime Error", elapsedMs, peakBytes,
                    elapsedUs, cpuMs, null, userException, userStdout);
        }

        // ADR-002 §8 (P0-2): user code ran cleanly but used more heap than the
        // per-run ceiling → Memory Limit Exceeded (harness self-report; the
        // backend also has a Layer-B backstop for older harnesses).
        if (memoryLimitBytes > 0 && peakBytes > memoryLimitBytes) {
            return finishCase(result, "Memory Limit Exceeded", elapsedMs, peakBytes,
                    elapsedUs, cpuMs, null, null, userStdout);
        }

        Object jsonable;
        String actualJson;
        try {
            jsonable = Harness.jsonable(methodResult, method);
            actualJson = Harness.toJson(jsonable);
        } catch (Throwable t) {
            // CR fix #2/#7/#8: jsonable() raises on cycles, depth, node-count,
            // and non-finite floats. Convert to per-case Runtime Error so the
            // envelope stays well-formed (and the next case still runs).
            return finishCase(result, "Runtime Error", elapsedMs, peakBytes,
                    elapsedUs, cpuMs, null, t, userStdout);
        }
        String expectedJson = (expectedOutput == null)
                ? null
                : Harness.normalizeJson(String.valueOf(expectedOutput));
        boolean passed = (expectedJson != null) && expectedJson.equals(Harness.normalizeJson(actualJson));

        result.put("elapsed_ms", elapsedMs);
        result.put("peak_memory_bytes", peakBytes);
        result.put("elapsed_us", elapsedUs);
        result.put("cpu_ms", cpuMs);
        result.put("status", passed ? "Accepted" : "Wrong Answer");
        result.put("result", jsonable);
        result.put("user_stdout", userStdout);
        result.put("user_stderr", "");
        return result;
    }

    private static Map<String, Object> finishCase(Map<String, Object> result, String status,
                                                  long elapsedMs, long peakMemoryBytes,
                                                  long elapsedUs, long cpuMs,
                                                  Object value, Throwable error, String userStdout) {
        result.put("elapsed_ms", elapsedMs);
        result.put("peak_memory_bytes", peakMemoryBytes);
        result.put("elapsed_us", elapsedUs);
        result.put("cpu_ms", cpuMs);
        result.put("status", status);
        result.put("result", value);
        if (error != null) {
            result.put("error", errorObj(error));
        }
        result.put("user_stdout", userStdout);
        result.put("user_stderr", "");
        return result;
    }

    /**
     * Reset the JVM's per-pool heap peak-usage counters. Called before each
     * case's user code so {@link #peakHeapUsageBytes()} reflects only that
     * case (ADR-002 §8).
     */
    private static void resetHeapPeakUsage() {
        try {
            for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
                if (p.getType() == MemoryType.HEAP) {
                    p.resetPeakUsage();
                }
            }
        } catch (Throwable ignored) {
            // best-effort; if reset fails the peak is just cumulative
        }
    }

    /**
     * Sum of per-pool heap peak usage after user code ran. This is a true
     * high-water mark (since the last {@link #resetHeapPeakUsage}), unlike
     * the old {@code totalMemory()-freeMemory()} sample which only caught
     * whatever was live at sampling time. ADR-002 §8.
     */
    private static long peakHeapUsageBytes() {
        try {
            long sum = 0L;
            for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
                if (p.getType() == MemoryType.HEAP) {
                    sum += p.getPeakUsage().getUsed();
                }
            }
            return Math.max(0L, sum);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * Backend writes {@code inputs} as a list of {@code {name, value}} objects,
     * where {@code value} is the JSON-encoded argument text. We parse the
     * {@code value} field into a structured object here.
     */
    private static Object extractInputValue(Object inputSpec) {
        if (inputSpec instanceof Map<?, ?> map) {
            Object value = map.get("value");
            if (value instanceof String s) {
                return Harness.parseJson(s);
            }
            return value;
        }
        return inputSpec;
    }

    static String truncateUserOutput(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_USER_STDOUT_BYTES) {
            return s;
        }
        return new String(bytes, 0, MAX_USER_STDOUT_BYTES, StandardCharsets.UTF_8)
                + "\n... [truncated, original=" + bytes.length + " bytes]";
    }

    static Map<String, Object> errorObj(Throwable t) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", t.getClass().getName());
        err.put("message", t.getMessage() == null ? "" : t.getMessage());
        List<String> stack = new ArrayList<>();
        for (StackTraceElement ste : t.getStackTrace()) {
            String cn = ste.getClassName();
            // Hide harness frames (Main, Harness, java.lang.reflect.*). Keep
            // user frames (Solution and any user-declared helper classes).
            if (cn.equals("Main") || cn.equals("Harness")
                    || cn.startsWith("Main$") || cn.startsWith("Harness$")
                    || cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")) {
                continue;
            }
            stack.add(ste.toString());
        }
        err.put("stack", stack);
        return err;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Expected object, got " + o.getClass().getName());
    }

    private static List<?> asList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> l) return l;
        throw new IllegalArgumentException("Expected array, got " + o.getClass().getName());
    }
}
