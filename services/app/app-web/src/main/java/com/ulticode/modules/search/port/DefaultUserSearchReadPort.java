package com.ulticode.modules.search.port;

import com.ulticode.app.api.dto.UserIndexDTO;
import com.ulticode.app.api.service.UserSearchReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link UserSearchReadPort}.
 *
 * <p>Delegates to the owner-composed {@link UserSearchReadMapper} port. Auth
 * account data is obtained through the adapter's owner query seam rather than
 * through the App datasource.
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
