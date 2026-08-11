package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.AdminForumCommunityPage;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.ForumPostVoteCountReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ADMIN-007: Dubbo consumer adapter registering {@link AdminForumReadPort}
 * as a local admin bean, backed by the {@code backend-app} providers
 * ({@code ForumAdminReadProvider} + {@code ForumPostVoteCountReadProvider}).
 *
 * <p>This adapter is the only local bean of that type. It composes the
 * forum-owned post rows with the vote-owned up/down counts (the same
 * cross-module enrichment the legacy projection did with
 * {@code ForumCommentMapper} + {@code EdgeOperationMapper}), so the
 * projection stays entity-free. Read references use the query RPC policy
 * (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboAdminForumReadAdapter implements AdminForumReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AdminForumReadPort forumReadPort;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ForumPostVoteCountReadPort voteCountReadPort;

    @Override
    public AdminForumPostPage listPosts(AdminForumPostQuery query) {
        AdminForumPostPage page = forumReadPort.listPosts(query);
        List<AdminForumPostRowDTO> rows = page.rows();
        if (rows.isEmpty()) {
            return page;
        }
        List<String> postIds = rows.stream().map(AdminForumPostRowDTO::getId).toList();
        Map<String, Long> upvotes = voteCountReadPort.countVoteUpByTargets(postIds);
        Map<String, Long> downvotes = voteCountReadPort.countVoteDownByTargets(postIds);
        for (AdminForumPostRowDTO row : rows) {
            row.setUpvotes(upvotes.getOrDefault(row.getId(), 0L).intValue());
            row.setDownvotes(downvotes.getOrDefault(row.getId(), 0L).intValue());
        }
        return page;
    }

    @Override
    public AdminForumPostRowDTO getPost(String postId) {
        AdminForumPostRowDTO row = forumReadPort.getPost(postId);
        if (row == null) {
            return null;
        }
        row.setUpvotes(voteCountReadPort.countVoteUpByTargets(List.of(postId)).getOrDefault(postId, 0L).intValue());
        row.setDownvotes(voteCountReadPort.countVoteDownByTargets(List.of(postId)).getOrDefault(postId, 0L).intValue());
        return row;
    }

    @Override
    public AdminForumCommunityPage listCommunities(int page, int limit, String search) {
        return forumReadPort.listCommunities(page, limit, search);
    }

    @Override
    public Map<String, String> findPostTitlesByIds(Collection<String> postIds) {
        return forumReadPort.findPostTitlesByIds(postIds);
    }
}
