package com.ulticode.security.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private OAuthProvider github = new OAuthProvider();
    private OAuthProvider google = new OAuthProvider();

    @Data
    public static class OAuthProvider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String authorizeUrl;
        private String tokenUrl;
        private String userUrl;
        private String scopes;
    }
}
