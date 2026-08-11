package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.SubmissionAdminRowDTO;

import java.util.List;

/**
 * Admin service for problem management with tab-specific data.
 */
public interface AdminProblemService {

    PageResult<ProblemAdminVO> listProblems(ProblemAdminQueryDTO query);

    ProblemAdminVO getProblemById(Long id);

    HeaderDataVO getHeaderData(Long id);

    DescriptionDataVO getDescriptionData(Long id);

    CodeDataVO getCodeData(Long id);

    CasesDataVO getCasesData(Long id);

    List<BulkProblemResultDTO> bulkAction(BulkProblemRequestDTO request);

    ProblemAdminVO flagProblem(Long id, String reason);

    ProblemAdminVO moderateProblem(Long id, String status, String notes);

    PageResult<ProblemAdminVO> getFlaggedProblems(String status, int page, int limit);

    List<BulkProblemResultDTO> batchModerateProblems(BatchModerateRequestDTO request);

    PageResult<SubmissionAdminRowDTO> getProblemSubmissions(Long id, int page, int limit);

    List<AuditLogVO> getProblemAuditHistory(Long id);
}
