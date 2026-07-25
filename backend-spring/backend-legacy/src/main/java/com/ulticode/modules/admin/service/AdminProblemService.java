package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;

/**
 * Admin service for problem management with tab-specific data.
 */
public interface AdminProblemService {

    HeaderDataVO getHeaderData(Long id);

    DescriptionDataVO getDescriptionData(Long id);

    CodeDataVO getCodeData(Long id);

    CasesDataVO getCasesData(Long id);

    List<BulkProblemResultDTO> bulkAction(BulkProblemRequestDTO request);

    ProblemVO flagProblem(Long id, String reason);

    ProblemVO moderateProblem(Long id, String status, String notes);

    PageResult<ProblemVO> getFlaggedProblems(String status, int page, int limit);

    List<BulkProblemResultDTO> batchModerateProblems(BatchModerateRequestDTO request);

    PageResult<Submission> getProblemSubmissions(Long id, int page, int limit);

    List<AuditLogVO> getProblemAuditHistory(Long id);
}
