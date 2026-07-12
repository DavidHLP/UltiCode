package com.ulticode.modules.forum.lifecycle;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultForumUserLifecycleModule}.
 *
 * <p>The architecture-review task #2 mandate: post + comment write paths
 * must share one lifecycle that collapses the first-use race. These tests
 * verify that contract without spinning up a real DB.
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumUserLifecycleModuleTest {

    private static final String USER_ID = "u-001";
    private static final String USERNAME = "alice";
    private static final String AVATAR = "https://cdn/avatar.png";

    @Mock private ForumUserMapper forumUserMapper;
    @Mock private UserReadProjection userReadProjection;

    private DefaultForumUserLifecycleModule module;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-12T16:00:00Z"), ZoneId.of("UTC"));
        module = new DefaultForumUserLifecycleModule(forumUserMapper, userReadProjection, fixed);
    }

    @Test
    @DisplayName("resolveOrCreate returns the existing row without re-inserting")
    void resolveOrCreate_existingRow_returnsIt() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername(USERNAME);
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result).isSameAs(existing);
        verify(forumUserMapper, never()).insert(any(ForumUser.class));
    }

    @Test
    @DisplayName("resolveOrCreate creates the row with copied identity when missing")
    void resolveOrCreate_missingRow_inserts() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setAvatar(AVATAR);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(u));

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getUsername()).isEqualTo(USERNAME);
        assertThat(result.getAvatar()).isEqualTo(AVATAR);
        assertThat(result.getKarma()).isZero();
        assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 12, 16, 0));
        verify(forumUserMapper, times(1)).insert(any(ForumUser.class));
    }

    @Test
    @DisplayName("resolveOrCreate throws BusinessException when underlying User is missing")
    void resolveOrCreate_userMissing_throws() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> module.resolveOrCreate(USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(forumUserMapper, never()).insert(any(ForumUser.class));
    }

    @Test
    @DisplayName("first-use race: concurrent threads produce one insert, all get the row")
    void resolveOrCreate_concurrentRace_collapsesToOneInsert() throws Exception {
        AtomicInteger insertCount = new AtomicInteger();
        when(forumUserMapper.selectById(USER_ID)).thenAnswer(inv -> {
            if (insertCount.get() == 0) return null;
            ForumUser fu = new ForumUser();
            fu.setId(USER_ID);
            fu.setUsername(USERNAME);
            fu.setKarma(0);
            return fu;
        });
        when(forumUserMapper.insert(any(ForumUser.class))).thenAnswer(inv -> {
            insertCount.incrementAndGet();
            return 1;
        });
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setAvatar(AVATAR);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(u));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ForumUser>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return module.resolveOrCreate(USER_ID);
            }));
        }
        start.countDown();

        for (Future<ForumUser> f : futures) {
            ForumUser fu = f.get(5, TimeUnit.SECONDS);
            assertThat(fu.getId()).isEqualTo(USER_ID);
        }
        pool.shutdown();
        assertThat(insertCount.get())
                .as("per-userId lock must collapse the race to a single insert")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("resolveOrCreate survives a DuplicateKeyException from a competing node")
    void resolveOrCreate_duplicateKeyFromOtherNode_returnsWinner() {
        ForumUser winner = new ForumUser();
        winner.setId(USER_ID);
        winner.setUsername(USERNAME);
        when(forumUserMapper.selectById(USER_ID))
                .thenReturn(null, null, winner);
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setAvatar(AVATAR);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(u));
        when(forumUserMapper.insert(any(ForumUser.class)))
                .thenThrow(new DuplicateKeyException("PK collision from another node"));

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result).isSameAs(winner);
    }

    @Test
    @DisplayName("syncIdentityFields updates username and avatar when they differ")
    void syncIdentityFields_changesUsernameAndAvatar() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername("oldName");
        existing.setAvatar("oldUrl");
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setAvatar(AVATAR);
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(u));

        module.syncIdentityFields(USER_ID);

        assertThat(existing.getUsername()).isEqualTo(USERNAME);
        assertThat(existing.getAvatar()).isEqualTo(AVATAR);
        verify(forumUserMapper).updateById(existing);
    }

    @Test
    @DisplayName("syncIdentityFields is a no-op when nothing changed")
    void syncIdentityFields_unchanged_noUpdate() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername(USERNAME);
        existing.setAvatar(AVATAR);
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setAvatar(AVATAR);
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(u));

        module.syncIdentityFields(USER_ID);

        verify(forumUserMapper, never()).updateById(any(ForumUser.class));
    }

    @Test
    @DisplayName("syncIdentityFields skips silently when no forum row exists yet")
    void syncIdentityFields_noRow_noOp() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);

        module.syncIdentityFields(USER_ID);

        verify(forumUserMapper, never()).updateById(any(ForumUser.class));
        verify(userReadProjection, never()).findById(any());
    }
}