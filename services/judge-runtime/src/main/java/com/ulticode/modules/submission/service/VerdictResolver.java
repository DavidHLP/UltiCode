package com.ulticode.modules.submission.service;
import com.ulticode.modules.submission.port.VerdictResolvePort;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.domain.submission.enums.SubmissionStatus.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure reducer that turns a collection of per-case verdicts into a single final
 * submission verdict (ADR-001 §2.4).
 * <p>
 * Replaces the stringly-typed {@code VERDICT_PRIORITY} maps previously embedded
 * in {@code JudgeWorkerProcessor} <b>and</b> {@code CodeExecutionService}, so
 * the {@code /run} and {@code /submit} paths cannot drift (Codex F15 fix —
 * M1a round 4).
 * <p>
 * Uses {@link SubmissionStatus#getSeverity()} as the ordering and refuses to
 * reduce in-flight statuses ({@link Kind#IN_FLIGHT}) — those indicate a caller
 * bug.
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
 * Observability (Codex F16 fix — M1a round 4): {@link #reduceWire} decodes via
 * the strict {@link SubmissionStatusCodec#fromWire(String)} and counts any
 * unknown wire values via {@link #unknownWireFallbackCount}. Unknown values
 * are logged at WARN with the offending raw value and a sample traceId, then
 * mapped to {@link SubmissionStatus#SYSTEM_ERROR} so a single malformed case
 * can't crash the judge loop. The counter is intentionally an
 * {@link AtomicLong} (not Micrometer) to keep M1a dependency-free; it can be
 * exposed via a future actuator integration.
 * <p>
 * Pure function aside from the counter — safe to call from any thread.
 */
@Slf4j
@Component
public class VerdictResolver implements VerdictResolvePort {

    private final AtomicLong unknownWireFallbackCount = new AtomicLong(0);

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
     * {@code JudgeRunResponse.RunCaseResult.getStatus()}). Strict decode —
     * values are logged, counted, and mapped to
     * {@link SubmissionStatus#SYSTEM_ERROR} rather than failing the whole
     * reduction, so a single malformed case cannot crash the judge loop.
     *
     * @param caseStatusWireValues wire strings of the per-case statuses; may not be {@code null}
     * @return the final verdict
     */
    /**
     * Port-contract bridge — satisfies {@link VerdictResolvePort#reduceWire(List)}
     * by delegating to the {@code Collection<String>} overload above.
     * Callers that hold {@code List} (all existing call sites) bind here.
     */
    @Override
    public SubmissionStatus reduceWire(List<String> caseWireValues) {
        return reduceWire((Collection<String>) caseWireValues);
    }

    /**
     * String-overload bridge for callers that still hold wire strings (e.g.
     * {@code JudgeRunResponse.RunCaseResult.getStatus()}). Strict decode —
     * values are logged, counted, and mapped to
     * {@link SubmissionStatus#SYSTEM_ERROR} rather than failing the whole
     * reduction, so a single malformed case cannot crash the judge loop.
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
            SubmissionStatus s;
            try {
                s = SubmissionStatusCodec.fromWire(wire);
            } catch (IllegalArgumentException e) {
                long count = unknownWireFallbackCount.incrementAndGet();
                log.warn("VerdictResolver unknown wire value: raw='{}' (fallback to SYSTEM_ERROR, total={})",
                        wire, count);
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

    /**
     * Number of times {@link #reduceWire} has encountered a wire value not in
     * the {@link SubmissionStatus} enum contract. Bumping this counter
     * indicates either a sandbox runner regression (emitting legacy / future
     * verdict labels) or a contract drift between sandbox image and backend.
     * <p>
     * Test-only access in M1a; will be exposed via Micrometer in a follow-up.
     */
    public long unknownWireFallbackCount() {
        return unknownWireFallbackCount.get();
    }
}

