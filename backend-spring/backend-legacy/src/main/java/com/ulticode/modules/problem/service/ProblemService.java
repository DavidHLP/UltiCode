package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

import java.util.Optional;

/**
 * State-machine service for problem entities: create / update / publish /
 * unpublish / delete, the premium-access guard on the read entry points
 * ({@code getProblemById} / {@code getProblemBySlug}), the cross-module
 * entity lookups ({@code findById} / {@code findBySlug}), and the
 * cross-module {@code toVO} facade.
 *
 * <p>Read-side projection and aggregation (list, detail, adjacent, random)
 * moved to {@link com.ulticode.modules.problem.projection.ProblemProjection}.
 * Controllers that serve those read paths depend on the projection directly;
 * this interface no longer exposes them.
 *
 * @author ulticode
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
     * Get a problem by ID as a view object. Applies the premium-access guard:
     * premium problems require ADMIN or SUPER_ADMIN role.
     *
     * @param id the problem ID
     * @return the problem view object
     */
    ProblemVO getProblemById(Long id);

    /**
     * Get a problem by slug as a view object. Applies the premium-access guard.
     *
     * @param slug the URL-friendly identifier
     * @return the problem view object
     */
    ProblemVO getProblemBySlug(String slug);

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
     * Convert a Problem entity to ProblemVO. Thin facade over
     * {@link com.ulticode.modules.problem.projection.ProblemProjection#toVO(Problem)},
     * retained because four cross-module callers already hold a
     * {@code ProblemService} reference.
     *
     * @param problem the problem entity
     * @return the problem view object
     */
    ProblemVO toVO(Problem problem);
}
