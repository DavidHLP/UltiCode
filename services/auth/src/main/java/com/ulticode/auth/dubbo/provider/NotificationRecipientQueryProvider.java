package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.NotificationRecipientQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Auth-owned minimum recipient projection for notification delivery. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class NotificationRecipientQueryProvider implements NotificationRecipientQueryService {

    private static final String TRACE_ID = "t-system";
    private final AuthAccountPort authAccountPort;

    public NotificationRecipientQueryProvider(AuthAccountPort authAccountPort) {
        this.authAccountPort = authAccountPort;
    }

    @Override
    public RpcResult<List<AuthNotificationRecipientDTO>> findRecipients(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), TRACE_ID);
        }
        Set<String> validIds = accountIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), TRACE_ID);
        }
        try {
            List<AuthAccountRecord> records = authAccountPort.findByIds(validIds);
            if (records == null) {
                records = Collections.emptyList();
            }
            return RpcResult.success(records.stream()
                    .map(record -> new AuthNotificationRecipientDTO(
                            record.id(), record.email(),
                            Boolean.TRUE.equals(record.isActive()),
                            Boolean.TRUE.equals(record.isBanned())))
                    .toList(), TRACE_ID);
        } catch (RuntimeException exception) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, TRACE_ID);
        }
    }
}
