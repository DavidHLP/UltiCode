package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.enums.SubmissionStatus.Kind;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Pure reducer that turns a collection of per-case verdicts into a single final
 * submission verdict (ADR-001 §2.4).
 * <p>
 * Replaces the stringly-typed {@code VERDICT_PRIORITY} map previously embedded
 * in {@code JudgeWorkerProcessor}. Uses {@link SubmissionStatus#getSeverity()}
 * as the ordering and refuses to reduce in-flight statuses
 * ({@link Kind#IN_FLIGHT}) — those indicate a caller bug.
 * <p>
 * Reduction rules:
 * <ul>
 *   <li>Empty input → {@link SubmissionStatus#SYSTEM_ERROR} (no cases means
 *       the runner produced nothing).</li>
 *   <li>Any {@link Kind#IN_FLIGHT} case → throws
 *       {@link IllegalStateException} (caller bug).</li>
 *   <li>Otherwise → the case with the highest {@code severity()} wins.
 *       {@link SubmissionStatus#ACCEPTED} wins only when every case is
 *       {@code ACCEPTED}.</li>
 * </ul>
 * <p>
 * Pure function — no side effects, no I/O, safe to call from any thread.
 */
@Component
public class VerdictResolver {

    /**
     * Reduce a collection of per-case statuses to the final verdict.
     *
     * @param caseStatuses statuses of the individual test cases; may not be {@code null}
     * @return the worst (highest severity) terminal status from the collection,
     *         or {@link SubmissionStatus#SYSTEM_ERROR} if the collection is empty
     * @throws IllegalArgumentException if {@code caseStatuses} is {@code null}
     * @throws IllegalStateException if any case is still {@link Kind#IN_FLIGHT}
     */
    public SubmissionStatus reduce(Collection<SubmissionStatus> caseStatuses) {
        if (caseStatuses == null) {
            throw new IllegalArgumentException("caseStatuses must not be null");
        }
        if (caseStatuses.isEmpty()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        SubmissionStatus worst = null;
        for (SubmissionStatus s : caseStatuses) {
            if (s == null) {
                continue;
            }
            if (s.getKind() == Kind.IN_FLIGHT) {
                throw new IllegalStateException(
                        "Cannot reduce: case still in-flight (" + s.name() + ")");
            }
            if (worst == null || s.getSeverity() > worst.getSeverity()) {
                worst = s;
            }
        }
        return worst == null ? SubmissionStatus.SYSTEM_ERROR : worst;
    }

    /**
     * String-overload bridge for callers that still hold wire strings (e.g.
     * {@code RunResultDTO.RunCaseResult.getStatus()}). Each string is decoded via
     * {@link SubmissionStatus#fromDbName(String)} — unknown strings are treated
     * as {@link SubmissionStatus#SYSTEM_ERROR} rather than failing the whole
     * reduction, matching legacy behavior of the old VERDICT_PRIORITY map.
     *
     * @param caseStatusWireValues wire strings of the per-case statuses; may not be {@code null}
     * @return the final verdict
     */
    public SubmissionStatus reduceWire(Collection<String> caseStatusWireValues) {
        if (caseStatusWireValues == null) {
            throw new IllegalArgumentException("caseStatusWireValues must not be null");
        }
        if (caseStatusWireValues.isEmpty()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        SubmissionStatus worst = null;
        for (String wire : caseStatusWireValues) {
            SubmissionStatus s = SubmissionStatus.fromDbName(wire);
            if (s == null) {
                // Legacy behavior: unknown wire value treated as SYSTEM_ERROR (severity 8).
                s = SubmissionStatus.SYSTEM_ERROR;
            }
            if (s.getKind() == Kind.IN_FLIGHT) {
                throw new IllegalStateException(
                        "Cannot reduce: case still in-flight (" + s.name() + ")");
            }
            if (worst == null || s.getSeverity() > worst.getSeverity()) {
                worst = s;
            }
        }
        return worst == null ? SubmissionStatus.SYSTEM_ERROR : worst;
    }
}
