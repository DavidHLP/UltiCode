package com.ulticode.common.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring context bridge for Dubbo SPI filters.
 *
 * <p>Dubbo filters are instantiated by the Dubbo extension loader, not by
 * Spring, so constructor injection of Micrometer {@link Tracer} is not
 * available at creation time. This lightweight holder captures the
 * {@link ApplicationContext} once it is ready and exposes the tracer lazily.
 *
 * <p>Used by {@link DubboTraceFilter} to resolve the tracer on the first
 * invocation. If the Spring context is not available (e.g. in unit tests) the
 * filter falls back to the tracer instance supplied via its test constructor.
 */
@Component
public class TracerHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static Tracer getTracer() {
        return context == null ? null : context.getBeanProvider(Tracer.class).getIfAvailable();
    }
}
