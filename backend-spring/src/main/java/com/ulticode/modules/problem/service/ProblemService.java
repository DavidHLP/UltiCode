package com.ulticode.modules.problem.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemDetailResponse;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

import java.util.Optional;

/**
 * Service interface for problem-related operations.
 */
public interface ProblemService {

    /**
     * Find a problem by its ID.
     *
     * @param id the problem ID
     * @return the problem entity, or empty if not found
     */
    Optional<Problem> findById(Long id);

    /**
     * Find a problem by its slug.
     *
     * @param slug the URL-friendly identifier
     * @return the problem entity, or empty if not found
     */
    Optional<Problem> findBySlug(String slug);

    /**
     * List problems with pagination and filters.
     *
     * @param query the query parameters
     * @return paginated result of problem view objects
     */
    PageResult<ProblemVO> listProblems(ProblemQueryDTO query);

    /**
     * Get a problem by ID as a view object.
     *
     * @param id the problem ID
     * @return the problem view object
     */
    ProblemVO getProblemById(Long id);

    /**
     * Get a problem by slug as a view object.
     *
     * @param slug the URL-friendly identifier
     * @return the problem view object
     */
    ProblemVO getProblemBySlug(String slug);

    /**
     * Get full problem detail response including description, examples, and languages.
     *
     * @param id the problem ID
     * @return the full problem detail response
     */
    ProblemDetailResponse getProblemDetailResponse(Long id);

    /**
     * Get full problem detail response by slug including description, examples, and languages.
     *
     * @param slug the URL-friendly identifier
     * @return the full problem detail response
     */
    ProblemDetailResponse getProblemDetailResponseBySlug(String slug);

    /**
     * Create a new problem.
     *
     * @param createDTO the create data
     * @return the created problem view object
     */
    ProblemVO createProblem(CreateProblemDTO createDTO);

    /**
     * Update an existing problem.
     *
     * @param id        the problem ID
     * @param updateDTO the update data
     * @return the updated problem view object
     */
    ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO);

    /**
     * Delete a problem (soft delete).
     *
     * @param id the problem ID
     */
    void deleteProblem(Long id);

    /**
     * Publish a problem.
     *
     * @param id the problem ID
     * @return the updated problem view object
     */
    ProblemVO publishProblem(Long id);

    /**
     * Unpublish a problem.
     *
     * @param id the problem ID
     * @return the updated problem view object
     */
    ProblemVO unpublishProblem(Long id);

    /**
     * Convert a Problem entity to ProblemVO.
     *
     * @param problem the problem entity
     * @return the problem view object
     */
    ProblemVO toVO(Problem problem);

    /**
     * Get adjacent problems (previous and next) for navigation.
     *
     * @param id the current problem ID
     * @return the adjacent problems response with prev and next problem IDs (slugs)
     */
    AdjacentProblemsVO getAdjacentProblems(Long id);

    /**
     * Get a random published problem.
     *
     * @return a random published problem view object
     * @throws BusinessException if no published problems are available
     */
    ProblemVO findRandomPublished();
}
