package com.ulticode.modules.user.port;

import com.ulticode.common.audit.BanCheckPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link BanCheckPort}. Reads the user's ban
 * flag via the user module's own mapper; the ban-check aspect never
 * imports {@code User} or {@code UserMapper} directly.
 *
 * <p>Two adapters justify the seam: this class is the production path;
 * a static variant lives in {@code common/audit/} test sources for
 * aspect tests.
 *
 * <p><strong>Non-throwing contract:</strong> if the user does not
 * exist, the principal is treated as not banned (matches the previous
 * inline behavior — see {@code BanCheckAspect#checkBan}).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class UserBanCheckAdapter implements BanCheckPort {

    private final UserMapper userMapper;

    @Override
    public boolean isBanned(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getIsBanned());
    }
}
