package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.List;

/**
 * Write surface for the submission domain — the <b>Submission intake</b> and
 * the verdict writers.
 *
 * <p>Extracted from the {@code SubmissionService} facade. Owns every state
 * mutation on submission records behind a narrow interface:
 * <ul>
 *   <li>the <em>intake</em> path — create a {@code Pending} submission, write
 *       the judge-outbox row in the same transaction (ADR-003), ask the contest
 *       module to record a {@code ContestSubmission} via
 *       {@link ContestSubmissionPort}, and enqueue the judge job;</li>
 *   <li>the <em>flag-off</em> verdict writer {@code updateSubmissionResult}
 *       (legacy, unfenced by design);</li>
 *   <li>the <em>ADR-003 M3b</em> fenced verdict writer
 *       {@code updateSubmissionResultFenced}, which CAS-rejects stale workers
 *       whose generation was bumped (rejudge / reaper) and folds the F4
 *       performance-stats columns into the same generation+attempt CAS.</li>
 * </ul>
 *
 * <p>The {@code testDetailsJson} parameter is a JSON-serialized representation
 * of per-case execution details. Callers use
 * {@code TestCaseDetailCodec.toJson(List)} to serialize before calling; the
 * implementation deserializes for entity-binding (unfenced path) or passes
 * the JSON directly to the mapper (fenced path).
 *
 * @author ulticode
 */
public interface SubmissionWritePort {

    /**
     * Create a new submission in {@code Pending} status and enqueue the judge job.
     *
     * @param userId    the submitting user id
     * @param createDTO the submission payload
     * @return the created submission view
     */
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    /**
     * Flag-off verdict writer. Writes status / runtime / memory /
     * test-details (and, for an Accepted verdict, the performance-stats
     * columns) in a single unfenced {@code updateById}, then runs the
     * achievement triggers, the submission-result notification, and the
     * {@code SubmissionJudgedEvent} publish.
     *
     * <p>Unfenced by design — this path runs only when the ADR-003 fenced
     * port is off.
     *
     * @param submissionId    the submission id
     * @param status          terminal verdict
     * @param runtime         runtime in ms
     * @param memory          memory in MB (boxed to allow null)
     * @param testDetailsJson per-case execution details as JSON string (may be null)
     */
    void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                int runtime, Double memory, String testDetailsJson);

    /**
     * ADR-003 M3b fenced verdict write. The worker calls this with the
     * {@code generation} and {@code attemptId} it observed at acquire time;
     * the underlying mapper CAS rejects the write if the generation has since
     * been bumped (rejudge / reaper) or the attempt lost its lease.
     *
     * <p>F4: the performance-stats columns are computed before the CAS and
     * persisted in the same fenced write. Side-effects (achievements /
     * notification) run only when the verdict actually lands (affected = 1).
     *
     * @param submissionId    submission id
     * @param status          terminal verdict
     * @param runtime         runtime in ms
     * @param memory          memory in MB (boxed to allow null)
     * @param testDetailsJson per-case execution details as JSON string (may be null)
     * @param generation      generation observed at acquire (fence axis 1)
     * @param attemptId       attempt UUID held by the worker (fence axis 2)
     * @return {@code true} if the verdict was written; {@code false} if the
     *         fence rejected it (stale result dropped)
     */
    boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                         int runtime, Double memory, String testDetailsJson,
                                         long generation, String attemptId);
}
