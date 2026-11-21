package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import com.ulticode.auth.security.jwt.RsaKeyManager;
import com.ulticode.auth.session.AuthSessionPort;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private WebApplicationContext webContext;
    @Autowired
    private com.ulticode.auth.service.AuthenticationWorkflow authentication;
    @Autowired
    private com.ulticode.auth.service.AccountSessionService sessions;
    @MockitoSpyBean
    private AuthSessionPort sessionIssuer;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private com.ulticode.auth.refreshtoken.service.RefreshTokenService refreshTokens;
    @MockitoSpyBean
    private MyBatisAuthAccountAdapter accountAdapter;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JwtProperties jwtProperties;

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
    void adminOnlyAddDeniesOrdinaryAccountWithoutWritingSessionState() throws Exception {
        prepareAccountCredentials();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = 'user-100'");
        var mvc = authMvc();
        var login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"cas_user","password":"test-password"}
                            """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn().getResponse();
        assertThat(lastLoginAt("user-100")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);

        mvc.perform(post("/auth/accounts")
                        .cookie(login.getCookies())
                        .header("X-CSRF-Token", login.getCookie("csrf_token").getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"second_user","password":"test-password","adminOnly":true}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertAll(
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isEqualTo(1),
                () -> assertThat(lastLoginAt("user-200")).isNull()
        );
        assertThat(refreshTokens.findValidUserId(login.getCookie("refresh_token").getValue())).isEqualTo("user-100");
    }

    private LocalDateTime lastLoginAt(String accountId) {
        return jdbcTemplate.queryForObject("SELECT last_login_at FROM users WHERE id = ?", LocalDateTime.class, accountId);
    }

    @Test
    void accountSwitchRoundTripPersistsRotationAndKeepsOtherAccountAfterLogout() {
        prepareAccountCredentials();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = 'user-200'");
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        accept(jar, authentication.login("cas_user", "test-password"));
        accept(jar, sessions.add("second_user", "test-password", jar, true));
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
        accept(jar, sessions.switchTo("user-200", jar, true));
        assertThat(refreshTokens.findValidUserId(jar.get("refresh_token"))).isEqualTo("user-200");
    }

    @Test
    void holdsAccountRowLockWhileIssuingSessionForSavedAccount() {
        String credential = refreshTokens.createToken("user-100");
        doAnswer(invocation -> {
            assertAccountRowLocked();
            return invocation.callRealMethod();
        }).when(sessionIssuer).completeRefresh(any(AuthAccountRecord.class), anyString());

        var session = authentication.switchAccount("user-100", credential, false);

        assertThat(session.response().getUser().id()).isEqualTo("user-100");
        assertThat(refreshTokens.findValidUserId(credential)).isNull();
    }

    @Test
    void refreshHoldsAccountRowLockThroughSessionIssuanceAndSignsCurrentRole() throws Exception {
        String credential = refreshTokens.createToken("user-100");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", "user-100");
        doAnswer(invocation -> {
            assertAccountRowLocked();
            return invocation.callRealMethod();
        }).when(refreshTokens).validateAndRotate(eq(credential), any(Runnable.class));
        doAnswer(invocation -> {
            assertAccountRowLocked();
            return invocation.callRealMethod();
        }).when(sessionIssuer).completeRefresh(any(AuthAccountRecord.class), anyString());

        var response = authMvc().perform(refreshRequest(credential))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn().getResponse();
        assertThat(jwtTokenProvider.getRoleFromToken(response.getCookie("access_token").getValue()))
                .isEqualTo("ADMIN");
        assertThat(refreshTokens.findValidUserId(credential)).isNull();
        assertThat(refreshTokens.findValidUserId(response.getCookie("refresh_token").getValue()))
                .isEqualTo("user-100");
    }

    @Test
    void loginHoldsAccountRowLockBeforeFirstWriteAndThroughSessionIssuance() {
        prepareAccountCredentials();
        doAnswer(invocation -> {
            assertAccountRowLocked();
            return invocation.callRealMethod();
        }).when(accountAdapter).updateLastLoginAt("user-100");
        doAnswer(invocation -> {
            assertAccountRowLocked();
            return invocation.callRealMethod();
        }).when(sessionIssuer).completeLogin(any(AuthAccountRecord.class));

        var session = authentication.login("cas_user", "test-password");

        assertThat(session.response().getUser().id()).isEqualTo("user-100");
        assertThat(lastLoginAt("user-100")).isNotNull();
    }

    private void assertAccountRowLocked() {
        var otherTransaction = new TransactionTemplate(transactionManager);
        otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> otherTransaction.execute(status -> jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE id = ? FOR UPDATE NOWAIT", String.class, "user-100")))
                .isInstanceOf(QueryTimeoutException.class)
                .hasMessageContaining("Timeout trying to lock");
    }

    @ParameterizedTest
    @CsvSource({"false, false, false, 401, 10001", "true, true, false, 401, 10001",
            "true, false, true, 404, 10005"})
    void deniedRefreshPreservesRefreshRowsAndCookies(
            boolean active, boolean banned, boolean deleted, int httpStatus, int code) throws Exception {
        String credential = refreshTokens.createToken("user-100");
        jdbcTemplate.update("UPDATE users SET is_active = ?, is_banned = ?, is_deleted = ? WHERE id = ?",
                active, banned, deleted, "user-100");
        var before = refreshRows();

        authMvc().perform(refreshRequest(credential))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(refreshRows()).isEqualTo(before);
    }

    @Test
    void rejectedSessionIssuanceRollsBackCredentialRotation() throws Exception {
        String credential = refreshTokens.createToken("user-100");
        var before = refreshRows();
        doThrow(new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS))
                .when(sessionIssuer).completeRefresh(any(AuthAccountRecord.class), anyString());

        authMvc().perform(refreshRequest(credential))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(refreshRows()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replayedRefreshCommitsOnlyItsFamilyRevocation(boolean banned) throws Exception {
        String credential = refreshTokens.createToken("user-100");
        String family = jdbcTemplate.queryForObject("SELECT family_id FROM refresh_tokens", String.class);
        String successor = refreshTokens.createToken("user-100", family, family);
        String separateSession = refreshTokens.createToken("user-100");
        jdbcTemplate.update("UPDATE refresh_tokens SET is_revoked = 1 WHERE id = ?", family);
        jdbcTemplate.update("UPDATE users SET is_banned = ? WHERE id = ?", banned, "user-100");
        assertThat(refreshTokens.findValidUserId(successor)).isEqualTo("user-100");

        authMvc().perform(refreshRequest(credential))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked — possible token theft"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(refreshTokens.findValidUserId(successor)).isNull();
        assertThat(refreshTokens.findValidUserId(separateSession)).isEqualTo("user-100");
        assertThat(refreshRows()).hasSize(3);
    }

    @Test
    void refreshPreservesCredentialAndCsrfErrorsWithoutWritingCookiesOrRows() throws Exception {
        String credential = refreshTokens.createToken("user-100");
        var mvc = authMvc();
        var before = refreshRows();
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", credential)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        expectUnauthorizedRefresh(mvc, post("/auth/refresh"), 10006, "Refresh token is missing");
        expectUnauthorizedRefresh(mvc, refreshRequest("invalid"), 40100, "Invalid refresh token");
        expectUnauthorizedRefresh(mvc, refreshRequest(jwtTokenProvider.generateRefreshToken("user-100")),
                40100, "Invalid refresh token");

        var expired = new JwtProperties();
        expired.setSecret(jwtProperties.getSecret());
        expired.setIssuer(jwtProperties.getIssuer());
        expired.getRefreshToken().setExpiration(-60_000L);
        String expiredCredential = new JwtTokenProvider(expired, new RsaKeyManager()).generateRefreshToken("user-100");
        expectUnauthorizedRefresh(mvc, refreshRequest(expiredCredential), 10006, "Refresh token has expired");
        assertThat(refreshRows()).isEqualTo(before);

        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ?", LocalDateTime.of(2000, 1, 1, 0, 0));
        var expiredRows = refreshRows();
        expectUnauthorizedRefresh(mvc, refreshRequest(credential), 40100, "Refresh token has expired");
        assertThat(refreshRows()).isEqualTo(expiredRows);
    }

    private MockMvc authMvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder refreshRequest(String credential) {
        return post("/auth/refresh")
                .cookie(new Cookie("refresh_token", credential), new Cookie("csrf_token", "test-csrf"))
                .header("X-CSRF-Token", "test-csrf");
    }

    private static void expectUnauthorizedRefresh(MockMvc mvc, MockHttpServletRequestBuilder request,
            int code, String message) throws Exception {
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private List<Map<String, Object>> refreshRows() {
        return jdbcTemplate.queryForList("SELECT * FROM refresh_tokens ORDER BY id");
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, true"})
    void deniedSavedAccountSwitchPreservesRefreshRowsAndCookies(boolean active, boolean banned) throws Exception {
        prepareAccountCredentials();
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        accept(jar, authentication.login("cas_user", "test-password"));
        accept(jar, sessions.add("second_user", "test-password", jar, false));
        jdbcTemplate.update("UPDATE users SET is_active = ?, is_banned = ? WHERE id = ?", active, banned, "user-100");
        var before = refreshRows();
        assertThat(before).hasSize(2);
        String saved = jar.get("saved_account_dXNlci0xMDA");
        assertThat(refreshTokens.findValidUserId(saved)).isEqualTo("user-100");

        var mvc = authMvc();
        mvc.perform(post("/auth/accounts/switch")
                        .cookie(jar.entrySet().stream().map(entry -> new Cookie(entry.getKey(), entry.getValue()))
                                .toArray(Cookie[]::new))
                        .header("X-CSRF-Token", jar.get("csrf_token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"accountId":"user-100","adminOnly":false}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(refreshRows()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replayedSavedCredentialCommitsRevocationOfItsRotationFamily(boolean banned) {
        prepareAccountCredentials();
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        accept(jar, authentication.login("cas_user", "test-password"));
        accept(jar, sessions.add("second_user", "test-password", jar, false));
        String savedCredential = jar.get("saved_account_dXNlci0xMDA");
        java.util.Map<String, String> rotated = new java.util.LinkedHashMap<>();
        accept(rotated, authentication.refresh(savedCredential));
        if (banned) {
            jdbcTemplate.update("UPDATE users SET is_banned = 1 WHERE id = ?", "user-100");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sessions.switchTo("user-100", jar, false))
                .isInstanceOf(com.ulticode.auth.error.AuthBusinessException.class);
        assertThat(refreshTokens.findValidUserId(rotated.get("refresh_token"))).isNull();
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
