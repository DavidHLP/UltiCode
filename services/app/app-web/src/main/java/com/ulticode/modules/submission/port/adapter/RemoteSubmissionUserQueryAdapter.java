package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Direct App adapter for the Submission-owner user query contract.
 *
 * <p>All user-facing Submission reads cross the owner boundary through this
 * bounded Dubbo contract. The App controller remains unchanged and receives
 * the same wire DTOs and pagination envelopes.
 */
@Component
public class RemoteSubmissionUserQueryAdapter implements SubmissionUserQueryPort {

    @DubboReference(group = "backend-submission", version = "1.1.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionUserQueryPort submissionUserQuery;

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return submissionUserQuery.aggregateDates(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        return submissionUserQuery.aggregateLearningProgress(userId);
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        return submissionUserQuery.aggregateHistory(userId);
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return submissionUserQuery.getStatusCatalog();
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        return submissionUserQuery.findById(id, userId);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        return submissionUserQuery.findByUserId(userId, query);
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query) {
        return submissionUserQuery.findByProblemId(problemId, userId, query);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        return submissionUserQuery.findBest(problemId, userId);
    }
}
