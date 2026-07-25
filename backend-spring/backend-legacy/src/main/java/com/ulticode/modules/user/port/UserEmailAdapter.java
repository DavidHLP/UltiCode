package com.ulticode.modules.user.port;

import com.ulticode.modules.notification.port.UserEmailPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEmailAdapter implements UserEmailPort {

    private final UserMapper userMapper;

    @Override
    public String findEmail(String userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user != null ? user.getEmail() : null;
    }
}
