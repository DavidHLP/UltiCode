package com.ulticode.modules.forum.lifecycle;

import com.ulticode.modules.forum.entity.ForumUser;

/**
 * Deep module that owns the {@code forum_users} row lifecycle.
 *
 * <p>Both {@code ForumPostServiceImpl} and {@code ForumCommentServiceImpl}
 * used to duplicate an {@code ensureForumUserExists} private helper that
 * mirrored the same identity rule (the row id IS the global user id),
 * the same defaults (karma=0, username/avatar copied from {@code User}),
 * and the same check-then-insert dance. Two writers can race on first use
 * (post + comment on the same freshly-registered user), producing a
 * duplicate-key error in one of them.
 *
 * <p>This port concentrates:
 * <ol>
 *   <li><b>Identity</b>: {@code ForumUser.id == User.id} — enforced here so
 *       callers cannot fabricate a different id.</li>
 *   <li><b>Creation policy</b>: row defaults (karma, username, avatar)
 *       live in one place.</li>
 *   <li><b>Concurrency</b>: per-userId locking so the first-use race
 *       collapses to a single insert and the second writer re-reads.</li>
 *   <li><b>Field sync</b>: a method to refresh username/avatar when the
 *       global {@code User} record changes.</li>
 * </ol>
 *
 * <p>All callers (post/comment write paths) inject this port and stop
 * touching {@code ForumUserMapper} directly.
 *
 * @author ulticode
 */
public interface ForumUserLifecyclePort {

    /**
     * Resolve or create the forum-user row for {@code userId}. Returns the
     * persisted {@link ForumUser}; never null.
     *
     * @param userId the global user id (becomes the forum-user id)
     * @return the existing or newly-created {@link ForumUser}
     * @throws com.ulticode.common.exception.BusinessException
     *         when the underlying {@code User} is missing
     */
    ForumUser resolveOrCreate(String userId);

    /**
     * Refresh denormalised identity fields on the forum-user row from the
     * latest {@code User} record. No-op if no forum-user row exists yet.
     *
     * @param userId the global user id
     */
    void syncIdentityFields(String userId);
}