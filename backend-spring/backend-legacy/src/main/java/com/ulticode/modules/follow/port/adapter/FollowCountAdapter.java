package com.ulticode.modules.follow.port.adapter;

import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.port.FollowCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link FollowCountPort}. Reads the
 * follower / following counts via {@link FollowMapper}.
 *
 * <p>Replaces the direct {@code FollowMapper} import in
 * {@code DefaultUserReadProjection}. The follow module owns the read;
 * other modules consume the port.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class FollowCountAdapter implements FollowCountPort {

    private final FollowMapper followMapper;

    @Override
    public int countFollowers(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        return followMapper.countByFollowingId(userId);
    }

    @Override
    public int countFollowing(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        return followMapper.countByFollowerId(userId);
    }
}
