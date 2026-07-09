package com.ulticode.modules.follow.port;

import com.ulticode.modules.user.entity.User;

public interface UserReadPort {

    boolean exists(String userId);

    User findById(String userId);
}
