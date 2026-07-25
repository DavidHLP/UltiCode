package com.ulticode.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProperties")
class JwtPropertiesTest {

    private JwtProperties jwtProperties;

    @Nested
    @DisplayName("validateSecret()")
    class ValidateSecret {

        @Test
        @DisplayName("rejects null secret with NullPointerException")
        void nullSecret_throws() {
            jwtProperties = new JwtProperties();
            // secret is null by default
            assertThatThrownBy(jwtProperties::validateSecret)
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("JWT secret must not be null");
        }

        @Test
        @DisplayName("rejects blank secret with IllegalStateException")
        void blankSecret_throws() {
            jwtProperties = new JwtProperties();
            jwtProperties.setSecret("   ");
            assertThatThrownBy(jwtProperties::validateSecret)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret must not be blank");
        }

        @Test
        @DisplayName("rejects a secret shorter than 32 characters")
        void shortSecret_throws() {
            jwtProperties = new JwtProperties();
            jwtProperties.setSecret("a".repeat(16));
            assertThatThrownBy(jwtProperties::validateSecret)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 32 characters");
        }

        @Test
        @DisplayName("accepts valid 64-char secret without exception")
        void validSecret_succeeds() {
            jwtProperties = new JwtProperties();
            jwtProperties.setSecret("a".repeat(64));
            assertThatCode(jwtProperties::validateSecret).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("default values")
    class DefaultValues {

        @BeforeEach
        void setUp() {
            jwtProperties = new JwtProperties();
        }

        @Test
        @DisplayName("default access token expiration is 900000ms (15 minutes)")
        void defaultAccessTokenExpiration() {
            assertThat(jwtProperties.getAccessTokenExpiration()).isEqualTo(900000L);
        }

        @Test
        @DisplayName("default refresh token expiration is 604800000ms (7 days)")
        void defaultRefreshTokenExpiration() {
            assertThat(jwtProperties.getRefreshTokenExpiration()).isEqualTo(604800000L);
        }
    }
}
