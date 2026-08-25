package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import java.util.Map;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo adapter for {@link SubmissionUserReadPort} — user profile facts are
 * owned by {@code backend-app} (App schema) and read through its provider.
 *
 * <p>SPLIT-004 slice-6: the Submission owner's user-facing read VOs need
 * username/name/avatar; Auth's {@code IdentityQueryService} only exposes
 * username, so the App-owned user-profile read is the DEC-011-compliant
 * seam. Mirrors the write path's {@code ProblemFactsDubboAdapter} pattern.
 */
@Component
@Primary
public class SubmissionUserReadDubboAdapter implements SubmissionUserReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionUserReadPort appUserRead;

    @Override
    public boolean existsById(String userId) {
        return appUserRead.existsById(userId);
    }

    @Override
    public UserSummary findById(String userId) {
        return appUserRead.findById(userId);
    }

    @Override
    public Map<String, UserSummary> findAllById(Iterable<String> userIds) {
        return appUserRead.findAllById(userIds);
    }
}
