package com.ulticode.modules.solution.port;

import java.util.List;

/**
 * Consumer-owned read seam the {@code solution} module uses to look up
 * problem-tag relationships without importing
 * {@code ProblemTagRelationMapper} or {@code ProblemTagMapper} directly.
 *
 * <p>Used by {@code DefaultSolutionProjection.toVO} to populate
 * {@code SolutionVO.topicName} from the first tag attached to a
 * problem. Adapter lives in the {@code problem} module.
 *
 * @author ulticode
 */
public interface ProblemTagReadPort {

    /**
     * Return the tag ids attached to the problem, in the order chosen
     * by the adapter (typically relation order). Returns an empty list
     * when the problem has no tags or does not exist.
     */
    List<String> findTagIdsByProblemId(Long problemId);

    /**
     * Return the first tag's display label, or {@code null} when the
     * problem has no tags.
     */
    String findFirstTagLabel(Long problemId);
}