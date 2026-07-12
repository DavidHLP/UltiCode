package com.ulticode.modules.forum.lifecycle;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default adapter for {@link ForumUserLifecyclePort}.
 *
 * <p>Owns the {@code forum_users} row: identity rule, defaults, and the
 * per-userId lock that collapses the post/comment first-use race.
 *
 * <p>The lock is a {@link ConcurrentHashMap} of per-userId monitor objects.
 * Two threads racing on the same {@code userId} serialize on the same
 * monitor; threads for different users do not contend.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultForumUserLifecycleModule implements ForumUserLifecyclePort {

    private final ForumUserMapper forumUserMapper;
    private final UserReadProjection userReadProjection;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public DefaultForumUserLifecycleModule(ForumUserMapper forumUserMapper,
                                           UserReadProjection userReadProjection,
                                           Clock clock) {
        this.forumUserMapper = forumUserMapper;
        this.userReadProjection = userReadProjection;
        this.clock = clock;
    }

    @Override
    public ForumUser resolveOrCreate(String userId) {
        ForumUser existing = forumUserMapper.selectById(userId);
        if (existing != null) {
            return existing;
        }
        Object monitor = locks.computeIfAbsent(userId, k -> new Object());
        synchronized (monitor) {
            // Re-check inside the lock: another thread may have just inserted.
            existing = forumUserMapper.selectById(userId);
            if (existing != null) {
                return existing;
            }
            User user = userReadProjection.findById(userId).orElseThrow(() -> {
                log.error("User not found when creating forum user: {}", userId);
                return new BusinessException(ErrorCode.USER_NOT_FOUND);
            });
            ForumUser fresh = new ForumUser();
            fresh.setId(userId);
            fresh.setUsername(user.getUsername());
            fresh.setAvatar(user.getAvatar());
            fresh.setKarma(0);
            fresh.setCreatedAt(LocalDateTime.now(clock));
            try {
                forumUserMapper.insert(fresh);
                log.debug("Created forum user entry for user: {} with id: {}",
                        user.getUsername(), userId);
            } catch (DuplicateKeyException raceLost) {
                // Another process / node inserted between our re-check and
                // our insert. Re-read so the caller still gets a row.
                ForumUser winner = forumUserMapper.selectById(userId);
                if (winner == null) {
                    throw raceLost;
                }
                return winner;
            }
            return fresh;
        }
    }

    @Override
    public void syncIdentityFields(String userId) {
        ForumUser existing = forumUserMapper.selectById(userId);
        if (existing == null) {
            // No row yet — nothing to sync. Caller should call resolveOrCreate
            // before publishing to populate identity.
            return;
        }
        User user = userReadProjection.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        boolean changed = false;
        if (!equalsStr(existing.getUsername(), user.getUsername())) {
            existing.setUsername(user.getUsername());
            changed = true;
        }
        if (!equalsStr(existing.getAvatar(), user.getAvatar())) {
            existing.setAvatar(user.getAvatar());
            changed = true;
        }
        if (changed) {
            forumUserMapper.updateById(existing);
        }
    }

    private static boolean equalsStr(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}