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
     * Legacy update entry point retained for existing in-process callers.
     * New owner-bound writes must use the expected-version overload.
     */
    default Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId) {
        return updateProblem(id, dto, actorId, null);
    }

    /**
     * Update a problem behind its owner-side optimistic-lock fence.
     */
    Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId, Long expectedVersion);

    /**
     * Parameter-order compatibility overload for callers that place the
     * version token next to the aggregate id.
     */
    default Problem updateProblem(Long id, Long expectedVersion, UpdateProblemDTO dto, String actorId) {
        return updateProblem(id, dto, actorId, expectedVersion);
    }

    /**
     * Legacy delete entry point retained for existing in-process callers.
     */
    default void deleteProblem(Long id, String actorId) {
        deleteProblem(id, actorId, null);
    }

    /**
     * Soft-delete a problem behind its owner-side optimistic-lock fence.
     */
    void deleteProblem(Long id, String actorId, Long expectedVersion);

    default void deleteProblem(Long id, Long expectedVersion, String actorId) {
        deleteProblem(id, actorId, expectedVersion);
    }

    /**
     * Legacy publish entry point retained for existing in-process callers.
     */
    default Problem publishProblem(Long id, String actorId) {
        return publishProblem(id, actorId, null);
    }

    /**
     * Publish a problem behind its owner-side optimistic-lock fence.
     */
    Problem publishProblem(Long id, String actorId, Long expectedVersion);

    default Problem publishProblem(Long id, Long expectedVersion, String actorId) {
        return publishProblem(id, actorId, expectedVersion);
    }

    /**
     * Legacy unpublish entry point retained for existing in-process callers.
     */
    default Problem unpublishProblem(Long id, String actorId) {
        return unpublishProblem(id, actorId, null);
    }

    /**
     * Unpublish a problem behind its owner-side optimistic-lock fence.
     */
    Problem unpublishProblem(Long id, String actorId, Long expectedVersion);

    default Problem unpublishProblem(Long id, Long expectedVersion, String actorId) {
        return unpublishProblem(id, actorId, expectedVersion);
    }
}
