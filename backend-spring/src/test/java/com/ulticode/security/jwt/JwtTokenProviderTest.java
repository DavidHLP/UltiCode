package com.ulticode.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private static final String USER_ID = "user-123";
    private static final String USERNAME = "testuser";
    private static final String ROLE = "USER";
    private static final String TEST_SECRET = "a".repeat(64);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtProperties, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtProperties, "accessToken", new JwtProperties.AccessTokenConfig());
        ReflectionTestUtils.setField(jwtProperties, "refreshToken", new JwtProperties.RefreshTokenConfig());
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("generates valid JWT with correct claims")
        void generatesValidJwt_withCorrectClaims() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(USERNAME);
            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(ROLE);
        }

        @Test
        @DisplayName("validates own generated token")
        void selfGeneratedToken_validates() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("rejects token signed with different secret")
        void differentSecret_failsValidation() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            // Change secret
            ReflectionTestUtils.setField(jwtProperties, "secret", "b".repeat(64));

            assertThat(jwtTokenProvider.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("generates refresh token with userId claim")
        void generatesRefreshToken_withTypeClaim() {
            String token = jwtTokenProvider.generateRefreshToken(USER_ID);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getUserIdFromRefreshToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("rejects an access token as a refresh token")
        void accessToken_isNotRefreshToken() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.getUserIdFromRefreshToken(token)).isNull();
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("returns false for malformed token")
        void malformedToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("returns false for null token")
        void nullToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for empty token")
        void emptyToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("returns false for expired token")
        void expiredToken_returnsFalse() throws InterruptedException {
            // Set access token expiration to 1ms so the token expires immediately
            JwtProperties.AccessTokenConfig config = new JwtProperties.AccessTokenConfig();
            ReflectionTestUtils.setField(config, "expiration", 1L);
            ReflectionTestUtils.setField(jwtProperties, "accessToken", config);

            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            // Wait briefly for the token to expire
            Thread.sleep(10);

            assertThat(jwtTokenProvider.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("claim extraction")
    class ClaimExtraction {

        @Test
        @DisplayName("getUserIdFromToken returns correct userId")
        void getUserIdFromToken_returnsCorrectUserId() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("getUsernameFromToken returns correct username")
        void getUsernameFromToken_returnsCorrectUsername() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("getRoleFromToken returns correct role")
        void getRoleFromToken_returnsCorrectRole() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(ROLE);
        }

        @Test
        @DisplayName("claim extraction returns null for invalid token")
        void claimExtraction_returnsNullForInvalidToken() {
            assertThat(jwtTokenProvider.getUserIdFromToken("invalid")).isNull();
            assertThat(jwtTokenProvider.getUsernameFromToken("invalid")).isNull();
            assertThat(jwtTokenProvider.getRoleFromToken("invalid")).isNull();
        }
    }
}
