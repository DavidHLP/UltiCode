package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class UserIdentityQueryProvider implements IdentityQueryService {

    private final AuthAccountPort authAccountPort;

    public UserIdentityQueryProvider(AuthAccountPort authAccountPort) {
        this.authAccountPort = authAccountPort;
    }

    @Override
    public RpcResult<UserIdentityDTO> getIdentity(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-system");
        }
        Optional<AuthAccountRecord> accountOpt = authAccountPort.findById(accountId);
        if (accountOpt.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-system");
        }
        return RpcResult.success(toDto(accountOpt.get()), "t-system");
    }

    @Override
    public RpcResult<List<UserIdentityDTO>> batchGetIdentity(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), "t-system");
        }
        Set<String> validIds = accountIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        if (validIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), "t-system");
        }

        List<AuthAccountRecord> records = authAccountPort.findByIds(validIds);
        if (records == null) {
            records = Collections.emptyList();
        }

        List<UserIdentityDTO> dtos = records.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return RpcResult.success(dtos, "t-system");
    }

    private UserIdentityDTO toDto(AuthAccountRecord record) {
        return new UserIdentityDTO(
                record.id(),
                record.username(),
                record.role(),
                Boolean.TRUE.equals(record.isActive()),
                Boolean.TRUE.equals(record.isBanned())
        );
    }
}
