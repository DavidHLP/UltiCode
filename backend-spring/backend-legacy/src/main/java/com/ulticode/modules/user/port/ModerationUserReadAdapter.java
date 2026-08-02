package com.ulticode.modules.user.port;

import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.app.api.service.ModerationUserReadPort;
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
    public ModerationUserInfo findById(String userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user != null ? new ModerationUserInfo(user.getId(), user.getUsername()) : null;
    }

    @Override
    public Map<String, ModerationUserInfo> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new ModerationUserInfo(u.getId(), u.getUsername())));
    }
}
