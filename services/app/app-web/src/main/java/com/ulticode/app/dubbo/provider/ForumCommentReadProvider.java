package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentPage;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentRow;
import com.ulticode.modules.forum.port.DefaultForumCommentReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * ADMIN-007: Dubbo provider for {@link ForumCommentReadPort} exported by
 * {@code backend-app} so backend-admin reads forum comments without
 * importing the forum module.
 *
 * <p>Delegates to the concrete {@link DefaultForumCommentReadAdapter} —
 * never to the port interface itself.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumCommentReadProvider implements ForumCommentReadPort {

    private final DefaultForumCommentReadAdapter delegate;

    @Override
    public ForumCommentPage page(Boolean isFlagged, Boolean isDeleted, String search, String postId,
                                 String sortBy, String sortOrder, int page, int limit) {
        return delegate.page(isFlagged, isDeleted, search, postId, sortBy, sortOrder, page, limit);
    }

    @Override
    public ForumCommentRow getById(String commentId) {
        return delegate.getById(commentId);
    }
}
