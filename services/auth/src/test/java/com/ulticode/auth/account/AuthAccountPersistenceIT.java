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

    @BeforeEach
    void setUpSchema() {
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
