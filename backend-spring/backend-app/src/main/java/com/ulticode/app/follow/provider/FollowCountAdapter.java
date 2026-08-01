package com.ulticode.app.follow.provider;

import com.ulticode.app.api.service.FollowCountPort;
import com.ulticode.modules.follow.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing FollowCountPort backed by backend-app's FollowMapper.
 */
@Component
@RequiredArgsConstructor
public class FollowCountAdapter implements FollowCountPort {
    private final FollowMapper followMapper;

    @Override
    public long countFollowers(String userId) {
        return followMapper.countByFollowingId(userId);
    }

    @Override
    public long countFollowing(String userId) {
        return followMapper.countByFollowerId(userId);
    }
}
