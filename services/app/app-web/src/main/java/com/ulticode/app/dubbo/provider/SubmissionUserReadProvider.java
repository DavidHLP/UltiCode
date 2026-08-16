package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.port.DefaultSubmissionUserReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Map;

/**
 * Dubbo provider for {@link SubmissionUserReadPort} exported by
 * {@code backend-app} so the Submission owner resolves user summaries
 * through the App-owned user-profile store (App schema) instead of reading
 * user tables cross-service.
 *
 * <p>SPLIT-004 slice-6: the Submission read provider needs user
 * name/username/avatar for detail/list VOs; Auth's
 * {@code IdentityQueryService} only exposes username, so the App keeps
 * owning the profile read and this provider is the DEC-011-compliant seam.
 * The App-side {@link DefaultSubmissionUserReadAdapter} stays the single
 * local implementation; this RPC export delegates to it.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionUserReadProvider implements SubmissionUserReadPort {

    private final DefaultSubmissionUserReadAdapter delegate;

    @Override
    public boolean existsById(String userId) {
        return delegate.existsById(userId);
    }

    @Override
    public UserSummary findById(String userId) {
        return delegate.findById(userId);
    }

    @Override
    public Map<String, UserSummary> findAllById(Iterable<String> userIds) {
        return delegate.findAllById(userIds);
    }
}
