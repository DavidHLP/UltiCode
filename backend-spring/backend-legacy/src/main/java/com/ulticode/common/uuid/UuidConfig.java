package com.ulticode.common.uuid;

import org.springframework.context.annotation.Configuration;

/**
 * Holder for {@link UuidGenerator} wiring. Production uses
 * {@link ProdUuidGenerator}; tests inject {@link FixedUuidGenerator} via
 * {@code @MockBean} or {@code @Primary}.
 *
 * <p>No explicit {@code @Bean} method is needed — {@link ProdUuidGenerator}
 * is a {@code @Component} and is auto-discovered. This class exists for
 * parity with {@code ClockConfig} and for future test-only overrides.
 *
 * @author ulticode
 */
@Configuration
public class UuidConfig {
}
