package com.ulticode.auth.security.oauth;

import java.net.URI;

import org.springframework.web.util.UriComponentsBuilder;

/** Validates trusted provider authorization endpoints before URI construction. */
final class OAuthAuthorizationUrl {

    private OAuthAuthorizationUrl() {
    }

    static UriComponentsBuilder fromTrustedHttpUrl(String value) {
        URI endpoint = URI.create(value);
        String scheme = endpoint.getScheme();
        if (!endpoint.isAbsolute() || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("OAuth authorization URL must be an absolute HTTP(S) URL");
        }
        return UriComponentsBuilder.fromUriString(value);
    }
}
