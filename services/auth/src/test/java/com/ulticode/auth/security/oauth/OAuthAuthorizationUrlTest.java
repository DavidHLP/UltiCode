package com.ulticode.auth.security.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OAuthAuthorizationUrlTest {

    @Test
    void preservesConfiguredHttpAuthorizationEndpointAndQuery() {
        String endpoint = "https://provider.example/oauth/authorize?prompt=consent";

        assertThat(OAuthAuthorizationUrl.fromTrustedHttpUrl(endpoint).build().toUriString())
                .isEqualTo(endpoint);
    }

    @Test
    void rejectsNonHttpProviderEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OAuthAuthorizationUrl.fromTrustedHttpUrl("ftp://provider.example/authorize"));
    }
}
