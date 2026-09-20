package com.ulticode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;


/** Starts allowlisted Owner implementations in bounded child contexts. */
@Component
public class CoreOwnerContextManager implements ApplicationContextAware {
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
    private final Set<OwnerStartup> startupAttempts = ConcurrentHashMap.newKeySet();
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "core-owner-bootstrap");
        thread.setDaemon(true);
        return thread;
    });
    private final long startupTimeoutMs;
    private final boolean enabled;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean startupSubmitted = new AtomicBoolean();
    private final CompletableFuture<Void> startupCompletion = new CompletableFuture<>();
    private volatile ApplicationContext ownerContext;

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
            startupExecutor.submit(this::startAllAndComplete);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        ownerContext = applicationContext;
    }

    CompletionStage<Void> startupCompletion() {
        return startupCompletion;
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
    public void onContextClosed(ContextClosedEvent event) {
        ApplicationContext source = event.getApplicationContext();
        if (ownerContext != null && source != ownerContext) {
            return;
        }
        stopOwnerModules();
    }

    private void startAll() {
        for (CoreModuleDefinition module : registry.enabledModules()) {
            if (stopping.get()) {
                return;
            }
            OwnerStartup attempt = admitStartupAttempt(module.name());
            if (attempt == null) {
                return;
            }
            try {
                try {
                    attempt.startAndAwait(startup -> bootOwnerModule(module, startup));
                } finally {
                    // Removal only after startAndAwait's drain completed; a
                    // successful object is held on by the published-owner map.
                    synchronized (this) {
                        startupAttempts.remove(attempt);
                    }
                }
                // Re-check under the same lock the stop path uses to snapshot
                // and clear, so a startup cannot be published after the
                // stop snapshot was taken (it would leak un-closed resources).
                // The queued published-owner close runs on the same
                // single-threaded startup executor as this loop, so closing
                // here is always the sole owner's decision.
                OwnerStartup stray = null;
                // Read the attempt's own state before taking the manager lock:
                // the global lock must never nest above an attempt monitor.
                org.springframework.context.ConfigurableApplicationContext context =
                        attempt.context();
                synchronized (this) {
                    if (stopping.get()) {
                        stray = attempt;
                    } else {
                        contexts.put(module.name(), context);
                        ownerStartups.put(module.name(), attempt);
                        states.put(module.name(), State.READY);
                    }
                }
                if (stray != null) {
                    try {
                        stray.close();
                    } catch (RuntimeException closeFailure) {
                        log.error("Core Owner Module close failed during startup stop", closeFailure);
                    }
                    return;
                }
            } catch (RuntimeException failure) {
                if (stopping.get()) {
                    // The stop path holds this attempt in its snapshot and
                    // owns the module's terminal state; startAndAwait already
                    // released any resources the losing attempt carried.
                    return;
                }
                log.error("Core Owner Module startup failed for {}", module.name(), failure);
                synchronized (this) {
                    states.put(module.name(), State.FAILED);
                }
            }
        }
    }

    /**
     * Admission step: creates an attempt and registers it under the same global
     * lock that guards the stop snapshot and READY publication, so no new
     * attempt can slip in behind a stop snapshot. Returns null, without
     * registering, once stop is in progress. Package-private so a test can put
     * more than one attempt into the active set: the sequential production loop
     * above can never present that shape on its own.
     */
    OwnerStartup admitStartupAttempt(String moduleName) {
        OwnerStartup attempt = new OwnerStartup(moduleName, startupTimeoutMs);
        synchronized (this) {
            if (stopping.get()) {
                return null;
            }
            startupAttempts.add(attempt);
            return attempt;
        }
    }

    private void startAllAndComplete() {
        try {
            startAll();
            if (allReady()) {
                startupCompletion.complete(null);
            } else {
                rollbackPublishedOwnerModules();
                startupCompletion.completeExceptionally(
                        new IllegalStateException("Core Owner Module startup did not reach READY"));
            }
        } catch (RuntimeException | Error failure) {
            startupCompletion.completeExceptionally(failure);
            throw failure;
        }
    }
    private void rollbackPublishedOwnerModules() {
        closePublishedOwnerStartups(
                state -> state == State.READY,
                failure -> log.error("Core Owner Module rollback close failed", failure));
    }

    synchronized java.util.Map<String, org.springframework.context.ConfigurableApplicationContext>
            contextsSnapshot() {
        return java.util.Map.copyOf(contexts);
    }

    /**
     * The production boot adapter: creates the owner classloader, runs the
     * child context and installs both into the running attempt through its
     * internal setters. Failure cleanup and resource ownership stay with the
     * attempt; this method only propagates the boot failure.
     */
    void bootOwnerModule(CoreModuleDefinition module, OwnerStartup attempt) {
        String prefix = module.environmentPrefix();
        boolean admin = "admin".equals(module.name());
        boolean search = "search".equals(module.name());
        String redisUsername = property(
                prefix + "_REDIS_USERNAME", "ulticode-" + module.name());
        String redisPassword = requiredProperty(
                prefix + "_REDIS_PASSWORD", "REDIS_PASSWORD");
        List<String> properties = new java.util.ArrayList<>(List.of(
                "spring.application.name=ulticode-core-" + module.name(),
                "spring.main.web-application-type=none",
                "spring.main.banner-mode=off",
                "spring.main.lazy-initialization=" + property(
                        "spring.main.lazy-initialization", "false"),
                "ulticode.app.inbox.enabled=" + property(
                        "ulticode.app.inbox.enabled", admin ? "false" : "true"),
                "spring.main.allow-bean-definition-overriding=false",
                "spring.flyway.enabled=false",
                "app.storage.type=" + property(
                        "APP_STORAGE_TYPE", "s3"),
                "app.storage.s3.endpoint=" + requiredProperty(
                        "APP_STORAGE_S3_ENDPOINT"),
                "app.storage.s3.region=" + requiredProperty(
                        "APP_STORAGE_S3_REGION"),
                "app.storage.s3.bucket=" + requiredProperty(
                        "APP_STORAGE_S3_BUCKET"),
                "app.storage.s3.access-key=" + requiredProperty(
                        "APP_STORAGE_S3_ACCESS_KEY"),
                "app.storage.s3.secret-key=" + requiredProperty(
                        "APP_STORAGE_S3_SECRET_KEY"),
                "app.storage.s3.tls-enabled=" + requiredProperty(
                        "APP_STORAGE_S3_TLS_ENABLED"),
                "app.storage.s3.ca-certificate-path=" + property(
                        "APP_STORAGE_S3_CA_CERTIFICATE", ""),
                "app.storage.s3.connect-timeout-ms=" + property(
                        "APP_STORAGE_S3_CONNECT_TIMEOUT_MS", "10000"),
                "app.storage.s3.request-timeout-ms=" + property(
                        "APP_STORAGE_S3_REQUEST_TIMEOUT_MS", "30000"),
                "app.storage.s3.upload-timeout-ms=" + property(
                        "APP_STORAGE_S3_UPLOAD_TIMEOUT_MS", "1800000"),
                "app.storage.s3.max-concurrent-requests=" + property(
                        "APP_STORAGE_S3_MAX_CONCURRENT_REQUESTS", "16"),
                "app.storage.startup-probe.enabled=" + property(
                        "APP_STORAGE_STARTUP_PROBE_ENABLED", "true"),
                "app.storage.startup-probe.attempts=" + property(
                        "APP_STORAGE_STARTUP_PROBE_ATTEMPTS", "30"),
                "app.storage.startup-probe.delay-ms=" + property(
                        "APP_STORAGE_STARTUP_PROBE_DELAY_MS", "2000"),
                "spring.data.redis.host=" + requiredProperty(
                        prefix + "_REDIS_HOST", "REDIS_HOST"),
                "spring.data.redis.port=" + property(
                        prefix + "_REDIS_PORT", property("REDIS_PORT", "6379")),
                "spring.data.redis.username=" + redisUsername,
                "spring.data.redis.password=" + redisPassword,
                "REDIS_USERNAME=" + redisUsername,
                "REDIS_PASSWORD=" + redisPassword,
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
                CoreLocalContractAssembly.LOCAL_CONTRACTS_ENABLED_PROPERTY + "=" + admin,
                "dubbo.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.port=-1",
                "dubbo.application.register-mode=none"
        ));
        String autoConfigurationExcludes = property("spring.autoconfigure.exclude", "");
        if (!autoConfigurationExcludes.isBlank()) {
            properties.add("spring.autoconfigure.exclude=" + autoConfigurationExcludes);
        }
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
                            .initializers(child ->
                                    CoreLocalContractAssembly.register(child, module, this))
                            .properties(properties.toArray(String[]::new))
                            .run();
            attempt.setContext(context);
            CoreLocalContractAssembly.validate(context, module);
        } finally {
            current.setContextClassLoader(previous);
        }
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

    /**
     * The single startup attempt for one owner module: registration, boot,
     * cancellation, handoff, resource ownership and the per-attempt drain
     * budget all live here. The manager creates and registers the attempt
     * before any thread is submitted, so the attempt — never the boot
     * future — is the resource owner; the future only conveys boot completion
     * or failure. {@link #close()} claims the terminal state and releases the
     * installed context/classloader exactly once, and a late install after
     * that claim disposes the incoming resource itself. {@link #requestStop()}
     * is a pure cancellation signal and deliberately a distinct claim, so it
     * never consumes the right to close. The module is one-shot: after a
     * submit or a close there is no restart, re-submit or reuse, even once
     * the future/slot/boot references have been released after the drain.
     */
    static class OwnerStartup {
        private final String moduleName;
        private final long startupTimeoutMs;
        private boolean started;
        private boolean stopRequested;
        private boolean closed;
        private Consumer<OwnerStartup> boot;
        private ExecutorService slot;
        private Future<?> startupFuture;
        private java.net.URLClassLoader classLoader;
        private org.springframework.context.ConfigurableApplicationContext context;

        OwnerStartup(String moduleName, long startupTimeoutMs) {
            this.moduleName = moduleName;
            this.startupTimeoutMs = startupTimeoutMs;
        }

        /**
         * Runs {@code boot} on this attempt's own startup slot and waits for
         * completion within the startup budget. Every failed or cancelled
         * await closes the registered attempt before propagating, so a
         * completed-but-lost boot result can never orphan its resources; the
         * await failure stays the primary cause and a cleanup failure is only
         * attached as suppressed. A successful return additionally requires
         * that no stop claim landed and that the slot thread terminated within
         * the fresh drain budget.
         * The instance monitor serializes first submit against close; no lock
         * is ever held across the future await, the drain or a close.
         */
        void startAndAwait(Consumer<OwnerStartup> boot) {
            Future<?> startup;
            synchronized (this) {
                if (started) {
                    throw new IllegalStateException(
                            "Core Owner Module startup attempt already used: " + moduleName);
                }
                if (closed || stopRequested) {
                    throw new IllegalStateException(
                            "Core Owner Module startup cancelled: " + moduleName);
                }
                started = true;
                // One executor slot per child: a hung start can occupy its own
                // daemon thread, but it must not starve the remaining
                // children's bounded startup futures on a shared executor.
                slot = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(
                            runnable, "core-owner-context-startup-" + moduleName);
                    thread.setDaemon(true);
                    return thread;
                });
                this.boot = boot;
                try {
                    startup = slot.submit(() -> runBoot(boot));
                    startupFuture = startup;
                } catch (RuntimeException | Error failure) {
                    ExecutorService rejected = slot;
                    slot = null;
                    this.boot = null;
                    // Submission failure leaves no active resource and no
                    // running slot: the terminal claim here only prevents any
                    // later use of this one-shot attempt.
                    stopRequested = true;
                    closed = true;
                    rejected.shutdownNow();
                    throw failure;
                }
            }
            boolean delivered = false;
            boolean terminated;
            try {
                try {
                    awaitStartup(startup);
                } catch (TimeoutException timeout) {
                    throw closePreservingPrimary(new IllegalStateException(
                            "Core Owner Module startup timed out: " + moduleName, timeout));
                } catch (InterruptedException interrupted) {
                    IllegalStateException primary = new IllegalStateException(
                            "Core Owner Module startup interrupted: " + moduleName, interrupted);
                    closePreservingPrimary(primary);
                    Thread.currentThread().interrupt();
                    throw primary;
                } catch (CancellationException cancelled) {
                    throw closePreservingPrimary(new IllegalStateException(
                            "Core Owner Module startup cancelled: " + moduleName, cancelled));
                } catch (ExecutionException execution) {
                    Throwable cause = execution.getCause();
                    throw closePreservingPrimary(cause instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new IllegalStateException(
                                    "Core Owner Module startup failed: " + moduleName, cause));
                }
                // A completed boot is still not deliverable once a stop claim
                // landed: only the close protocol may own the resources now.
                boolean cancelledBeforeDelivery;
                synchronized (this) {
                    cancelledBeforeDelivery = closed || stopRequested;
                }
                if (cancelledBeforeDelivery) {
                    throw closePreservingPrimary(new IllegalStateException(
                            "Core Owner Module startup cancelled: " + moduleName));
                }
                delivered = true;
            } finally {
                // Fresh drain budget: the await already consumed its own.
                terminated = drainSlot();
                synchronized (this) {
                    startupFuture = null;
                    slot = null;
                    this.boot = null;
                }
            }
            if (delivered && !terminated) {
                // Honest limit: the bounded drain did not prove the worker
                // exited, so a possibly-still-creating boot must never be
                // published as READY on top of it.
                throw closePreservingPrimary(new IllegalStateException(
                        "Core Owner Module startup thread did not terminate: " + moduleName));
            }
        }

        /**
         * Claims the close for a failed await without letting a cleanup failure
         * replace the await failure: the timeout, interrupt or boot cause stays
         * primary and the cleanup failure is attached as suppressed. An
         * {@link Error} from the cleanup is never swallowed.
         */
        private RuntimeException closePreservingPrimary(RuntimeException primary) {
            try {
                close();
            } catch (RuntimeException cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
            return primary;
        }

        /** Await seam: tests override it to lose an already-completed result. */
        void awaitStartup(Future<?> startup)
                throws InterruptedException, ExecutionException, TimeoutException {
            startup.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Late-install rule for the classloader: claim the decision under the
         * monitor, then release the rejected resource outside it, so a slow
         * third-party close can never hold this attempt's lock.
         */
        void setClassLoader(java.net.URLClassLoader classLoader) {
            java.net.URLClassLoader rejected;
            synchronized (this) {
                if (!closed) {
                    this.classLoader = classLoader;
                    return;
                }
                rejected = classLoader;
            }
            closeOwnerClassLoader(rejected);
        }

        /** Late-install rule for the context: same claim-then-dispose split. */
        void setContext(
                org.springframework.context.ConfigurableApplicationContext context) {
            org.springframework.context.ConfigurableApplicationContext rejected;
            synchronized (this) {
                if (!closed) {
                    this.context = context;
                    return;
                }
                rejected = context;
            }
            closeContext(rejected);
        }

        /** The installed child context: null before install and after close. */
        synchronized org.springframework.context.ConfigurableApplicationContext context() {
            return context;
        }

        /** Observation seam: has the stop claim landed? */
        synchronized boolean stopRequested() {
            return stopRequested;
        }

        /**
         * Cancellation signal only: marks the stop claim, blocks later
         * submit and delivery, cancels the registered future and shuts the
         * slot down now. Never closes the context or classloader and never
         * waits for the drain.
         */
        void requestStop() {
            Future<?> startup;
            ExecutorService startupSlot;
            synchronized (this) {
                stopRequested = true;
                startup = startupFuture;
                startupSlot = slot;
            }
            if (startup != null) {
                startup.cancel(true);
            }
            if (startupSlot != null) {
                startupSlot.shutdownNow();
            }
        }

        /**
         * Idempotent resource-close claim: performs its own requestStop first
         * so a bare close stays complete. A losing concurrent claim returns
         * without waiting, and the worker's late install releases itself
         * through the closed flag.
         */
        void close() {
            requestStop();
            releaseInstalledResources();
        }

        private void runBoot(Consumer<OwnerStartup> pendingBoot) {
            try {
                pendingBoot.accept(this);
            } catch (RuntimeException | Error failure) {
                // The cancellation signal may still be pending on this worker
                // when the boot fails; clear it around the release so an
                // interrupt-sensitive context/classloader close cannot abort
                // and strand the resources, then put the flag back.
                boolean interrupted = Thread.interrupted();
                try {
                    releaseInstalledResources();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                throw failure;
            }
        }

        private void releaseInstalledResources() {
            org.springframework.context.ConfigurableApplicationContext contextToClose;
            java.net.URLClassLoader classLoaderToClose;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                contextToClose = context;
                classLoaderToClose = classLoader;
                context = null;
                classLoader = null;
            }
            // Claim and snapshot under the lock; every cancel, shutdown and
            // close below runs outside it so a close never waits on the lock.
            try {
                closeContext(contextToClose);
            } finally {
                closeOwnerClassLoader(classLoaderToClose);
            }
        }

        /**
         * Bounded drain with a fresh budget: the cancel signals were already
         * sent, so this only waits for the worker to observe them. A
         * non-terminating daemon thread is recorded and reported, never
         * killed and never pretended to be dead.
         */
        private boolean drainSlot() {
            ExecutorService startupSlot;
            synchronized (this) {
                startupSlot = slot;
            }
            if (startupSlot == null) {
                return true;
            }
            startupSlot.shutdownNow();
            boolean interrupted = false;
            long drainDeadlineNanos =
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMs);
            while (!startupSlot.isTerminated()) {
                long remaining = drainDeadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    log.error("Core Owner Module startup thread did not terminate: {}", moduleName);
                    return false;
                }
                try {
                    startupSlot.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException interruptedException) {
                    interrupted = true;
                    startupSlot.shutdownNow();
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        private static void closeContext(
                org.springframework.context.ConfigurableApplicationContext context) {
            if (context != null) {
                context.close();
            }
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

    /**
     * Pass one of the batch stop: snapshot the active attempts under the global
     * lock and deliver every cancellation signal, returning exactly the set
     * the later close pass must release. Never closes a resource.
     */
    private Set<OwnerStartup> requestStopActiveStartupAttempts() {
        Set<OwnerStartup> activeAttempts;
        synchronized (this) {
            activeAttempts = Set.copyOf(startupAttempts);
        }
        for (OwnerStartup attempt : activeAttempts) {
            try {
                attempt.requestStop();
            } catch (RuntimeException signalFailure) {
                log.error("Core Owner Module startup cancellation signal failed", signalFailure);
            }
        }
        return activeAttempts;
    }

    private void closeStartupAttempts(Set<OwnerStartup> activeAttempts) {
        for (OwnerStartup attempt : activeAttempts) {
            try {
                attempt.close();
            } catch (RuntimeException closeFailure) {
                log.error("Core Owner Module startup resource close failed", closeFailure);
            }
        }
    }

    /**
     * Published-owner exit protocol shared by the rollback and both stop
     * paths: claim the snapshot under the same lock startAll holds when it
     * publishes (so nothing slips in behind the snapshot), clear the context
     * and ownership maps, mark the claimed states, then close outside the
     * lock in reverse publication order with per-close failure logging so one
     * failing close cannot strand a later owner's resources.
     */
    private void closePublishedOwnerStartups(
            Predicate<State> becomesStopped,
            Consumer<RuntimeException> closeFailureLogger) {
        List<OwnerStartup> closing;
        synchronized (this) {
            closing = List.copyOf(ownerStartups.values());
            contexts.clear();
            ownerStartups.clear();
            states.replaceAll((name, state) ->
                    becomesStopped.test(state) ? State.STOPPED : state);
        }
        for (int index = closing.size() - 1; index >= 0; index--) {
            try {
                closing.get(index).close();
            } catch (RuntimeException closeFailure) {
                closeFailureLogger.accept(closeFailure);
            }
        }
    }

    private void stopOwnerModules() {
        synchronized (this) {
            if (!stopping.compareAndSet(false, true)) {
                return;
            }
        }
        // Two passes: every active attempt first receives its cancellation
        // signal, and only then do the potentially blocking closes run, so a
        // hung first close can never starve a later attempt of the signal.
        closeStartupAttempts(requestStopActiveStartupAttempts());
        Future<?> shutdown = startupExecutor.submit(
                () -> stopPublishedOwnerModules(failure ->
                        log.error("Core Owner Module close failed", failure)));
        startupExecutor.shutdown();
        try {
            shutdown.get(startupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            // Same rule as the per-attempt drain: clean up first, then restore
            // the caller's interrupt flag so the cleanup cannot be interrupted
            // away half done.
            forceStopAfterDroppedCleanup();
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException | CancellationException dropped) {
            forceStopAfterDroppedCleanup();
        }
    }

    /**
     * Published-owner exit used by both stop paths: same terminal-state
     * claim and reverse-order close, only the failure logging differs.
     */
    private void stopPublishedOwnerModules(Consumer<RuntimeException> closeFailureLogger) {
        closePublishedOwnerStartups(state -> state != State.DISABLED, closeFailureLogger);
    }

    /**
     * Cleanup for a queued published-owner close that never came back. Each
     * attempt drains its own slot inside startAndAwait, so this force-stops
     * any child still registered when the queued cleanup was dropped, drops
     * the executor and closes the published owners once more.
     */
    private void forceStopAfterDroppedCleanup() {
        closeStartupAttempts(requestStopActiveStartupAttempts());
        startupExecutor.shutdownNow();
        stopPublishedOwnerModules(failure ->
                log.warn("Core Owner Module forced module close failed", failure));
    }
}
