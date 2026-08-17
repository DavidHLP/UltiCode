package com.ulticode.app.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.port.adapter.DefaultSubmissionAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo provider for {@link SubmissionAdminReadPort} exported by
 * {@code backend-app} so backend-admin reads Submission list / detail /
 * statistics data without importing the submission module.
 *
 * <p>Delegates to the concrete {@link DefaultSubmissionAdminReadAdapter} —
 * never to the port interface itself — so the app bean graph keeps exactly
 * one primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdminReadProvider implements SubmissionAdminReadPort {

    private final DefaultSubmissionAdminReadAdapter delegate;

    @Override
    public SubmissionAdminRowDTO findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public PageResult<SubmissionAdminRowDTO> search(SubmissionAdminQueryDTO query, int page, int pageSize) {
        return delegate.search(query, page, pageSize);
    }

    @Override
    public long countAll() {
        return delegate.countAll();
    }

    @Override
    public long countCreatedSince(LocalDateTime from) {
        return delegate.countCreatedSince(from);
    }

    @Override
    public long countByStatus(String status) {
        return delegate.countByStatus(status);
    }

    @Override
    public List<String> findDistinctLanguages() {
        return delegate.findDistinctLanguages();
    }

    @Override
    public List<com.ulticode.submission.api.dto.StatusCountDTO> countByStatus() {
        return delegate.countByStatus();
    }

    @Override
    public List<com.ulticode.submission.api.dto.LanguageCountDTO> countByLanguage() {
        return delegate.countByLanguage();
    }

    @Override
    public long countDistinctUsersInRange(LocalDateTime from, LocalDateTime to) {
        return delegate.countDistinctUsersInRange(from, to);
    }

    @Override
    public long countSubmissionsInRange(LocalDateTime from) {
        return delegate.countSubmissionsInRange(from);
    }

    @Override
    public long countAcceptedSubmissionsInRange(LocalDateTime from) {
        return delegate.countAcceptedSubmissionsInRange(from);
    }
}
