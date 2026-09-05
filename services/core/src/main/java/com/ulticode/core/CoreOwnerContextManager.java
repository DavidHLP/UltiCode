package com.ulticode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Starts the existing Owner Implementations in isolated child contexts. */
@Component
public class CoreOwnerContextManager {
    private static final Logger log = LoggerFactory.getLogger(CoreOwnerContextManager.class);

    public enum State {
        DISABLED,
        STARTING,
        READY,
        FAILED,
        STOPPED
    }

    private final CoreModuleRegistry registry;
    private final CoreOwnerClassLoaders ownerClassLoaders;
    private final org.springframework.core.env.Environment environment;
    private final Map<String, State> states = new java.util.LinkedHashMap<>();
    private final Map<String, org.springframework.context.ConfigurableApplicationContext> contexts =
            new java.util.LinkedHashMap<>();
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "core-owner-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private final long startupTimeoutMs;
    private final boolean enabled;
    private final AtomicBoolean stopping = new AtomicBoolean();

    /** Closing-duty claim published by the timeout path before any context. */
    private static final Object TIMEOUT_CLAIMED = new Object();

    public CoreOwnerContextManager(
            CoreModuleRegistry registry,
            org.springframework.core.env.Environment environment,
            @org.springframework.beans.factory.annotation.Value("${core.owner-contexts.enabled:true}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${core.owner-contexts.startup-timeout-ms:120000}") long startupTimeoutMs) {
        this.registry = registry;
        this.ownerClassLoaders = new CoreOwnerClassLoaders();
        this.environment = environment;
        this.enabled = enabled;
        this.startupTimeoutMs = Math.max(1_000L, startupTimeoutMs);
        for (CoreModuleDefinition module : registry.modules()) {
            states.put(module.name(), enabled ? State.STARTING : State.DISABLED);
        }
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startOwnerModules() {
        if (!enabled || stopping.get()) {
            return;
        }
        startupExecutor.submit(this::startAll);
    }

    public synchronized Map<String, State> states() {
        return Map.copyOf(states);
    }

    public synchronized boolean allReady() {
        return enabled && states.values().stream().allMatch(state -> state == State.READY);
    }
    public synchronized <T> T bean(String owner, Class<T> type) {
        if (states.get(owner) != State.READY || !contexts.containsKey(owner)) {
            throw new IllegalStateException("Core Owner Module is not ready: " + owner);
        }
        return contexts.get(owner).getBean(type);
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        stopOwnerModules();
    }

    private void startAll() {
        for (CoreModuleDefinition module : registry.modules()) {
            if (stopping.get()) {
                return;
            }
            try {
                org.springframework.context.ConfigurableApplicationContext context =
                        startWithTimeout(module);
                // Re-check under the same lock the stop path uses to snapshot
                // and clear, so a context can never be published after the
                // stop snapshot was taken (it would leak un-closed).
                synchronized (this) {
                    if (stopping.get()) {
                        context.close();
                        return;
                    }
                    contexts.put(module.name(), context);
                    states.put(module.name(), State.READY);
                }
            } catch (RuntimeException failure) {
                log.error("Core Owner Module startup failed for {}", module.name(), failure);
                synchronized (this) {
                    states.put(module.name(), State.FAILED);
                }
            }
        }
    }

    synchronized java.util.Map<String, org.springframework.context.ConfigurableApplicationContext>
            contextsSnapshot() {
        return java.util.Map.copyOf(contexts);
    }

    private org.springframework.context.ConfigurableApplicationContext startWithTimeout(
            CoreModuleDefinition module) {
        if (stopping.get()) {
            throw new IllegalStateException("Core Owner Module startup cancelled: " + module.name());
        }
        // Ownership box: the slot thread publishes each created context here
        // (null -> context) exactly once; the timeout path claims closing duty
        // (null -> TIMEOUT_CLAIMED) exactly once. A created context therefore
        // always ends up owned by exactly one of the startAll caller, the
        // timeout path, or the late-completing callable itself — never by
        // more than one, never by nobody.
        AtomicReference<Object> handoff = new AtomicReference<>();
        // One executor slot per child: a hung start can occupy its own daemon
        // thread, but it must not starve the remaining children's bounded
        // startup futures on a shared single-thread executor.
        ExecutorService slot = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "core-owner-context-startup-" + module.name());
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<org.springframework.context.ConfigurableApplicationContext> startup =
                    slot.submit(() -> {
                        org.springframework.context.ConfigurableApplicationContext context =
                                start(module);
                        if (handoff.compareAndSet(null, context)) {
                            // Ownership transferred to the caller via the
                            // returned future; startAll publishes or closes it.
                            return context;
                        }
                        // Timeout path already claimed closing duty before
                        // publication, so the caller will never receive this
                        // context: the callable is the sole closer.
                        context.close();
                        throw new IllegalStateException(
                                "Core Owner Module startup abandoned after timeout claim: "
                                        + module.name());
                    });
            try {
                return awaitStartup(startup, module);
            } catch (TimeoutException exception) {
                closeOrphanedContext(handoff, startup, module.name());
                throw new IllegalStateException(
                        "Core Owner Module startup timed out: " + module.name(), exception);
            } catch (InterruptedException exception) {
                closeOrphanedContext(handoff, startup, module.name());
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Core Owner Module startup interrupted: " + module.name(), exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(
                        "Core Owner Module startup failed: " + module.name(), cause);
            }
        } finally {
            slot.shutdownNow();
        }
    }

    /**
     * Timeout/interrupt handoff. Claims closing duty with one CAS. If the
     * callable already published the context (claim fails), the caller never
     * received it because get() threw, so the timeout path closes it. If the
     * claim succeeds, a late-completing callable observes the claim, closes
     * its own context, and discards it. {@code Future.cancel(true)} is only a
     * cancellation signal for the worker thread — it is never treated as
     * proof that a context was stopped; ownership is decided solely by this
     * CAS protocol.
     */
    private void closeOrphanedContext(
            AtomicReference<Object> handoff,
            Future<org.springframework.context.ConfigurableApplicationContext> startup,
            String module) {
        Object boxed = handoff.compareAndSet(null, TIMEOUT_CLAIMED) ? null : handoff.get();
        startup.cancel(true);
        if (boxed instanceof org.springframework.context.ConfigurableApplicationContext context) {
            context.close();
            log.warn("Core Owner Module startup context closed by timeout path: {}", module);
        }
    }

    org.springframework.context.ConfigurableApplicationContext awaitStartup(
            Future<org.springframework.context.ConfigurableApplicationContext> startup,
            CoreModuleDefinition module)
            throws InterruptedException, ExecutionException, TimeoutException {
        return startup.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
    }

    org.springframework.context.ConfigurableApplicationContext start(CoreModuleDefinition module) {
        String prefix = module.environmentPrefix();
        boolean admin = "admin".equals(module.name());
        boolean search = "search".equals(module.name());
        List<String> properties = new java.util.ArrayList<>(List.of(
                "spring.application.name=ulticode-core-" + module.name(),
                "spring.main.web-application-type=none",
                "spring.main.banner-mode=off",
                "spring.main.allow-bean-definition-overriding=false",
                "spring.flyway.enabled=false",
                "spring.data.redis.host=" + requiredProperty(
                        prefix + "_REDIS_HOST", "REDIS_HOST"),
                "spring.data.redis.port=" + property(
                        prefix + "_REDIS_PORT", property("REDIS_PORT", "6379")),
                "spring.data.redis.username=" + property(
                        prefix + "_REDIS_USERNAME", "ulticode-" + module.name()),
                "spring.data.redis.password=" + requiredProperty(
                        prefix + "_REDIS_PASSWORD", "REDIS_PASSWORD"),
                "spring.data.redis.database=" + property(
                        prefix + "_REDIS_DB", property("REDIS_DB", "0")),
                "spring.data.redis.ssl.enabled=" + property(
                        prefix + "_REDIS_SSL_ENABLED", "false"),
                "security.internal-delegation.private-key="
                        + (admin ? property("INTERNAL_DELEGATION_PRIVATE_KEY", "") : ""),
                "security.internal-delegation.public-key="
                        + (admin ? "" : property("INTERNAL_DELEGATION_PUBLIC_KEY", "")),
                "security.internal-delegation.key-id="
                        + property("INTERNAL_DELEGATION_KEY_ID", ""),
                "security.internal-delegation.bootstrap-private-key="
                        + (admin ? property("BOOTSTRAP_DELEGATION_PRIVATE_KEY", "") : ""),
                "security.internal-delegation.bootstrap-public-key="
                        + (admin ? "" : property("BOOTSTRAP_DELEGATION_PUBLIC_KEY", "")),
                "security.internal-delegation.bootstrap-key-id="
                        + property("BOOTSTRAP_DELEGATION_KEY_ID", ""),
                "security.internal-delegation.issuer="
                        + property("INTERNAL_DELEGATION_ISSUER", "backend-admin"),
                "security.internal-delegation.audience=backend-" + module.name(),
                "security.internal-delegation.ttl-seconds="
                        + property("INTERNAL_DELEGATION_TTL_SECONDS", "30"),
                "dubbo.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.port=-1",
                "dubbo.application.register-mode=none"
        ));
        if (!search) {
            properties.add("spring.datasource.url=" + requiredProperty(
                    "core.datasource." + module.name() + ".url", prefix + "_DB_URL"));
            properties.add("spring.datasource.username=" + requiredProperty(
                    "core.datasource." + module.name() + ".username",
                    prefix + "_DB_USER"));
            properties.add("spring.datasource.password=" + requiredProperty(
                    "core.datasource." + module.name() + ".password",
                    prefix + "_DB_PASSWORD"));
        }
        java.net.URLClassLoader ownerClassLoader =
                ownerClassLoaders.createOwnerClassLoader(module.ownerArtifactId());
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        current.setContextClassLoader(ownerClassLoader);
        try {
            return new SpringApplicationBuilder(module.bootConfiguration())
                    .web(WebApplicationType.NONE)
                    .properties(properties.toArray(String[]::new))
                    .run();
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    private String property(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null ? fallback : value;
    }

    private String requiredProperty(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException(
                "Core Owner Module startup requires property: " + String.join(" | ", keys));
    }
    private void stopOwnerModules() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        Future<?> shutdown = startupExecutor.submit(() -> {
            List<org.springframework.context.ConfigurableApplicationContext> closing;
            synchronized (this) {
                closing = List.copyOf(contexts.values());
                contexts.clear();
                states.replaceAll((name, state) -> state == State.DISABLED ? state : State.STOPPED);
            }
            for (int index = closing.size() - 1; index >= 0; index--) {
                closing.get(index).close();
            }
        });
        startupExecutor.shutdown();
        try {
            shutdown.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.error("Core Owner Module shutdown did not complete", exception);
            // Child startup slots are per-child executors that die in their
            // own finally; force-close any child that registered before the
            // queued cleanup was dropped.
            startupExecutor.shutdownNow();
            List<org.springframework.context.ConfigurableApplicationContext> remaining;
            synchronized (this) {
                remaining = List.copyOf(contexts.values());
                contexts.clear();
                states.replaceAll((name, state) -> state == State.DISABLED ? state : State.STOPPED);
            }
            for (int index = remaining.size() - 1; index >= 0; index--) {
                try {
                    remaining.get(index).close();
                } catch (RuntimeException closeFailure) {
                    log.warn("Core Owner Module forced context close failed", closeFailure);
                }
            }
        }
    }
}
