package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
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
 *       the judge-outbox row in the same transaction (ADR-003 "submission +
 *       outbox 同事务"), ask the contest module to record a
 *       {@code ContestSubmission} via {@link ContestSubmissionPort}, and
 *       enqueue the judge job;</li>
 *   <li>the <em>flag-off</em> verdict writer {@code updateSubmissionResult}
 *       (legacy, unfenced by design);</li>
 *   <li>the <em>ADR-003 M3b</em> fenced verdict writer
 *       {@code updateSubmissionResultFenced}, which CAS-rejects stale workers
 *       whose generation was bumped (rejudge / reaper) and folds the F4
 *       performance-stats columns into the same generation+attempt CAS.</li>
 * </ul>
 *
 * <p>Post-verdict side effects — achievement triggers, submission-result
 * notifications (typed {@link com.ulticode.modules.notification.dispatcher.NotificationDispatcher}
 * intent via {@code JudgedNotificationDispatcher}), and the
 * {@code SubmissionJudgedEvent} publish — are owned here so the notification
 * fan-out logic lives in one place instead of being duplicated across the two
 * verdict writers.
 *
 * <p>The default adapter preserves every guard the facade used to inline:
 * {@code @Transactional} on intake, the ADR-003 §5
 * {@code judge.stale_result.dropped} counter, the F4 same-CAS stats write,
 * and the fire-and-forget isolation of contest-recording / achievement /
 * notification failures (a hiccup in any of them never surfaces as a 500 to
 * the judge worker).
 *
 * <p>Dependency category: <b>in-process</b>. The seam is real because the
 * submission state machine is the only writer and the default adapter is the
 * only provider today. The former {@code SubmissionService} write delegates
 * were collapsed onto this port so cross-module write callers
 * ({@code ContestServiceImpl#submit}, {@code DefaultJudgeAttemptExecutor}
 * verdict writes) inject it directly; {@code SubmissionService} now owns only
 * the read boundary. Tests can substitute a fake.
 *
 * @author ulticode
 */
public interface SubmissionWritePort {

    /**
     * Submit code for a problem — the Submission intake.
     * <p>Creates a new submission with {@code Pending} status inside a
     * transaction, writes the judge-outbox row (when the outbox flag is on)
     * so submission + outbox live or die together, asks the contest module
     * to record a contest submission (fire-and-forget), and enqueues the
     * judge job (skipped when the port cutover is active — the outbox
     * dispatcher is then the sole producer).
     *
     * @param userId    the submitting user id
     * @param createDTO the submission payload
     * @return the created submission view object
     */
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    /**
     * Flag-off verdict writer. Writes status / runtime / memory /
     * test-details (and, for an Accepted verdict, the performance-stats
     * columns) in a single unfenced {@code updateById}, then runs the
     * achievement triggers (skipped for virtual-contest replays — R6.3 /
     * F-08), the submission-result notification, and the
     * {@code SubmissionJudgedEvent} publish.
     *
     * <p>Unfenced by design — this path runs only when the ADR-003 fenced
     * port is off.
     *
     * @param submissionId the submission id
     * @param status       terminal verdict (typed; encoded to the wire string once inside)
     * @param runtime      runtime in ms
     * @param memory       memory in MB
     * @param testDetails  per-case execution details
     */
    void updateSubmissionResult(String submissionId, SubmissionStatus status, int runtime,
                                Double memory, List<Submission.TestCaseDetail> testDetails);

    /**
     * ADR-003 M3b fenced verdict write. The worker calls this with the
     * {@code generation} and {@code attemptId} it observed at acquire time;
     * the underlying {@code SubmissionMapper#writeVerdictFencedWithStats} CAS
     * rejects the write if the generation has since been bumped (rejudge /
     * reaper) or the attempt lost its lease. On rejection the result is
     * silently dropped and the {@code judge.stale_result.dropped} counter
     * increments.
     *
     * <p>F4: the performance-stats columns are computed before the CAS and
     * persisted in the same fenced write, eliminating the unfenced
     * second-write window the previous two-step path had. Side-effects
     * (achievements / notification) run only when the verdict actually lands
     * (affected = 1).
     *
     * @param submissionId submission id
     * @param generation   generation observed at acquire (fence axis 1)
     * @param attemptId    attempt UUID held by the worker (fence axis 2)
     * @param status       terminal verdict (typed; encoded to the wire string once inside)
     * @param runtime      runtime in ms
     * @param memory       memory in MB
     * @param testDetails  per-case execution details
     * @return {@code true} if the verdict was written; {@code false} if the
     *         fence rejected it (stale result dropped)
     */
    boolean updateSubmissionResultFenced(String submissionId, long generation, String attemptId,
                                         SubmissionStatus status, int runtime, Double memory,
                                         List<Submission.TestCaseDetail> testDetails);
}
