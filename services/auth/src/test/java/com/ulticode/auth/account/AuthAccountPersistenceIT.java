package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.auth.account-store=mysql",
        "spring.datasource.url=jdbc:h2:mem:auth_persist_it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class AuthAccountPersistenceIT {

    @Autowired
    private AuthAccountMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.ulticode.auth.service.AuthenticationWorkflow authentication;
    @Autowired
    private com.ulticode.auth.service.AccountSessionService sessions;
    @Autowired
    private com.ulticode.auth.session.AuthSessionPort sessionIssuer;
    @Autowired
    private com.ulticode.auth.refreshtoken.service.RefreshTokenService refreshTokens;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS refresh_tokens");
        jdbcTemplate.execute("""
            CREATE TABLE refresh_tokens (
                id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL,
                token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at DATETIME(3) NOT NULL,
                created_at DATETIME(3), rotated_at DATETIME(3), is_revoked TINYINT(1) NOT NULL DEFAULT 0,
                family_id VARCHAR(40), replaced_by_token_id VARCHAR(40), previous_token_id VARCHAR(40)
            )
        """);
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
            CREATE TABLE users (
                id VARCHAR(40) PRIMARY KEY,
                username VARCHAR(120) NOT NULL UNIQUE,
                email VARCHAR(255),
                password VARCHAR(255),
                role VARCHAR(20) NOT NULL DEFAULT 'USER',
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                is_banned TINYINT(1) NOT NULL DEFAULT 0,
                banned_until DATETIME(3),
                last_login_at DATETIME(3),
                joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                authz_version BIGINT NOT NULL DEFAULT 0,
                is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                password_reset_token_hash VARCHAR(255),
                password_reset_expires_at DATETIME(3)
            )
        """);

        jdbcTemplate.execute("""
            INSERT INTO users (id, username, email, password, role, is_active, is_banned, authz_version)
            VALUES ('user-100', 'cas_user', 'cas@example.com', 'pwd_hash', 'USER', 1, 0, 5)
        """);
    }

    @Test
    void accountSwitchRoundTripPersistsRotationAndKeepsOtherAccountAfterLogout() {
        prepareAccountCredentials();
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        accept(jar, authentication.login("cas_user", "test-password"));
        accept(jar, sessions.add("second_user", "test-password", jar, false));
        assertThat(sessions.list(jar)).hasSize(2);
        assertThat(sessions.list(jar)).anyMatch(a -> a.id().equals("user-200") && a.current());
        String originalSavedToken = jar.get("saved_account_dXNlci0xMDA");
        accept(jar, sessions.switchTo("user-100", jar, false));
        assertThat(refreshTokens.findValidUserId(originalSavedToken)).isNull();
        assertThat(refreshTokens.findValidUserId(jar.get("refresh_token"))).isEqualTo("user-100");
        accept(jar, authentication.logout(jar.get("refresh_token")));
        assertThat(jar).doesNotContainKeys("access_token", "refresh_token", "csrf_token", "session_account");
        assertThat(sessions.list(jar)).extracting(com.ulticode.auth.service.AccountSessionService.Account::id)
                .containsExactly("user-200");
        accept(jar, sessionIssuer.csrfSession(null));
        assertThat(jar.get("csrf_token")).isNotBlank();
        accept(jar, sessions.switchTo("user-200", jar, false));
        assertThat(refreshTokens.findValidUserId(jar.get("refresh_token"))).isEqualTo("user-200");
    }

    @Test
    void replayedSavedCredentialCommitsRevocationOfItsRotationFamily() {
        prepareAccountCredentials();
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        accept(jar, authentication.login("cas_user", "test-password"));
        accept(jar, sessions.add("second_user", "test-password", jar, false));
        String stolen = jar.get("saved_account_dXNlci0xMDA");
        var attacker = refreshTokens.validateAndRotate(stolen);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sessions.switchTo("user-100", jar, false))
                .isInstanceOf(com.ulticode.auth.error.AuthBusinessException.class);
        assertThat(refreshTokens.findValidUserId(attacker.token())).isNull();
    }

    private void prepareAccountCredentials() {
        String password = passwordEncoder.encode("test-password");
        jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?", password, "user-100");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password, role) VALUES (?, ?, ?, ?, ?)",
                "user-200", "second_user", "second@example.test", password, "USER");
    }

    private static void accept(java.util.Map<String, String> jar, com.ulticode.auth.session.AuthSession session) {
        session.cookies().forEach(cookie -> {
            if (cookie.maxAgeSeconds() == 0) jar.remove(cookie.name());
            else jar.put(cookie.name(), cookie.value());
        });
    }

    @Test
    @DisplayName("findByUsername and findByIds return authz_version correctly")
    void findAccountReturnsVersion() {
        AuthAccountEntity entity = mapper.findByUsername("cas_user");
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo("user-100");
        assertThat(entity.getAuthzVersion()).isEqualTo(5L);

        List<AuthAccountEntity> batch = mapper.findByIds(Set.of("user-100"));
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).getUsername()).isEqualTo("cas_user");
    }

    @Test
    @DisplayName("updateAccountIfVersion executes single SQL statement CAS with single version bump")
    void updateAccountIfVersionAtomicCas() {
        // Expected version 5 matches -> update succeeds
        int updated = mapper.updateAccountIfVersion("user-100", false, true, "ADMIN", 5L);
        assertThat(updated).isEqualTo(1);

        AuthAccountEntity refreshed = mapper.findById("user-100");
        assertThat(refreshed.getActive()).isFalse();
        assertThat(refreshed.getBanned()).isTrue();
        assertThat(refreshed.getRole()).isEqualTo("ADMIN");
        assertThat(refreshed.getAuthzVersion()).isEqualTo(6L);

        // Stale expected version 5 -> update fails
        int staleUpdated = mapper.updateAccountIfVersion("user-100", true, false, "USER", 5L);
        assertThat(staleUpdated).isEqualTo(0);

        // State remains unchanged after stale CAS attempt
        AuthAccountEntity afterStale = mapper.findById("user-100");
        assertThat(afterStale.getAuthzVersion()).isEqualTo(6L);
    }

}
