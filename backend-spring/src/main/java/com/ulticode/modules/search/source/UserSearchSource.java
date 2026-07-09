package com.ulticode.modules.search.source;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search source for the user domain. Owns:
 * <ul>
 *   <li>The {@link UserMapper} call and the {@code is_deleted} predicate.</li>
 *   <li>The username / name LIKE matching and the LIMIT cap.</li>
 *   <li>The {@code /users/{username}} URL template.</li>
 *   <li>The user metadata projection (username, avatar).</li>
 * </ul>
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class UserSearchSource implements SearchSource {

    private final UserMapper userMapper;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.USERS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                        .like("username", query)
                        .or()
                        .like("name", query))
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<User> users = userMapper.selectList(wrapper);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(users.size());
        for (User user : users) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", user.getUsername());
            if (user.getAvatar() != null) {
                metadata.put("avatar", user.getAvatar());
            }

            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(String.valueOf(user.getId()))
                    .type(SearchIndexType.USERS.name())
                    .title(user.getUsername())
                    .description(user.getName())
                    .url(buildUrl(user.getUsername()))
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
