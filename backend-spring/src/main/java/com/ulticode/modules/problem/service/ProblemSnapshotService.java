package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.VersionDiffVO;

import java.util.List;

/**
 * Owns the Problem-version snapshot schema end-to-end: capture from the live
 * problem tables, serialization, schema interpretation for the detail view,
 * field-level diff inputs, and multi-table restore coordination.
 *
 * <p>This is the single deep module for the fragile snapshot shape &mdash; the
 * {@code title}/{@code slug}/{@code examples}/{@code inputs} keys live here and
 * nowhere else. Callers see a coherent capture/restore seam instead of
 * choreographing a codec, a diff, and a rollback themselves.
 *
 * <p><strong>Zero behavioral change</strong> vs. the prior split (buildSnapshot
 * + ProblemSnapshotCodec + ProblemVersionDiff + ProblemVersionRollback): same
 * JSON shape, same diff output, same restore semantics.
 */
public interface ProblemSnapshotService {

    /**
     * Capture the current problem state across all versioned tables and return
     * the serialized snapshot JSON.
     *
     * @param problemId the problem ID
     * @return the snapshot JSON string
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code PROBLEM_NOT_FOUND} if the problem row is missing
     */
    String capture(Long problemId);

    /**
     * Interpret a snapshot JSON into the detail view object.
     *
     * @param detail      the detail VO to populate (title, slug, examples, etc.)
     * @param snapshotJson the snapshot JSON; null/blank is a no-op
     */
    void populateDetail(ProblemVersionDetailVO detail, String snapshotJson);

    /**
     * Compute the field-level diff between two snapshot JSON strings.
     *
     * @param fromJson the source snapshot JSON
     * @param toJson   the target snapshot JSON
     * @return list of changed fields with their old/new values
     * @throws com.ulticode.common.exception.BusinessException if either JSON is malformed
     */
    List<VersionDiffVO> diff(String fromJson, String toJson);

    /**
     * Restore the live problem tables (problem, detail, examples, languages,
     * tag relations) from a snapshot JSON, deleting and reinserting the
     * collection tables as needed.
     *
     * @param problemId    the problem ID
     * @param snapshotJson the snapshot JSON to restore from
     */
    void restore(Long problemId, String snapshotJson);
}
