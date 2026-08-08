package com.ulticode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend-auth service entry point.
 *
 * <p>The shell owns authentication, OAuth, credential/session, authorization,
 * and JWKS capabilities and exposes their HTTP adapters plus Dubbo providers.
 * The health endpoint remains available for service-process readiness checks.</p>
 */
@SpringBootApplication
public class BackendAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAuthApplication.class, args);
    }
}
