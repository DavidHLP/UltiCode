package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
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
 * Single App user-read route; selects local or remote without ever
 * registering two active {@link SubmissionUserQueryPort} beans.
 *
 * <p>SPLIT-004 slice-8: mirrors the write/fence routing wrappers. The local
 * adapter is always available; the remote adapter is conditionally active
 * only when {@code app.submission.routing.mode=remote}. This wrapper is
 * {@code @Primary} so {@code SubmissionController} resolves exactly one
 * bean in every mode — App boots in remote mode too.
 */
@Component
@Primary
@RequiredArgsConstructor
public class SubmissionUserQueryRoutingPort implements SubmissionUserQueryPort {

    private final LocalSubmissionUserQueryAdapter local;
    private final ObjectProvider<RemoteSubmissionUserQueryAdapter> remote;
    private final SubmissionRoutingProperties routing;

    private SubmissionUserQueryPort delegate() {
        if (!routing.isRemote()) {
            return local;
        }
        RemoteSubmissionUserQueryAdapter remotePort = remote.getIfAvailable();
        if (remotePort == null) {
            throw new IllegalStateException("Remote Submission user-read route is enabled but unavailable");
        }
        return remotePort;
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
    public SubmissionVO findBest(Long problemId, String userId) {
        return delegate().findBest(problemId, userId);
    }
}
