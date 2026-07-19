package com.ulticode.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.impl.UserManagementServiceImpl;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the Administrator User lifecycle audit semantics.
 *
 * <p>Bulk operations previously self-invoked the {@code @Audited} single
 * methods; because {@code @Audited} is a Spring AOP aspect, self-invocation
 * bypassed the proxy and emitted no audit for bulk ban / unban, and bulk
 * delete recorded a different audit shape than single delete. These tests
 * prove the bulk paths now emit audit through {@link AuditRecorder} for
 * every successfully mutated user (and not for failed ones), with the same
 * old/new value shape as the single paths.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private AdminUserProjection adminUserProjection;
    @Mock
    private Clock clock;
    @Mock
    private UuidGenerator uuidGenerator;

    @InjectMocks
    private UserManagementServiceImpl service;

    /**
     * Bootstrap the MyBatis-Plus lambda cache so {@code LambdaUpdateWrapper}
     * column references in executeBan / executeUnban resolve without a Spring
     * context. Mirrors {@code DefaultForumReadProjectionSortTest}.
     */
    @BeforeAll
    static void bootstrapLambdaCache() {
        try {
            Class<?> assistantClass = Class.forName(
                    "com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant");
            MapperBuilderAssistant assistant =
                    (MapperBuilderAssistant) assistantClass
                            .getDeclaredConstructor(Configuration.class, String.class)
                            .newInstance(new Configuration(), "");
            TableInfoHelper.initTableInfo(assistant, User.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to register User with MyBatis-Plus TableInfoHelper", e);
        }
    }

    @AfterEach
    void clearAuditContext() {
        // AuditContext is a thread-local; keep tests isolated.
        com.ulticode.common.util.AuditContext.clear();
    }

    private static User user(String id, String username, boolean banned) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setIsBanned(banned);
        return u;
    }

    @Nested
    @DisplayName("bulkBan")
    class BulkBan {

        @Test
        @DisplayName("records a BAN_USER audit per banned user and skips failures")
        void recordsAuditPerBannedUser() {
            User alice = user("u1", "alice", false);
            User bob = user("u3", "bob", false);
            when(userMapper.selectById("u1")).thenReturn(alice);
            when(userMapper.selectById("u2")).thenReturn(null); // not found
            when(userMapper.selectById("u3")).thenReturn(bob);
            when(userMapper.update(isNull(), any())).thenReturn(1);

            List<UserManagementService.BanResult> results =
                    service.bulkBan(List.of("u1", "u2", "u3"), "spam");

            assertEquals(3, results.size());
            assertTrue(results.get(0).success(), "u1 banned");
            assertFalse(results.get(1).success(), "u2 not found -> failure");
            assertTrue(results.get(2).success(), "u3 banned");

            // Audit recorded exactly for the two successful bans, never for u2.
            verify(auditRecorder, times(2)).recordForUser(
                    eq(AuditVocabulary.BAN_USER),
                    eq(AuditVocabulary.ENTITY_USER),
                    anyString(), anyString(),
                    any(), any());
            verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.BAN_USER),
                    eq(AuditVocabulary.ENTITY_USER),
                    eq("u1"), eq("u1"),
                    eq(Map.of("isBanned", false, "bannedReason", "")),
                    eq(Map.of("isBanned", true, "bannedReason", "spam")));
        }
    }

    @Nested
    @DisplayName("bulkDelete")
    class BulkDelete {

        @Test
        @DisplayName("records a DELETE_USER audit with deleted:true new values (single-path shape)")
        void recordsAuditWithDeletedNewValues() {
            User alice = user("u1", "alice", false);
            when(userMapper.selectById("u1")).thenReturn(alice);
            when(userMapper.deleteById("u1")).thenReturn(1);

            List<UserManagementService.DeleteResult> results = service.bulkDelete(List.of("u1"));

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            // The single delete path records newValues = {deleted:true}; bulk
            // must match (previously it recorded null newValues).
            verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.DELETE_USER),
                    eq(AuditVocabulary.ENTITY_USER),
                    eq("u1"), eq("u1"),
                    eq(Map.of("username", "alice")),
                    eq(Map.of("deleted", true)));
        }

        @Test
        @DisplayName("does not record audit for a missing user")
        void noAuditForMissingUser() {
            when(userMapper.selectById("uX")).thenReturn(null);

            List<UserManagementService.DeleteResult> results = service.bulkDelete(List.of("uX"));

            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            verifyNoInteractions(auditRecorder);
        }
    }

    @Nested
    @DisplayName("bulkUnban")
    class BulkUnban {

        @Test
        @DisplayName("records an UNBAN_USER audit per unbanned user")
        void recordsAuditPerUnbannedUser() {
            User alice = user("u1", "alice", true);
            alice.setBannedReason("spam");
            when(userMapper.selectById("u1")).thenReturn(alice);
            when(userMapper.update(isNull(), any())).thenReturn(1);

            List<UserManagementService.BanResult> results = service.bulkUnban(List.of("u1"));

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            verify(auditRecorder).recordForUser(
                    eq(AuditVocabulary.UNBAN_USER),
                    eq(AuditVocabulary.ENTITY_USER),
                    eq("u1"), eq("u1"),
                    eq(Map.of("isBanned", true, "bannedReason", "spam")),
                    eq(Map.of("isBanned", false, "bannedReason", "")));
        }
    }
}
