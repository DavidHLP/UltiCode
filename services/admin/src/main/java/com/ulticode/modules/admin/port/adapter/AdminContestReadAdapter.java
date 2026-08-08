package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.modules.admin.port.AdminContestReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter hiding contest mapper access behind app-api read-port.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7: replaced ContestProblemMapper direct
 * import with ContestAdminReadPort.
 */
@Component
@RequiredArgsConstructor
public class AdminContestReadAdapter implements AdminContestReadPort {

    private final ContestAdminReadPort contestAdminReadPort;

    @Override
    public long countProblemsByContestId(String contestId) {
        return contestAdminReadPort.countProblemsByContestId(contestId);
    }
}
