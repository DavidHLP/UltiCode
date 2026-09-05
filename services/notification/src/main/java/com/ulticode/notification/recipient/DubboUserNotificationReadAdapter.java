package com.ulticode.notification.recipient;

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
import org.springframework.stereotype.Component;

/** Notification-side client for Auth-owned recipient reads. */
@Component
public class DubboUserNotificationReadAdapter implements UserNotificationReadPort {

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private NotificationRecipientQueryService recipientQueryService;

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    /** Focused-test constructor; Dubbo fields are injected in production. */
    DubboUserNotificationReadAdapter(
            NotificationRecipientQueryService recipientQueryService,
            IdentityQueryService identityQueryService) {
        this.recipientQueryService = recipientQueryService;
        this.identityQueryService = identityQueryService;
    }

    public DubboUserNotificationReadAdapter() {
    }

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
        if (recipientQueryService == null) {
            throw new IllegalStateException("Auth recipient provider is unavailable");
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
        if (identityQueryService == null) {
            throw new IllegalStateException("Auth identity provider is unavailable");
        }
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
