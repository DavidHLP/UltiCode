package com.ulticode.modules.submission.stats;

import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.modules.submission.entity.Submission;

/**
 * Deep module that owns the runtime/memory performance-statistics computation
 * for the submission domain.
 *
 * <p>Replaces the {@code computePerformanceStats} private method cluster
 * previously embedded in {@code SubmissionServiceImpl}. The percentile and
 * distribution-bin math is pure (it only reads the per-user best values via
 * {@code SubmissionMapper}), so this module is exercised with a mocked mapper
 * in tests — no verdict-write / notification collaborators need to be wired
 * just to test the math.
 *
 * <p>Why a separate module and not "a helper class":
 * <ul>
 *   <li><b>Locality</b>: the bin-algorithm and percentile-strategy have both
 *       changed in the last year. Keeping them with the fenced-verdict CAS and
 *       notification dispatch made the diff noise and risked touching the hot
 *       path. They are now concentrated here.</li>
 *   <li><b>Leverage</b>: the future admin-side "problem difficulty stats" can
 *       reuse this module instead of re-implementing the binning.</li>
 *   <li><b>Interface is the test surface</b>: the math is testable with plain
 *       {@code List<Double>} inputs and a mocked aggregation query — no
 *       transactional context, no entity persistence.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (single SQL read, no I/O that
 * cannot be exercised with mocks). Mirrors the {@link
 * com.ulticode.modules.submission.projection.SubmissionProjection} shape:
 * interface + single default adapter, no external seam.
 */
public interface SubmissionPerformanceStats {

    /**
     * Compute how the supplied submission's runtime and memory compare against
     * the per-user best values of other users who solved the same problem in
     * the same language.
     *
     * <p>Only meaningful for {@code Accepted} verdicts; callers gate on status
     * before invoking. For non-Accepted verdicts callers should use
     * {@link PerformanceStats#EMPTY} (or skip the call) so the percentile
     * columns are cleared, matching the legacy behaviour of always
     * overwriting the field.
     *
     * @param current the just-judged submission entity (read for problemId /
     *                language / userId — never mutated)
     * @param runtime the verdict runtime in ms (ignored if &lt; 0)
     * @param memory  the verdict memory in MB ({@code null}/negative skips the
     *                memory axis)
     * @return the immutable stats snapshot; never {@code null}
     */
    PerformanceStats compute(Submission current, int runtime, Double memory);
}
