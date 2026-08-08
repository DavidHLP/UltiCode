package com.ulticode.modules.forum.lifecycle;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
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
 *
 * <p>P7-RELOCATE-FORUM-001: {@code UserReadProjection} replaced with
 * {@link ForumUserReadPort}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumUserLifecycleModuleTest {

    private static final String USER_ID = "u-001";
    private static final String USERNAME = "alice";
    private static final String AVATAR = "https://cdn/avatar.png";

    @Mock private ForumUserMapper forumUserMapper;
    @Mock private ForumUserReadPort forumUserReadPort;

    private DefaultForumUserLifecycleModule module;

    @BeforeEach
    void setUp() {
        module = new DefaultForumUserLifecycleModule(
                forumUserMapper, forumUserReadPort, Clock.systemUTC());
    }

    @Test
    @DisplayName("resolveOrCreate returns the existing row without re-inserting")
    void resolveOrCreate_existingRow_returnsIt() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername(USERNAME);
        existing.setAvatar(AVATAR);
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result).isSameAs(existing);
        verify(forumUserMapper, never()).insert(any(ForumUser.class));
    }

    @Test
    @DisplayName("resolveOrCreate creates the row with copied identity when missing")
    void resolveOrCreate_missingRow_inserts() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);
        ForumUserReadPort.UserSummary userSummary =
                new ForumUserReadPort.UserSummary(USER_ID, USERNAME, AVATAR);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(userSummary);
        when(forumUserMapper.insert(any(ForumUser.class))).thenReturn(1);

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getUsername()).isEqualTo(USERNAME);
        assertThat(result.getAvatar()).isEqualTo(AVATAR);
        verify(forumUserMapper).insert(any(ForumUser.class));
    }

    @Test
    @DisplayName("resolveOrCreate throws BusinessException when underlying User is missing")
    void resolveOrCreate_userMissing_throws() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> module.resolveOrCreate(USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("first-use race: concurrent threads produce one insert, all get the row")
    void resolveOrCreate_concurrentRace_collapsesToOneInsert() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean inserted = new java.util.concurrent.atomic.AtomicBoolean(false);
        ForumUser insertedRow = new ForumUser();
        insertedRow.setId(USER_ID);
        insertedRow.setUsername(USERNAME);
        insertedRow.setAvatar(AVATAR);
        // selectById returns null until insert succeeds, then returns the row
        when(forumUserMapper.selectById(USER_ID)).thenAnswer(inv -> inserted.get() ? insertedRow : null);
        ForumUserReadPort.UserSummary userSummary =
                new ForumUserReadPort.UserSummary(USER_ID, USERNAME, AVATAR);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(userSummary);
        when(forumUserMapper.insert(any(ForumUser.class))).thenAnswer(inv -> {
            inserted.set(true);
            return 1;
        });

        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<ForumUser>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(exec.submit(() -> {
                startLatch.await();
                try {
                    return module.resolveOrCreate(USER_ID);
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Let all threads race
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        // The per-userId lock collapses the race: only one thread inserts,
        // the rest re-read the just-inserted row inside the synchronized block.
        verify(forumUserMapper, times(1)).insert(any(ForumUser.class));
        exec.shutdown();
    }

    @Test
    @DisplayName("resolveOrCreate survives a DuplicateKeyException from a competing node")
    void resolveOrCreate_duplicateKeyFromOtherNode_returnsWinner() {
        ForumUser winner = new ForumUser();
        winner.setId(USER_ID);
        winner.setUsername(USERNAME);
        winner.setAvatar(AVATAR);

        when(forumUserMapper.selectById(USER_ID))
                .thenReturn(null)   // first check outside lock
                .thenReturn(null)   // re-check inside lock
                .thenReturn(winner); // re-read after DuplicateKeyException
        ForumUserReadPort.UserSummary userSummary =
                new ForumUserReadPort.UserSummary(USER_ID, USERNAME, AVATAR);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(userSummary);
        when(forumUserMapper.insert(any(ForumUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        ForumUser result = module.resolveOrCreate(USER_ID);

        assertThat(result).isSameAs(winner);
    }

    @Test
    @DisplayName("syncIdentityFields updates username and avatar when they differ")
    void syncIdentityFields_changesUsernameAndAvatar() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername("old-name");
        existing.setAvatar("old-avatar");
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);
        ForumUserReadPort.UserSummary updated =
                new ForumUserReadPort.UserSummary(USER_ID, USERNAME, AVATAR);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(updated);
        when(forumUserMapper.updateById(any(ForumUser.class))).thenReturn(1);

        module.syncIdentityFields(USER_ID);

        verify(forumUserMapper).updateById(existing);
        assertThat(existing.getUsername()).isEqualTo(USERNAME);
        assertThat(existing.getAvatar()).isEqualTo(AVATAR);
    }

    @Test
    @DisplayName("syncIdentityFields is a no-op when nothing changed")
    void syncIdentityFields_unchanged_noUpdate() {
        ForumUser existing = new ForumUser();
        existing.setId(USER_ID);
        existing.setUsername(USERNAME);
        existing.setAvatar(AVATAR);
        when(forumUserMapper.selectById(USER_ID)).thenReturn(existing);
        ForumUserReadPort.UserSummary same =
                new ForumUserReadPort.UserSummary(USER_ID, USERNAME, AVATAR);
        when(forumUserReadPort.findById(USER_ID)).thenReturn(same);

        module.syncIdentityFields(USER_ID);

        verify(forumUserMapper, never()).updateById(any(ForumUser.class));
    }

    @Test
    @DisplayName("syncIdentityFields skips silently when no forum row exists yet")
    void syncIdentityFields_noRow_noOp() {
        when(forumUserMapper.selectById(USER_ID)).thenReturn(null);

        module.syncIdentityFields(USER_ID);

        verify(forumUserReadPort, never()).findById(any());
        verify(forumUserMapper, never()).updateById(any(ForumUser.class));
    }
}
