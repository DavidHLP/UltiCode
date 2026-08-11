package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.modules.forum.port.DefaultForumTagReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * ADMIN-007: Dubbo provider for {@link ForumTagReadPort} exported by
 * {@code backend-app} so backend-admin reads forum tags without importing
 * the forum module.
 *
 * <p>Delegates to the concrete {@link DefaultForumTagReadAdapter} — never
 * to the port interface itself.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumTagReadProvider implements ForumTagReadPort {

    private final DefaultForumTagReadAdapter delegate;

    @Override
    public ForumTagPage page(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        return delegate.page(search, pageNum, pageSize, sortBy, sortOrder);
    }

    @Override
    public ForumTagRow getById(String id) {
        return delegate.getById(id);
    }
}
