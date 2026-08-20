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
 *   <li>The {@link UserSearchReadPort} call (owner-composed Auth account and
 *       App profile read; non-deleted predicate and username/name matching
 *       are enforced by the port adapter).</li>
 *   <li>The {@code /users/{username}} URL template.</li>
 *   <li>The user metadata projection (username, avatar).</li>
 * </ul>
 *
 * <p>Results arrive in a deterministic username order from the read port.
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
        List<UserIndexDTO> users = userSearchReadPort.searchForIndex(query, offset, limit);

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
    public long countDatabase(String query) {
        return userSearchReadPort.countForIndex(query);
    }

    @Override
    public String buildUrl(String entityId) {
        return "/u/" + entityId;
    }
}
