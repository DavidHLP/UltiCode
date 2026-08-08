package com.ulticode.modules.forum.lifecycle;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import com.ulticode.app.error.ForumErrorCode;
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
 * <p>P7-RELOCATE-FORUM-001: {@code UserReadProjection} replaced with
 * {@link ForumUserReadPort}.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultForumUserLifecycleModule implements ForumUserLifecyclePort {

    private final ForumUserMapper forumUserMapper;
    private final ForumUserReadPort forumUserReadPort;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public DefaultForumUserLifecycleModule(ForumUserMapper forumUserMapper,
                                           ForumUserReadPort forumUserReadPort,
                                           Clock clock) {
        this.forumUserMapper = forumUserMapper;
        this.forumUserReadPort = forumUserReadPort;
        this.clock = clock;
    }

    @Override
    public ForumUser resolveOrCreate(String userId) {
        ForumUser existing = forumUserMapper.selectById(userId);
        if (existing != null) {
            return existing;
        }
        Object monitor = locks.computeIfAbsent(userId, k -> new Object());
        try {
            synchronized (monitor) {
                // Re-check inside the lock: another thread may have just inserted.
                existing = forumUserMapper.selectById(userId);
                if (existing != null) {
                    return existing;
                }
                ForumUserReadPort.UserSummary user = forumUserReadPort.findById(userId);
                if (user == null) {
                    log.error("User not found when creating forum user: {}", userId);
                    throw new BusinessException(ForumErrorCode.FORUM_POST_NOT_FOUND);
                }
                ForumUser fresh = new ForumUser();
                fresh.setId(userId);
                fresh.setUsername(user.username());
                fresh.setAvatar(user.avatar());
                fresh.setKarma(0);
                fresh.setCreatedAt(LocalDateTime.now(clock));
                try {
                    forumUserMapper.insert(fresh);
                    log.debug("Created forum user entry for user: {} with id: {}",
                            user.username(), userId);
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
        } finally {
            locks.remove(userId);
        }
    }

    @Override
    public void syncIdentityFields(String userId) {
        ForumUser existing = forumUserMapper.selectById(userId);
        if (existing == null) {
            return;
        }
        ForumUserReadPort.UserSummary user = forumUserReadPort.findById(userId);
        if (user == null) {
            return;
        }
        boolean changed = false;
        if (!equalsStr(existing.getUsername(), user.username())) {
            existing.setUsername(user.username());
            changed = true;
        }
        if (!equalsStr(existing.getAvatar(), user.avatar())) {
            existing.setAvatar(user.avatar());
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
