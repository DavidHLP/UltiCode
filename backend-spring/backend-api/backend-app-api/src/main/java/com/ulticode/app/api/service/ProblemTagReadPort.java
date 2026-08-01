package com.ulticode.app.api.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Consumer-owned read seam the {@code solution} module uses to look up
 * problem-tag labels without importing {@code ProblemTagRelationMapper}
 * or {@code ProblemTagMapper} directly.
 *
 * <p>Used by {@code DefaultSolutionProjection} to populate
 * {@code SolutionVO.topicName} from the first tag attached to a problem.
 * Adapter lives in the {@code problem} module.
 *
 * @author ulticode
 */
public interface ProblemTagReadPort {

    /**
     * Batch-resolve the first tag label per problem. Returns a map from
     * problem id to its first tag's display label; problems with no tags
     * (or that do not exist) are simply absent. One round-trip per batch
     * regardless of size — kills the per-row N+1 the single-problem helper
     * caused on list pages.
     *
     * @param problemIds the problem ids to resolve (null / empty → empty map)
     * @return problem id → first tag label
     */
    Map<Long, String> findFirstTagLabels(Collection<Long> problemIds);

    /**
     * Return the first tag's display label for a single problem, or
     * {@code null} when the problem has no tags.
     *
     * @param problemId the problem id
     * @return the first tag label, or {@code null}
     */
    default String findFirstTagLabel(Long problemId) {
        if (problemId == null) {
            return null;
        }
        return findFirstTagLabels(List.of(problemId)).get(problemId);
    }
}
