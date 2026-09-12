package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

/**
 * Write-side seam for a problem's detail satellites.
 *
 * <p>The problem aggregate owns the {@code problems} row while this port
 * owns the coordinated writes for {@code problem_details}, languages,
 * examples, and tag relations. Keeping the satellite lifecycle behind one
 * port preserves the aggregate update's transaction boundary and gives the
 * domain module a narrow, replaceable collaborator.
 *
 * <p>The {@link Problem} argument lets the adapter denormalize the aggregate
 * slug when it creates a detail row. A {@code null} section in the update DTO
 * is a no-op; the adapter owns the validation and rebuild policy for each
 * supplied section.
 */
public interface ProblemDetailDomainPort {

    /**
     * Apply the supplied detail-satellite changes for one problem.
     *
     * @param problemId the problem id whose satellites are changed
     * @param problem the current problem aggregate snapshot
     * @param updateDTO the requested partial detail update
     */
    void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO);
}
