package com.ulticode.modules.solution.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;

import java.util.List;

/**
 * Read-side projection for the solution module.
 *
 * <p>Owns every entity-to-VO shaping rule and read-side aggregation for solutions and their
 * comments: author enrichment, vote/comment count roll-ups from {@code edge_operations},
 * viewer-vote resolution, topic-name and achievement-badge decoration, and the batched
 * (N+1-safe) list projection. Lifting this cluster out of {@code SolutionServiceImpl} keeps the
 * write state machine (create / update / delete / recordView / comment mutations) free of read
 * concerns, and gives every projection rule a single home. Write paths shape their return values
 * through this facade rather than owning a private copy of the mapping logic.
 */
public interface SolutionProjection {

    /**
     * List the comments of a solution as VOs, oldest first.
     *
     * @param solutionId the solution ID
     * @return the ordered comment VOs
     */
    List<SolutionCommentVO> getComments(String solutionId);

    /**
     * Convert a {@link SolutionComment} entity to its VO, enriched with author info.
     *
     * @param comment the entity (may be {@code null})
     * @return the VO, or {@code null} when the input is {@code null}
     */
    SolutionCommentVO toCommentVO(SolutionComment comment);

    /**
     * Page the published solutions of a problem as lightweight list items, batch-enriched to
     * avoid N+1 queries (author, vote counts, comment counts, viewer vote).
     *
     * @param problemId the problem ID
     * @param page      the page number (1-based)
     * @param pageSize  the page size
     * @return the paginated list items
     */
    PageResult<SolutionListItemVO> findByProblemId(Long problemId, Integer page, Integer pageSize);

    /**
     * List a user's published solutions as full VOs.
     *
     * @param userId    the author ID
     * @param problemId optional problem filter
     * @return the solution VOs
     */
    List<SolutionVO> findByUserId(String userId, Long problemId);

    /**
     * Convert a {@link Solution} entity to its full VO.
     *
     * @param solution the entity (may be {@code null})
     * @return the VO, or {@code null} when the input is {@code null}
     */
    SolutionVO toVO(Solution solution);

    /**
     * Convert a {@link Solution} entity to its full VO, including the current user's vote state.
     *
     * @param solution      the entity (may be {@code null})
     * @param currentUserId the current user ID (optional, may be {@code null})
     * @return the VO, or {@code null} when the input is {@code null}
     */
    SolutionVO toVO(Solution solution, String currentUserId);
}
