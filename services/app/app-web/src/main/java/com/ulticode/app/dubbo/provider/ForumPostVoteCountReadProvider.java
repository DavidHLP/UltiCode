package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ForumPostVoteCountReadPort;
import com.ulticode.modules.vote.port.adapter.ForumPostVoteCountReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.Map;

/**
 * ADMIN-007: Dubbo provider for {@link ForumPostVoteCountReadPort}
 * exported by {@code backend-app} so backend-admin reads forum-post vote
 * counts without importing the vote module.
 *
 * <p>Delegates to the concrete {@link ForumPostVoteCountReadAdapter}
 * (vote module) — never to the port interface itself.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumPostVoteCountReadProvider implements ForumPostVoteCountReadPort {

    private final ForumPostVoteCountReadAdapter delegate;

    @Override
    public Map<String, Long> countVoteUpByTargets(Collection<String> postIds) {
        return delegate.countVoteUpByTargets(postIds);
    }

    @Override
    public Map<String, Long> countVoteDownByTargets(Collection<String> postIds) {
        return delegate.countVoteDownByTargets(postIds);
    }
}
