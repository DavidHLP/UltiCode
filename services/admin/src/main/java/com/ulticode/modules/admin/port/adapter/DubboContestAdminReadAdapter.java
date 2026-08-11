package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.response.PageResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo consumer adapter registering {@link ContestAdminReadPort} as a local
 * admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ContestAdminReadProvider}).
 *
 * <p>Admin projections/services keep depending on the entity-free port
 * contract; this adapter is the only local bean of that type.
 */
@Primary
@Component
public class DubboContestAdminReadAdapter implements ContestAdminReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private ContestAdminReadPort contestAdminReadPort;

    @Override
    public ContestAdminDTO selectById(String id) {
        return contestAdminReadPort.selectById(id);
    }

    @Override
    public ContestAdminDTO selectByIdOrSlug(String identifier) {
        return contestAdminReadPort.selectByIdOrSlug(identifier);
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status,
            String contestType) {
        return contestAdminReadPort.selectPage(page, size, keyword, status, contestType);
    }

    @Override
    public PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status,
            String contestType, String sortBy, String sortOrder) {
        return contestAdminReadPort.selectPage(page, size, keyword, status, contestType, sortBy, sortOrder);
    }

    @Override
    public List<ContestAdminDTO> selectAll(List<String> statusNames) {
        return contestAdminReadPort.selectAll(statusNames);
    }

    @Override
    public long countByStatus(String statusName) {
        return contestAdminReadPort.countByStatus(statusName);
    }

    @Override
    public long countProblemsByContestId(String contestId) {
        return contestAdminReadPort.countProblemsByContestId(contestId);
    }

    @Override
    public List<ContestAdminDTO> selectByStartTimeAfter(LocalDateTime afterStartTime) {
        return contestAdminReadPort.selectByStartTimeAfter(afterStartTime);
    }
}
