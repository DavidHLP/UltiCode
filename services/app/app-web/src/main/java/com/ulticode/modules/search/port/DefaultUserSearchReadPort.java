package com.ulticode.modules.search.port;

import com.ulticode.app.api.dto.UserIndexDTO;
import com.ulticode.modules.search.port.UserSearchReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link UserSearchReadPort}.
 *
 * <p>Delegates to the owner-composed {@link UserDirectoryQueryPort} seam.
 * Auth account data is obtained through the adapter's owner query seam rather
 * than through the App datasource.
 */
@Component
@RequiredArgsConstructor
public class DefaultUserSearchReadPort implements UserSearchReadPort {

    private final UserDirectoryQueryPort userDirectoryQueryPort;

    @Override
    public List<UserIndexDTO> searchForIndex(String query, int offset, int limit) {
        if (query == null || query.isBlank() || offset < 0 || limit <= 0) {
            return List.of();
        }
        return userDirectoryQueryPort.search(query, offset, limit).stream()
                .map(UserDirectoryRow::row)
                .map(row -> new UserIndexDTO(
                        row.getId(),
                        row.getUsername(),
                        row.getName(),
                        row.getAvatar()))
                .toList();
    }

    public List<UserIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    @Override
    public long countForIndex(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        return userDirectoryQueryPort.count(query);
    }
}
