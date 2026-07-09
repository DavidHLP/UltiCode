package com.ulticode.modules.user.port;

import com.ulticode.modules.moderation.port.ModerationUserReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ModerationUserReadAdapter implements ModerationUserReadPort {

    private final UserMapper userMapper;

    @Override
    public User findById(String userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    @Override
    public Map<String, User> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}
