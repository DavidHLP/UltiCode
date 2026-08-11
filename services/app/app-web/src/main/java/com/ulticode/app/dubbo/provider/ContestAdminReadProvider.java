package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.port.adapter.DefaultContestAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo provider for {@link ContestAdminReadPort} exported by
 * {@code backend-app} so backend-admin reads contests without importing the
 * contest module.
 *
 * <p>Delegates to the concrete {@link DefaultContestAdminReadAdapter} — never
 * to the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestAdminReadProvider implements ContestAdminReadPort {

    private final DefaultContestAdminReadAdapter delegate;

    @Override
    public ContestAdminDTO selectById(String id) {
        return delegate.selectById(id);
    }

    @Override
    public ContestAdminDTO selectByIdOrSlug(String identifier) {
        return delegate.selectByIdOrSlug(identifier);
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status,
            String contestType) {
        return delegate.selectPage(page, size, keyword, status, contestType);
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status,
            String contestType, String sortBy, String sortOrder) {
        return delegate.selectPage(page, size, keyword, status, contestType, sortBy, sortOrder);
    }

    @Override
    public List<ContestAdminDTO> selectAll(List<String> statusNames) {
        return delegate.selectAll(statusNames);
    }

    @Override
    public long countByStatus(String statusName) {
        return delegate.countByStatus(statusName);
    }

    @Override
    public long countProblemsByContestId(String contestId) {
        return delegate.countProblemsByContestId(contestId);
    }

    @Override
    public List<ContestAdminDTO> selectByStartTimeAfter(LocalDateTime afterStartTime) {
        return delegate.selectByStartTimeAfter(afterStartTime);
    }
}
