package com.ulticode.modules.admin.port.adapter;

import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo consumer adapter registering {@link SubmissionAdminReadPort} as a
 * local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SubmissionAdminReadProvider}).
 *
 * <p>Admin services keep depending on the entity-free port contract; this
 * adapter is the only local bean of that type. Read references use the
 * query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboSubmissionAdminReadAdapter implements SubmissionAdminReadPort {

    @DubboReference(group = "${app.submission.admin.read-group:backend-app}", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionAdminReadPort submissionAdminReadPort;

    @Override
    public SubmissionAdminRowDTO findById(String id) {
        return submissionAdminReadPort.findById(id);
    }

    @Override
    public PageResult<SubmissionAdminRowDTO> search(SubmissionAdminQueryDTO query, int page, int pageSize) {
        return submissionAdminReadPort.search(query, page, pageSize);
    }

    @Override
    public long countAll() {
        return submissionAdminReadPort.countAll();
    }

    @Override
    public long countCreatedSince(LocalDateTime from) {
        return submissionAdminReadPort.countCreatedSince(from);
    }

    @Override
    public long countByStatus(String status) {
        return submissionAdminReadPort.countByStatus(status);
    }

    @Override
    public List<String> findDistinctLanguages() {
        return submissionAdminReadPort.findDistinctLanguages();
    }

    @Override
    public List<com.ulticode.submission.api.dto.StatusCountDTO> countByStatus() {
        return submissionAdminReadPort.countByStatus();
    }

    @Override
    public List<com.ulticode.submission.api.dto.LanguageCountDTO> countByLanguage() {
        return submissionAdminReadPort.countByLanguage();
    }

    @Override
    public long countDistinctUsersInRange(LocalDateTime from, LocalDateTime to) {
        return submissionAdminReadPort.countDistinctUsersInRange(from, to);
    }

    @Override
    public long countSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countSubmissionsInRange(from);
    }

    @Override
    public long countAcceptedSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countAcceptedSubmissionsInRange(from);
    }

    @Override
    public SubmissionDashboardStatsDTO loadDashboardStats(LocalDateTime now) {
        return submissionAdminReadPort.loadDashboardStats(now);
    }

    @Override
    public List<SubmissionDashboardChartDataDTO> loadDashboardChartData(
            LocalDateTime start, LocalDateTime end, String period) {
        return submissionAdminReadPort.loadDashboardChartData(start, end, period);
    }
}
