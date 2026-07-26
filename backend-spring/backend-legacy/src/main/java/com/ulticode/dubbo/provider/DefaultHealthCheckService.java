package com.ulticode.dubbo.provider;

import org.apache.dubbo.config.annotation.DubboService;

/**
 * P1-INFRA-003: default in-process implementation of
 * {@link HealthCheckService}. Marked with {@code @DubboService} so
 * Dubbo's annotation processor and {@code ServiceAnnotationBeanPostProcessor}
 * pick it up during {@code dubbo.scan.base-packages} scanning and
 * export it on the configured Triple protocol.
 *
 * <p>The {@code group = "ulticode"} tag keeps the placeholder distinct
 * from any real provider groups in Phase 4 and later. The
 * {@code version = "1.0.0"} matches the interface-level version the
 * Migration Guide (§6.4) requires for all Contract modules.
 */
@DubboService(group = "ulticode", version = "1.0.0")
public class DefaultHealthCheckService implements HealthCheckService {

    @Override
    public String ping() {
        return "pong";
    }
}
