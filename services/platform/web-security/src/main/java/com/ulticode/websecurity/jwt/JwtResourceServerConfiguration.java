package com.ulticode.websecurity.jwt;

import com.ulticode.common.security.JwtValidationPort;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/** Shared resource-server JWT/JWKS beans for App, Admin, and Notification. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "jwt.resource-server.enabled", havingValue = "true", matchIfMissing = true)
public class JwtResourceServerConfiguration {

    @Bean
    public JwksPublicKeyProvider jwksPublicKeyProvider(
            @Value("${jwt.rsa.enabled:false}") boolean rsaEnabled,
            @Value("${jwt.jwks-uri:http://localhost:9101/auth/jwks}") String jwksUri,
            @Value("${jwt.jwks-json:}") String staticJwks,
            @Value("${jwt.jwks.cache-ttl-seconds:900}") long cacheTtlSeconds,
            @Value("${jwt.jwks.retry-backoff-seconds:30}") long retryBackoffSeconds,
            @Value("${jwt.jwks.allowed-hosts:localhost,127.0.0.1,::1,backend-auth}") String allowedHosts,
            Environment environment) {
        return new JwksPublicKeyProvider(
                rsaEnabled,
                jwksUri,
                staticJwks,
                cacheTtlSeconds,
                retryBackoffSeconds,
                allowedHosts,
                environment,
                RestClient.builder().build());
    }

    @Bean
    public ResourceServerJwtVerifier resourceServerJwtVerifier(
            JwksPublicKeyProvider jwksProvider,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${jwt.expected-issuer:ulticode-auth}") String expectedIssuer,
            @Value("${jwt.expected-audience:ulticode-api}") String expectedAudience,
            @Value("${jwt.allowed-algorithms:HS256,RS256}") String algorithms,
            @Value("${jwt.clock-skew-seconds:30}") long clockSkewSeconds) {
        return new ResourceServerJwtVerifier(
                jwksProvider,
                jwtSecret,
                expectedIssuer,
                expectedAudience,
                parseAlgorithms(algorithms),
                Clock.systemUTC(),
                clockSkewSeconds);
    }

    @Bean("resourceServerJwtAuthenticationFilter")
    public JwtAuthenticationFilter jwtAuthenticationFilter(AccessTokenVerifier verifier) {
        return new JwtAuthenticationFilter(verifier);
    }

    @Bean
    public JwtValidationPort jwtValidationPort(AccessTokenVerifier verifier) {
        return new JwtValidationAdapter(verifier);
    }

    private static Set<String> parseAlgorithms(String value) {
        Set<String> algorithms = new HashSet<>();
        for (String algorithm : value.split(",")) {
            if (!algorithm.isBlank()) {
                algorithms.add(algorithm.trim());
            }
        }
        return Set.copyOf(algorithms);
    }
}
