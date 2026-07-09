package com.ulticode.modules.user.port;

import com.ulticode.modules.backup.port.UserLookupPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production adapter for {@link UserLookupPort}. Reads users in bulk via
 * {@link UserMapper#selectBatchIds} and reduces the {@code User} entities to
 * a {@code Map<userId, username>}.
 *
 * <p>Replaces the direct {@code UserMapper} import in
 * {@code BackupServiceImpl}. The user module owns the read; the backup module
 * consumes the port.
 *
 * <p><strong>Non-throwing contract:</strong> blank or absent input returns
 * an empty map; missing users are silently omitted (matches the previous
 * inline behavior in {@code BackupServiceImpl.toVO(Backup, Map<String, User>)}
 * which used a null-tolerant {@code userMap.get(...)} lookup).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class UserLookupAdapter implements UserLookupPort {

    private final UserMapper userMapper;

    @Override
    public Map<String, String> findUsernamesByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (User user : users) {
            if (user != null && user.getId() != null) {
                result.put(user.getId(), user.getUsername());
            }
        }
        return result;
    }
}
