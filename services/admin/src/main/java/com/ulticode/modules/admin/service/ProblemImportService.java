package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;

/**
 * Owns the whole batch outcome of importing problems.
 *
 * <p>The deep module behind the admin import seam: conflict-policy
 * resolution, per-row failure isolation, create/update identity, slug
 * uniqueness on conflict, and result accounting all live here as named
 * depth. Callers (and the controller) cross one small seam instead of
 * reconstructing the batch choreography.
 *
 * <p>Behavior is preserved exactly from the legacy inline
 * {@code AdminProblemServiceImpl#importProblems}; this interface only
 * expresses the concentrated intent.
 *
 * @author ulticode
 */
public interface ProblemImportService {

    /**
     * Import a batch of problems, resolving conflicts per the request
     * policy and isolating per-row failures into the result set.
     *
     * @param request the batch request carrying items and conflict policy
     * @return the per-batch and per-item outcome accounting
     */
    ImportProblemsResponseDTO importProblems(ImportProblemsRequestDTO request);
}
