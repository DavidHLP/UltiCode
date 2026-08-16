package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.LearningProgressDTO;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionHistoryDTO;
import com.ulticode.app.api.dto.SubmissionQueryDTO;
import com.ulticode.app.api.dto.SubmissionStatusMeta;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionUserQueryPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local route for {@link SubmissionUserQueryPort}: delegates the App
 * controller's read endpoints to the in-process submission module.
 *
 * <p>SPLIT-004 slice-8: the read-routing switch pairs this local adapter
 * (default, {@code app.submission.routing.mode=local}) with the Dubbo
 * remote adapter so {@code SubmissionController} no longer depends on
 * {@code SubmissionService}/{@code SubmissionProjection} directly. The
 * local path keeps today's behaviour exactly (including the NOT_FOUND
 * exception from {@link SubmissionService#findById}); the remote path
 * returns {@code null} and the controller maps both to HTTP semantics.
 */
@Component
@RequiredArgsConstructor
public class LocalSubmissionUserQueryAdapter implements SubmissionUserQueryPort {

    private final SubmissionService submissionService;
    private final SubmissionProjection submissionProjection;

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return submissionProjection.aggregateDates(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        return submissionProjection.aggregateLearningProgress(userId);
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        return submissionProjection.aggregateHistory(userId);
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return submissionProjection.getStatusCatalog();
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        return submissionService.findById(id, userId);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        return submissionService.findByUserId(userId, query);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        return submissionService.findBest(problemId, userId);
    }
}
