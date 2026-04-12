package com.ulticode.modules.solution.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.solution.dto.CreateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.dto.UpdateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.UpdateSolutionDTO;
import com.ulticode.modules.solution.entity.Solution;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for solution operations.
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
     * Get comments for a solution.
     *
     * @param solutionId the solution ID
     * @return list of comments
     */
    List<SolutionCommentVO> getComments(String solutionId);

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
     * Find all solutions for a specific problem.
     *
     * @param problemId the problem ID
     * @param page      the page number (1-based)
     * @param pageSize  the number of items per page
     * @return paginated list of solutions
     */
    PageResult<SolutionVO> findByProblemId(Long problemId, Integer page, Integer pageSize);

    /**
     * Get a solution by ID as VO.
     *
     * @param id the solution ID
     * @return the solution VO
     */
    SolutionVO getSolutionById(String id);

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

    /**
     * Convert entity to VO.
     *
     * @param solution the entity
     * @return the VO
     */
    SolutionVO toVO(Solution solution);

    /**
     * Find all solutions for a specific user.
     *
     * @param userId the user ID
     * @param problemId optional problem ID to filter by
     * @return list of solution VOs
     */
    List<SolutionVO> findByUserId(String userId, Long problemId);
}
