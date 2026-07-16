package com.ulticode.modules.problemlist.service;

import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;

/**
 * Admin-bypass mutation seam for the problem-list domain.
 *
 * <p>This is the narrow surface the admin module
 * ({@link com.ulticode.modules.admin.service.AdminProblemListService} and its
 * implementation) consumes to transition problem-list state without the
 * user-scoped ownership checks that gate
 * {@link ProblemListService#updateList} / {@link ProblemListService#deleteList}.
 * Keeping these operations off the user-facing {@link ProblemListService}
 * interface prevents admin-bypass mutations and the entity lookup that only
 * the audit path needs from widening the seam every user-facing caller sees.
 *
 * <p>This is a capability seam owned by the problem-list module, not the
 * admin orchestration type
 * {@code com.ulticode.modules.admin.service.AdminProblemListService} (which
 * owns {@code @Audited} policy and old/new-value capture); the two are
 * intentionally separate so each module changes for one reason.
 *
 * <p>Implementations live on {@code ProblemListServiceImpl}; admin callers
 * depend on this type, never on the implementation.
 */
public interface ProblemListAdminService {

    /**
     * Load the persistent entity for audit snapshot/identity resolution.
     * Throws {@code BusinessException(PROBLEM_LIST_NOT_FOUND)} when absent.
     *
     * @param id the list ID
     * @return the loaded entity
     */
    ProblemList findEntityById(String id);

    ProblemListSummaryVO adminUpdateProblemList(String id, UpdateProblemListDTO dto);

    ProblemListSummaryVO adminUpdateBasicInfo(String id, UpdateBasicInfoDTO dto);

    ProblemListSummaryVO adminUpdateVisibility(String id, UpdateVisibilityDTO dto);

    ProblemListSummaryVO adminUpdateBanner(String id, UpdateBannerDTO dto);

    void adminReplaceListProblems(String id, UpdateProblemListProblemsDTO dto);

    void adminDeleteProblemList(String id);
}
