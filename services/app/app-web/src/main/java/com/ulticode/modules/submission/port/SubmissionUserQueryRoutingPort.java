package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.config.SubmissionRoutingProperties;
import com.ulticode.modules.submission.port.adapter.LocalSubmissionUserQueryAdapter;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionUserQueryAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single App user-read route; normal boot uses the Submission owner and the
 * local projection exists only for explicit legacy rollback.
 *
 * <p>The wrapper remains the one injected App bean. It resolves only the
 * active conditional adapter, so normal owner mode and rollback mode cannot
 * silently perform a dual read.
 */
@Component
@Primary
@RequiredArgsConstructor
public class SubmissionUserQueryRoutingPort implements SubmissionUserQueryPort {

    private final ObjectProvider<LocalSubmissionUserQueryAdapter> local;
    private final ObjectProvider<RemoteSubmissionUserQueryAdapter> remote;
    private final SubmissionRoutingProperties routing;

    private SubmissionUserQueryPort delegate() {
        return routing.selectOwnerRead(local::getIfAvailable, remote::getIfAvailable, "user-read");
    }

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return delegate().aggregateDates(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        return delegate().aggregateLearningProgress(userId);
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        return delegate().aggregateHistory(userId);
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return delegate().getStatusCatalog();
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        return delegate().findById(id, userId);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        return delegate().findByUserId(userId, query);
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query) {
        return delegate().findByProblemId(problemId, userId, query);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        return delegate().findBest(problemId, userId);
    }
}
