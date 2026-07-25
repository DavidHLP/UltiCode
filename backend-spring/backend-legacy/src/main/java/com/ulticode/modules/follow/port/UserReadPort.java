package com.ulticode.modules.follow.port;

import com.ulticode.modules.user.entity.User;

import java.util.Collection;
import java.util.Map;

public interface UserReadPort {

    boolean exists(String userId);

    User findById(String userId);

    Map<String, User> findByIds(Collection<String> userIds);
}
