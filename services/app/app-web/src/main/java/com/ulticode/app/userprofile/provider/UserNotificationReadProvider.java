package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.app.api.service.UserNotificationReadPort;
import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.auth.api.service.NotificationRecipientQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

/** App-owned notification recipient seam backed by Auth-owned account data. */
@Component
@DubboService(group = "backend-app", version = "1.0.0")
public class UserNotificationReadProvider implements UserNotificationReadPort {

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private NotificationRecipientQueryService recipientQueryService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public NotificationRecipientDTO findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        List<NotificationRecipientDTO> recipients = findByIds(Set.of(userId));
        return recipients.isEmpty() ? null : recipients.get(0);
    }

    @Override
    public List<NotificationRecipientDTO> findByIds(Collection<String> userIds) {
        Set<String> validIds = cleanIds(userIds);
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }
        RpcResult<List<AuthNotificationRecipientDTO>> response = recipientQueryService
                .findRecipients(validIds);
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Auth notification recipient lookup failed");
        }
        return response.data().stream()
                .filter(Objects::nonNull)
                .map(this::toRecipient)
                .toList();
    }

    @Override
    public List<String> findAllActiveIds() {
        RpcResult<List<String>> response = identityQueryService.findActiveAccountIds();
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Auth active recipient lookup failed");
        }
        return response.data().stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    }

    private static Set<String> cleanIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return userIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
    }

    private NotificationRecipientDTO toRecipient(AuthNotificationRecipientDTO recipient) {
        return new NotificationRecipientDTO(
                recipient.accountId(), recipient.email(), recipient.active(), recipient.banned());
    }
}
