package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.AdminForumCommunityPage;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.modules.forum.port.DefaultAdminForumReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.Map;

/**
 * ADMIN-007: Dubbo provider for {@link AdminForumReadPort} exported by
 * {@code backend-app} so backend-admin reads forum posts / communities /
 * post titles without importing the forum module.
 *
 * <p>Delegates to the concrete {@link DefaultAdminForumReadAdapter} —
 * never to the port interface itself — so the app bean graph keeps
 * exactly one primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumAdminReadProvider implements AdminForumReadPort {

    private final DefaultAdminForumReadAdapter delegate;

    @Override
    public AdminForumPostPage listPosts(AdminForumPostQuery query) {
        return delegate.listPosts(query);
    }

    @Override
    public AdminForumPostRowDTO getPost(String postId) {
        return delegate.getPost(postId);
    }

    @Override
    public AdminForumCommunityPage listCommunities(int page, int limit, String search) {
        return delegate.listCommunities(page, limit, search);
    }

    @Override
    public Map<String, String> findPostTitlesByIds(Collection<String> postIds) {
        return delegate.findPostTitlesByIds(postIds);
    }
}
