package com.ulticode.auth.security.jwt;

import com.ulticode.websecurity.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Auth's local verifier adapter into the shared HTTP authentication filter. */
@Configuration(proxyBeanMethods = false)
public class AuthJwtFilterConfiguration {

    @Bean("authJwtAuthenticationFilter")
    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthAccessTokenVerifier verifier) {
        return new JwtAuthenticationFilter(verifier);
    }
}
