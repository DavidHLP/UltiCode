package com.ulticode.modules.solution.service;

import com.ulticode.modules.solution.dto.CreateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.dto.UpdateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.UpdateSolutionDTO;
import com.ulticode.modules.solution.entity.Solution;

import java.util.Optional;

/**
 * Service interface for solution operations.
 *
 * <p>Owns the solution and comment write state machine (create / update / delete / recordView and
 * comment mutations) plus the detail read that also records a view. All pure entity-to-VO
 * projection and read-side aggregation lives in
 * {@link com.ulticode.modules.solution.projection.SolutionProjection}.
 */
public interface SolutionService {

    /**
     * Find a solution by ID.
     *
     * @param id the solution ID
     * @return the solution if found
     */
    Optional<Solution> findById(String id);

    /**
     * Record a view for a solution.
     *
     * @param solutionId the solution ID
     * @param userId the user ID (optional, can be null for anonymous)
     */
    void recordView(String solutionId, String userId);

    /**
     * Create a comment on a solution.
     */
    SolutionCommentVO createComment(String solutionId, String userId, CreateSolutionCommentDTO dto);

    /**
     * Update an existing comment.
     */
    SolutionCommentVO updateComment(String commentId, String userId, UpdateSolutionCommentDTO dto);

    /**
     * Soft-delete a comment.
     */
    void deleteComment(String commentId, String userId);

    /**
     * Get a solution by ID as VO.
     *
     * @param id the solution ID
     * @return the solution VO
     */
    SolutionVO getSolutionById(String id);

    /**
     * Get a solution by ID as VO with current user's vote state.
     *
     * @param id the solution ID
     * @param currentUserId the current user ID (optional, can be null)
     * @return the solution VO
     */
    SolutionVO getSolutionById(String id, String currentUserId);

    /**
     * Create a new solution.
     *
     * @param problemId  the problem ID
     * @param userId     the user ID
     * @param createDTO  the create data
     * @return the created solution VO
     */
    SolutionVO create(Long problemId, String userId, CreateSolutionDTO createDTO);

    /**
     * Update an existing solution.
     *
     * @param id        the solution ID
     * @param userId    the user ID
     * @param updateDTO the update data
     * @return the updated solution VO
     */
    SolutionVO update(String id, String userId, UpdateSolutionDTO updateDTO);

    /**
     * Delete a solution.
     *
     * @param id     the solution ID
     * @param userId the user ID
     */
    void delete(String id, String userId);
}
