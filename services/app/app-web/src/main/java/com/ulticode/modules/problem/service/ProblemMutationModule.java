package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits ordinary Problem writes and their Search outbox records together. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemMutationModule {

    private final ProblemAdministrationDomainService domainService;
    private final ProblemIndexRefresher indexRefresher;

    public Problem createProblem(CreateProblemDTO dto, String actorId) {
        return recordSearchChange(domainService.createProblem(dto, actorId));
    }

    public Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId) {
        return updateProblem(id, dto, actorId, currentVersion(id));
    }

    public Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId, Long expectedVersion) {
        return recordSearchChange(domainService.updateProblem(id, dto, actorId, expectedVersion));
    }

    public Problem setPublished(Long id, boolean published, String actorId) {
        return setPublished(id, published, actorId, currentVersion(id));
    }

    public Problem setPublished(Long id, boolean published, String actorId, Long expectedVersion) {
        Problem problem = published
                ? domainService.publishProblem(id, actorId, expectedVersion)
                : domainService.unpublishProblem(id, actorId, expectedVersion);
        return recordSearchChange(problem);
    }

    public void deleteProblem(Long id, String actorId) {
        deleteProblem(id, actorId, currentVersion(id));
    }

    public void deleteProblem(Long id, String actorId, Long expectedVersion) {
        indexRefresher.publish(domainService.deleteProblem(id, actorId, expectedVersion));
    }

    private Problem recordSearchChange(Problem problem) {
        indexRefresher.publish(problem);
        return problem;
    }

    // Local HTTP writes use the current version. RPC writes supply the caller's version.
    private Long currentVersion(Long id) {
        return domainService.findById(id).map(Problem::getVersion).map(Integer::longValue).orElse(null);
    }
}
