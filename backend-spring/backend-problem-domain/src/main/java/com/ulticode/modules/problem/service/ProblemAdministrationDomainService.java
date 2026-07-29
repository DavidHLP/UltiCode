package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

import java.util.Optional;

public interface ProblemAdministrationDomainService {
    Optional<Problem> findById(Long id);
    Optional<Problem> findBySlug(String slug);
    Problem createProblem(CreateProblemDTO dto, String actorId);
    Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId);
    void deleteProblem(Long id, String actorId);
    Problem publishProblem(Long id, String actorId);
    Problem unpublishProblem(Long id, String actorId);
}
