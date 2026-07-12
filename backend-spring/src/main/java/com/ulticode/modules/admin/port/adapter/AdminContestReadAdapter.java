package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminContestReadPort;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AdminContestReadPort}, living in the admin
 * module and hiding {@link ContestProblemMapper} from
 * {@code AdminContestServiceImpl}.
 *
 * <p>The contest module's mapper stays the source of truth; this adapter is
 * the only admin-side code that imports it. Subsequent contest read needs
 * (participant counts, announcement lists) grow this adapter &mdash; never
 * the service's import list.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminContestReadAdapter implements AdminContestReadPort {

    private final ContestProblemMapper contestProblemMapper;

    @Override
    public long countProblemsByContestId(String contestId) {
        return contestProblemMapper.countByContestId(contestId);
    }
}
