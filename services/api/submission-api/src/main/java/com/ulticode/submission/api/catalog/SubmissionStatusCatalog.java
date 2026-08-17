package com.ulticode.submission.api.catalog;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical user-facing metadata catalog for {@link SubmissionStatus}.
 *
 * <p>The status enum owns durable identity and this catalog owns descriptions,
 * suggestions, UI severity and display order. Keeping the implementation in
 * submission-api prevents App and backend-submission projections from drifting.
 */
public final class SubmissionStatusCatalog {

    private SubmissionStatusCatalog() {
    }

    /** User-facing metadata that is not part of the durable status enum. */
    public record Entry(String description, String suggestion, String severity, int sortOrder) {
    }

    private static final Map<SubmissionStatus, Entry> CATALOG;

    static {
        Map<SubmissionStatus, Entry> entries = new LinkedHashMap<>();
        entries.put(SubmissionStatus.PENDING,
                new Entry("Submission is waiting to be judged",
                        "Please wait for the judging to complete", "info", 0));
        entries.put(SubmissionStatus.JUDGING,
                new Entry("Submission is being judged",
                        "Please wait for the judging to complete", "info", 1));
        entries.put(SubmissionStatus.ACCEPTED,
                new Entry("All test cases passed",
                        "Congratulations! Your solution is correct.", "success", 2));
        entries.put(SubmissionStatus.WRONG_ANSWER,
                new Entry("Your output was incorrect",
                        "Check your algorithm and edge cases", "error", 3));
        entries.put(SubmissionStatus.TIME_LIMIT_EXCEEDED,
                new Entry("Your program took too long to execute",
                        "Optimize your algorithm or reduce unnecessary operations", "error", 4));
        entries.put(SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                new Entry("Your program used too much memory",
                        "Optimize memory usage or use more efficient data structures", "error", 5));
        entries.put(SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                new Entry("Your program produced too much output",
                        "Check for infinite loops that produce output", "error", 6));
        entries.put(SubmissionStatus.RUNTIME_ERROR,
                new Entry("Your program crashed during execution",
                        "Check for division by zero, null pointer, array out of bounds, etc.", "error", 7));
        entries.put(SubmissionStatus.COMPILE_ERROR,
                new Entry("Your code failed to compile",
                        "Check syntax errors and make sure your code is valid", "error", 8));
        entries.put(SubmissionStatus.PRESENTATION_ERROR,
                new Entry("Your output format is incorrect",
                        "Check for extra spaces, newlines, or formatting issues", "warning", 9));
        entries.put(SubmissionStatus.SANDBOX_ERROR,
                new Entry("The judging sandbox encountered an internal error",
                        "Please retry; if the issue persists contact support", "error", 10));
        entries.put(SubmissionStatus.SYSTEM_ERROR,
                new Entry("An error occurred on our end",
                        "Please try again later or contact support", "error", 11));
        CATALOG = Collections.unmodifiableMap(entries);
    }

    /** Return user-facing metadata for a status, with a safe unknown default. */
    public static Entry forStatus(SubmissionStatus status) {
        Entry entry = CATALOG.get(status);
        return entry != null
                ? entry
                : new Entry("", "", "info", Integer.MAX_VALUE);
    }

    /** Project a status and its catalog entry to the published API DTO. */
    public static SubmissionStatusMeta toMeta(SubmissionStatus status) {
        Entry entry = forStatus(status);
        SubmissionStatusMeta meta = new SubmissionStatusMeta();
        meta.setKey(status.getDisplayName());
        meta.setCode(status.name());
        meta.setLabel(status.getDisplayName());
        meta.setDescription(entry.description());
        meta.setSuggestion(entry.suggestion());
        meta.setCategory(status.getCategory());
        meta.setSeverity(entry.severity());
        meta.setIsTerminal(status.isTerminal());
        meta.setSortOrder(entry.sortOrder());
        return meta;
    }

    /** Iterate entries in the status enum's lifecycle declaration order. */
    public static Iterable<Map.Entry<SubmissionStatus, Entry>> entries() {
        return CATALOG.entrySet();
    }
}
