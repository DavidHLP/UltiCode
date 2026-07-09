package com.ulticode.modules.user.port;

import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

    private final UserMapper userMapper;

    @Override
    public boolean exists(String userId) {
        if (userId == null) {
            return false;
        }
        return userMapper.selectById(userId) != null;
    }

    @Override
    public User findById(String userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }
}
