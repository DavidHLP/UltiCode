package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dubbo provider for {@link UserProfileQueryService}.
 *
 * <p>Reads directly from the App-owned {@code user_profiles} table. Missing profile
 * rows return a stable empty profile DTO without fabricating account credentials.
 */
@Component
@DubboService(version = "1.0.0")
public class UserProfileQueryProvider implements UserProfileQueryService {

    private static final String DEFAULT_TRACE_ID = "t-system";

    private final UserProfileReadMapper profileReadMapper;

    public UserProfileQueryProvider(UserProfileReadMapper profileReadMapper) {
        this.profileReadMapper = profileReadMapper;
    }

    @Override
    public RpcResult<UserProfileDTO> getProfileByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return RpcResult.success(UserProfileDTO.empty(""), DEFAULT_TRACE_ID);
        }
        String cleanId = accountId.trim();
        UserProfileDTO dto = profileReadMapper.findByAccountId(cleanId);
        if (dto == null) {
            dto = UserProfileDTO.empty(cleanId);
        }
        return RpcResult.success(dto, DEFAULT_TRACE_ID);
    }

    @Override
    public RpcResult<List<UserProfileDTO>> getProfilesByAccountIds(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(List.of(), DEFAULT_TRACE_ID);
        }
        Set<String> cleanIds = accountIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (cleanIds.isEmpty()) {
            return RpcResult.success(List.of(), DEFAULT_TRACE_ID);
        }

        List<UserProfileDTO> existing = profileReadMapper.findByAccountIds(cleanIds);
        Set<String> foundAccountIds = existing.stream()
                .filter(Objects::nonNull)
                .map(UserProfileDTO::accountId)
                .collect(Collectors.toSet());

        List<UserProfileDTO> allProfiles = new java.util.ArrayList<>(existing);
        for (String id : cleanIds) {
            if (!foundAccountIds.contains(id)) {
                allProfiles.add(UserProfileDTO.empty(id));
            }
        }
        return RpcResult.success(allProfiles, DEFAULT_TRACE_ID);
    }
}
