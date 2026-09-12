package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

import java.util.Optional;

public interface ProblemAdministrationDomainService {
    Optional<Problem> findById(Long id);
    Optional<Problem> findBySlug(String slug);
    Problem createProblem(CreateProblemDTO dto, String actorId);

    /**
     * Update a problem behind its owner-side optimistic-lock fence.
     */
    Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId, Long expectedVersion);

    /**
     * Deliberate bypass for controlled legacy/import flows that do not have a
     * version token. Public and owner-bound writes must use {@link
     * #updateProblem(Long, UpdateProblemDTO, String, Long)}.
     */
    Problem updateProblemUnfenced(Long id, UpdateProblemDTO dto, String actorId);

    /**
     * Soft-delete a problem behind its owner-side optimistic-lock fence.
     */
    void deleteProblem(Long id, String actorId, Long expectedVersion);

    /**
     * Publish a problem behind its owner-side optimistic-lock fence.
     */
    Problem publishProblem(Long id, String actorId, Long expectedVersion);

    /**
     * Unpublish a problem behind its owner-side optimistic-lock fence.
     */
    Problem unpublishProblem(Long id, String actorId, Long expectedVersion);
}
