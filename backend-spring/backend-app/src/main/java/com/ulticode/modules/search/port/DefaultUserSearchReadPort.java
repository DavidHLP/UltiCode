package com.ulticode.modules.search.port;

import com.ulticode.app.api.dto.UserIndexDTO;
import com.ulticode.app.api.service.UserSearchReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link UserSearchReadPort}.
 *
 * <p>Migration-state Q-read of the Auth-owned {@code users} table via
 * {@link UserSearchReadMapper} (ADR-P7-APP-DECOMPOSITION rule 3 — App
 * READS users table for account display fields, no RPC dependency).
 * When the physical DB splits, swap the mapper for the Auth identity
 * query seam without touching the search consumer.
 */
@Component
@RequiredArgsConstructor
public class DefaultUserSearchReadPort implements UserSearchReadPort {

    private final UserSearchReadMapper userSearchReadMapper;

    @Override
    public List<UserIndexDTO> searchForIndex(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        return userSearchReadMapper.searchIndex(query, limit).stream()
                .map(row -> new UserIndexDTO(
                        row.getId(),
                        row.getUsername(),
                        row.getName(),
                        row.getAvatar()))
                .toList();
    }
}
