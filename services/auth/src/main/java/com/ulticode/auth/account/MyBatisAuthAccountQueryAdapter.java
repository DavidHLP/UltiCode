package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountQueryMapper;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MySQL implementation of {@link AuthAccountQueryPort}. */
@Component
@ConditionalOnProperty(name = "app.auth.account-store", havingValue = "mysql", matchIfMissing = true)
@RequiredArgsConstructor
public class MyBatisAuthAccountQueryAdapter implements AuthAccountQueryPort {

    private final AuthAccountQueryMapper mapper;

    @Override
    public Optional<AuthAccountDTO> findById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findById(accountId.trim())).map(this::toDto);
    }

    @Override
    public Optional<AuthAccountDTO> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByUsername(username.trim())).map(this::toDto);
    }

    @Override
    public Optional<AuthAccountDTO> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByEmail(email.trim())).map(this::toDto);
    }

    @Override
    public List<AuthAccountDTO> queryAccounts(AccountQueryDTO query, int offset, int limit) {
        String search = query.search() != null ? query.search().trim() : null;
        String role = query.role() != null ? query.role().trim() : null;
        return mapper.queryAccounts(
                        search,
                        role,
                        query.active(),
                        query.banned(),
                        query.usernameOnly(),
                        query.sortBy(),
                        query.sortOrder(),
                        offset,
                        limit)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<AuthAccountDTO> findByIds(java.util.Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = accountIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.findByIds(ids).stream().map(this::toDto).toList();
    }

    @Override
    public long countByIdsExcludingUsernameMatch(java.util.Set<String> accountIds, String usernameQuery) {
        if (accountIds == null || accountIds.isEmpty() || usernameQuery == null || usernameQuery.isBlank()) {
            return 0;
        }
        List<String> ids = accountIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return ids.isEmpty() ? 0 : mapper.countByIdsExcludingUsernameMatch(ids, usernameQuery.trim());
    }

    @Override
    public long countAccounts(AccountQueryDTO query) {
        String search = query.search() != null ? query.search().trim() : null;
        String role = query.role() != null ? query.role().trim() : null;
        return mapper.countAccounts(search, role, query.active(), query.banned(), query.usernameOnly());
    }

    @Override
    public com.ulticode.auth.api.service.AccountQueryService.AccountStatsSummary dashboardStatsSummary(
            java.time.LocalDateTime todayStart,
            java.time.LocalDateTime weekStart,
            java.time.LocalDateTime monthStart) {
        AuthAccountQueryMapper.AccountStatsRow row =
                mapper.dashboardStatsSummary(todayStart, weekStart, monthStart);
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (AuthAccountQueryMapper.RoleCountRow roleCount : mapper.dashboardRoleCounts()) {
            if (roleCount.role() != null) {
                byRole.merge(roleCount.role(), roleCount.count(), Long::sum);
            }
        }
        return new com.ulticode.auth.api.service.AccountQueryService.AccountStatsSummary(
                row.total(), row.active(), row.banned(), row.activeToday(),
                row.activeWeek(), row.activeMonth(), Map.copyOf(byRole));
    }

    private AuthAccountDTO toDto(AuthAccountEntity entity) {
        return new AuthAccountDTO(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getRole(),
                Boolean.TRUE.equals(entity.getActive()),
                Boolean.TRUE.equals(entity.getBanned()),
                entity.getBannedReason(),
                entity.getBannedUntil(),
                entity.getJoinedAt(),
                entity.getLastLoginAt(),
                entity.getAuthzVersion() != null ? entity.getAuthzVersion() : 0L,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getJoinedAt(),
                entity.getDeletedAt());
    }
}
