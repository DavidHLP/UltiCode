package com.ulticode.judge.api;

import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/** Immutable command for a bounded public-code preview in Judge. */
public record JudgeRunCommand(
        String requestId,
        Long problemId,
        String userId,
        String language,
        String code,
        List<TestCase> testCases,
        TraceMetadata trace,
        Visibility visibility) implements Serializable {

    public enum Visibility {
        PUBLIC_PREVIEW,
        PRIVATE,
        HIDDEN
    }

    public JudgeRunCommand(String requestId, Long problemId, String userId,
                           String language, String code, List<TestCase> testCases,
                           TraceMetadata trace) {
        this(requestId, problemId, userId, language, code, testCases, trace,
                Visibility.PUBLIC_PREVIEW);
    }

    private static final long serialVersionUID = 1L;
    private static final int MAX_CODE_LENGTH = 65_536;
    private static final int MAX_CASES = 100;

    public JudgeRunCommand {
        if (visibility == null) {
            throw new IllegalArgumentException("visibility is required");
        }
        requireText(requestId, "requestId");
        if (problemId == null || problemId < 1) {
            throw new IllegalArgumentException("problemId must be positive");
        }
        requireText(language, "language");
        language = language.trim().toLowerCase(Locale.ROOT);
        requireText(code, "code");
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("code exceeds the maximum length");
        }
        if (testCases == null || testCases.size() > MAX_CASES) {
            throw new IllegalArgumentException("testCases must contain at most " + MAX_CASES + " items");
        }
        testCases = List.copyOf(testCases);
        if (trace == null) {
            trace = TraceMetadata.EMPTY;
        }
    }

    public record TestCase(
            String id,
            String label,
            String expectedOutput,
            List<Input> inputs) implements Serializable {
        private static final long serialVersionUID = 1L;

        public TestCase {
            requireText(id, "testCase.id");
            expectedOutput = expectedOutput == null ? "" : expectedOutput;
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
        }
    }

    public record Input(
            String id,
            String label,
            String name,
            String value,
            String type) implements Serializable {
        private static final long serialVersionUID = 1L;

    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
