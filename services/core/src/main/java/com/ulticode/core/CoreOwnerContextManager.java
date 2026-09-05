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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Starts allowlisted Owner implementations in bounded child contexts. */
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
    private final Map<String, OwnerStartup> ownerStartups =
            new java.util.LinkedHashMap<>();
    private final Set<StartupAttempt> startupAttempts = ConcurrentHashMap.newKeySet();
    private final Set<ExecutorService> startupSlots = ConcurrentHashMap.newKeySet();
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "core-owner-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private final long startupTimeoutMs;
    private final boolean enabled;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean startupSubmitted = new AtomicBoolean();

    /** Closing-duty claim published by the timeout path before any context. */
    private static final Object TIMEOUT_CLAIMED = new Object();

    public CoreOwnerContextManager(
            CoreModuleRegistry registry,
            org.springframework.core.env.Environment environment,
            @org.springframework.beans.factory.annotation.Value("${core.owner-contexts.enabled:false}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${core.owner-contexts.startup-timeout-ms:120000}") long startupTimeoutMs) {
        this.registry = registry;
        this.ownerClassLoaders = new CoreOwnerClassLoaders();
        this.environment = environment;
        this.enabled = enabled;
        this.startupTimeoutMs = Math.max(1_000L, startupTimeoutMs);
        for (CoreModuleDefinition module : registry.modules()) {
            states.put(module.name(), enabled && module.enabled() ? State.STARTING : State.DISABLED);
        }
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startOwnerModules() {
        synchronized (this) {
            if (!enabled || stopping.get() || !startupSubmitted.compareAndSet(false, true)) {
                return;
            }
            startupExecutor.submit(this::startAll);
        }
    }

    public synchronized Map<String, State> states() {
        return Map.copyOf(states);
    }

    public synchronized boolean allReady() {
        return enabled && registry.enabledModules().stream()
                .allMatch(module -> states.get(module.name()) == State.READY);
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
        for (CoreModuleDefinition module : registry.enabledModules()) {
            if (stopping.get()) {
                return;
            }
            try {
                OwnerStartup startup =
                        startWithTimeout(module);
                // Re-check under the same lock the stop path uses to snapshot
                // and clear, so a startup cannot be published after the
                // stop snapshot was taken (it would leak un-closed resources).
                synchronized (this) {
                    if (stopping.get()) {
                        try {
                            startup.close();
                        } catch (RuntimeException closeFailure) {
                            log.error("Core Owner Module close failed during startup stop", closeFailure);
                        }
                        return;
                    }
                    contexts.put(module.name(), startup.context());
                    ownerStartups.put(module.name(), startup);
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

    private OwnerStartup startWithTimeout(CoreModuleDefinition module) {
        // Ownership box: the slot thread publishes each created startup here
        // (null -> startup) exactly once; the timeout path claims closing duty
        // (null -> TIMEOUT_CLAIMED) exactly once. A created startup therefore
        // always ends up owned by exactly one of the startAll caller, the
        // timeout path, or the late-completing callable itself — never by
        // more than one, never by nobody.
        AtomicReference<Object> handoff = new AtomicReference<>();
        StartupAttempt attempt = new StartupAttempt();
        ExecutorService slot;
        Future<OwnerStartup> startup;
        synchronized (this) {
            if (stopping.get()) {
                throw new IllegalStateException(
                        "Core Owner Module startup cancelled: " + module.name());
            }
            startupAttempts.add(attempt);
            // One executor slot per child: a hung start can occupy its own
            // daemon thread, but it must not starve the remaining children's
            // bounded startup futures on a shared single-thread executor.
            slot = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable, "core-owner-context-startup-" + module.name());
                thread.setDaemon(true);
                return thread;
            });
            startupSlots.add(slot);
            try {
                startup = slot.submit(() -> {
                    OwnerStartup ownerStartup = start(module, attempt);
                    if (handoff.compareAndSet(null, ownerStartup)) {
                        // Ownership transferred to the caller via the
                        // returned future; startAll publishes or closes it.
                        return ownerStartup;
                    }
                    // Timeout path already claimed closing duty before
                    // publication, so the caller will never receive this
                    // startup: the callable is the sole closer.
                    ownerStartup.close();
                    throw new IllegalStateException(
                            "Core Owner Module startup abandoned after timeout claim: "
                                    + module.name());
                });
            } catch (RuntimeException | Error failure) {
                startupSlots.remove(slot);
                startupAttempts.remove(attempt);
                try {
                    attempt.close();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                slot.shutdownNow();
                throw failure;
            }
        }
        long startupDeadlineNanos =
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMs);
        try {
            try {
                return awaitStartup(startup, module);
            } catch (TimeoutException exception) {
                closeOrphanedStartup(handoff, startup, attempt, module.name());
                throw new IllegalStateException(
                        "Core Owner Module startup timed out: " + module.name(), exception);
            } catch (InterruptedException exception) {
                closeOrphanedStartup(handoff, startup, attempt, module.name());
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
            stopStartupSlot(slot, module.name(), startupDeadlineNanos);
            synchronized (this) {
                startupAttempts.remove(attempt);
                startupSlots.remove(slot);
            }
        }
    }

    private void stopStartupSlot(
            ExecutorService slot, String module, long startupDeadlineNanos) {
        slot.shutdownNow();
        boolean interrupted = false;
        while (!slot.isTerminated()) {
            long remaining = startupDeadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                log.error("Core Owner Module startup thread did not terminate: {}", module);
                break;
            }
            try {
                slot.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException interruptedException) {
                interrupted = true;
                slot.shutdownNow();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Timeout/interrupt handoff. Claims closing duty with one CAS. If the
     * callable already published the startup (claim fails), the caller never
     * received it because get() threw, so the timeout path closes it. If the
     * claim succeeds, a late-completing callable observes the claim, closes
     * its own startup, and discards it. {@code Future.cancel(true)} is only a
     * cancellation signal for the worker thread — it is never treated as
     * proof that a startup was stopped; ownership is decided solely by this
     * CAS protocol.
     */
    private void closeOrphanedStartup(
            AtomicReference<Object> handoff,
            Future<OwnerStartup> startup,
            StartupAttempt attempt,
            String module) {
        Object boxed = handoff.compareAndSet(null, TIMEOUT_CLAIMED) ? null : handoff.get();
        startup.cancel(true);
        if (boxed instanceof OwnerStartup ownerStartup) {
            ownerStartup.close();
            log.warn("Core Owner Module startup resources closed by timeout path: {}", module);
        } else {
            attempt.close();
        }
    }

    OwnerStartup awaitStartup(
            Future<OwnerStartup> startup,
            CoreModuleDefinition module)
            throws InterruptedException, ExecutionException, TimeoutException {
        return startup.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
    }

    OwnerStartup start(CoreModuleDefinition module) {
        return start(module, new StartupAttempt());
    }

    OwnerStartup start(CoreModuleDefinition module, StartupAttempt attempt) {
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
        attempt.setClassLoader(ownerClassLoader);
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        current.setContextClassLoader(ownerClassLoader);
        try {
            org.springframework.context.ConfigurableApplicationContext context =
                    new SpringApplicationBuilder(module.bootConfiguration())
                            .web(WebApplicationType.NONE)
                            .initializers(child -> registerChildContracts(child, module))
                            .properties(properties.toArray(String[]::new))
                            .run();
            attempt.setContext(context);
            return new OwnerStartup(attempt);
        } catch (RuntimeException | Error failure) {
            try {
                attempt.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    void registerChildContracts(
            org.springframework.context.ConfigurableApplicationContext child,
            CoreModuleDefinition module) {
        if (!"admin".equals(module.name())) {
            return;
        }
        child.getBeanFactory().registerSingleton("coreOwnerContextManager", this);
        child.getBeanFactory().registerSingleton(
                "coreLocalIdentityQueryAdapter", new CoreLocalIdentityQueryAdapter(this));
        child.getBeanFactory().registerSingleton(
                "coreLocalAuthorizationMutationAdapter",
                new CoreLocalAuthorizationMutationAdapter(this));
    }


    private static void closeOwnerClassLoader(java.net.URLClassLoader ownerClassLoader) {
        if (ownerClassLoader == null) {
            return;
        }
        try {
            ownerClassLoader.close();
        } catch (java.io.IOException closeFailure) {
            log.warn("Core Owner Module classloader close failed", closeFailure);
        }
    }

    static final class StartupAttempt {
        private final AtomicBoolean closed = new AtomicBoolean();
        private java.net.URLClassLoader classLoader;
        private org.springframework.context.ConfigurableApplicationContext context;

        StartupAttempt() {
        }

        StartupAttempt(
                org.springframework.context.ConfigurableApplicationContext context,
                java.net.URLClassLoader classLoader) {
            this.context = context;
            this.classLoader = classLoader;
        }

        synchronized void setClassLoader(java.net.URLClassLoader classLoader) {
            if (closed.get()) {
                closeOwnerClassLoader(classLoader);
            } else {
                this.classLoader = classLoader;
            }
        }

        synchronized void setContext(
                org.springframework.context.ConfigurableApplicationContext context) {
            if (closed.get()) {
                closeContext(context);
            } else {
                this.context = context;
            }
        }

        synchronized org.springframework.context.ConfigurableApplicationContext context() {
            return context;
        }

        void close() {
            org.springframework.context.ConfigurableApplicationContext contextToClose;
            java.net.URLClassLoader classLoaderToClose;
            synchronized (this) {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                contextToClose = context;
                classLoaderToClose = classLoader;
                context = null;
                classLoader = null;
            }
            try {
                closeContext(contextToClose);
            } finally {
                closeOwnerClassLoader(classLoaderToClose);
            }
        }

        private static void closeContext(
                org.springframework.context.ConfigurableApplicationContext context) {
            if (context != null) {
                context.close();
            }
        }
    }

    static final class OwnerStartup {
        private final StartupAttempt attempt;

        OwnerStartup(
                org.springframework.context.ConfigurableApplicationContext context,
                java.net.URLClassLoader classLoader) {
            this.attempt = new StartupAttempt(context, classLoader);
        }

        OwnerStartup(StartupAttempt attempt) {
            this.attempt = attempt;
        }

        org.springframework.context.ConfigurableApplicationContext context() {
            return attempt.context();
        }

        void close() {
            attempt.close();
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
    private void closeActiveStartupAttempts() {
        Set<StartupAttempt> activeAttempts;
        synchronized (this) {
            activeAttempts = Set.copyOf(startupAttempts);
        }
        for (StartupAttempt attempt : activeAttempts) {
            try {
                attempt.close();
            } catch (RuntimeException closeFailure) {
                log.error("Core Owner Module startup resource close failed", closeFailure);
            }
        }
    }

    private void stopOwnerModules() {
        Set<ExecutorService> activeSlots;
        synchronized (this) {
            if (!stopping.compareAndSet(false, true)) {
                return;
            }
            activeSlots = Set.copyOf(startupSlots);
            activeSlots.forEach(ExecutorService::shutdownNow);
        }
        closeActiveStartupAttempts();
        Future<?> shutdown = startupExecutor.submit(() -> {
            List<OwnerStartup> closing;
            synchronized (this) {
                closing = List.copyOf(ownerStartups.values());
                contexts.clear();
                ownerStartups.clear();
                states.replaceAll((name, state) -> state == State.DISABLED ? state : State.STOPPED);
            }
            for (int index = closing.size() - 1; index >= 0; index--) {
                try {
                    closing.get(index).close();
                } catch (RuntimeException closeFailure) {
                    log.error("Core Owner Module close failed", closeFailure);
                }
            }
        });
        startupExecutor.shutdown();
        try {
            shutdown.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            closeActiveStartupAttempts();
            // Child startup slots are per-child executors that die in their
            // own finally; force-close any child that registered before the
            // queued cleanup was dropped.
            startupExecutor.shutdownNow();
            List<OwnerStartup> remaining;
            synchronized (this) {
                remaining = List.copyOf(ownerStartups.values());
                contexts.clear();
                ownerStartups.clear();
                states.replaceAll((name, state) -> state == State.DISABLED ? state : State.STOPPED);
            }
            for (int index = remaining.size() - 1; index >= 0; index--) {
                try {
                    remaining.get(index).close();
                } catch (RuntimeException closeFailure) {
                    log.warn("Core Owner Module forced module close failed", closeFailure);
                }
            }
        }
    }
}
