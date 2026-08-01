package com.ulticode.app.follow.provider;

import com.ulticode.app.api.event.FollowDomainEvent;
import com.ulticode.app.api.event.FollowEventIngestionPort;
import com.ulticode.app.api.event.FollowEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Event publisher that dispatches follow domain events via Dubbo RPC
 * to the legacy event consumer in backend-legacy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFollowEventPublisher implements FollowEventPublisher {

    @DubboReference(group = "backend-legacy", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private FollowEventIngestionPort followEventIngestionPort;

    @Override
    public void publishFollowEvent(String followerId, String followerUsername, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        FollowDomainEvent event = new FollowDomainEvent(followerId, followerUsername, targetUserId, targetFollowerCount, followerFollowingCount, true);
        log.info("Follow event published: followerId={}, followerUsername={}, targetUserId={}", followerId, followerUsername, targetUserId);

        try {
            followEventIngestionPort.ingestFollowEvent(event);
        } catch (Exception e) {
            log.warn("Cross-process Dubbo follow event ingestion failed: {}", e.getMessage());
        }
    }

    @Override
    public void publishUnfollowEvent(String followerId, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        FollowDomainEvent event = new FollowDomainEvent(followerId, null, targetUserId, targetFollowerCount, followerFollowingCount, false);
        log.info("Unfollow event published: followerId={}, targetUserId={}", followerId, targetUserId);

        try {
            followEventIngestionPort.ingestFollowEvent(event);
        } catch (Exception e) {
            log.warn("Cross-process Dubbo unfollow event ingestion failed: {}", e.getMessage());
        }
    }
}
