package com.ulticode.modules.search.source;

import com.ulticode.app.api.dto.UserIndexDTO;
import com.ulticode.app.api.service.UserSearchReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search source for the user domain. Owns:
 * <ul>
 *   <li>The {@link UserSearchReadPort} call (App-side Q-read of the
 *       Auth-owned users table; non-deleted predicate, username/name
 *       LIKE matching and LIMIT cap enforced by the port adapter).</li>
 *   <li>The {@code /users/{username}} URL template.</li>
 *   <li>The user metadata projection (username, avatar).</li>
 * </ul>
 *
 * <p>P7-SEARCH-RELOCATE-001: replaced the direct legacy {@code UserMapper}
 * dependency with {@link UserSearchReadPort} (port extracted in
 * P7-SEARCH-CONTRACTS-001). Behavioral parity with the previous
 * {@code QueryWrapper} path: same LIKE columns, same is_deleted=0
 * predicate, same LIMIT cap; results additionally arrive in a
 * deterministic username order (port adapter).
 */
@Service
@RequiredArgsConstructor
public class UserSearchSource implements SearchSource {

    private final UserSearchReadPort userSearchReadPort;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.USERS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        List<UserIndexDTO> users = userSearchReadPort.searchForIndex(query, limit);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(users.size());
        for (UserIndexDTO user : users) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", user.username());
            if (user.avatar() != null) {
                metadata.put("avatar", user.avatar());
            }

            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(user.accountId())
                    .type(SearchIndexType.USERS.name())
                    .title(user.username())
                    .description(user.name())
                    .url(buildUrl(user.username()))
                    .metadata(metadata)
                    .build());
        }
        return results;
    }

    @Override
    public String buildUrl(String entityId) {
        return "/u/" + entityId;
    }
}
