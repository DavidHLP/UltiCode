package com.ulticode.core;

import com.ulticode.core.CoreOwnerContextManager.OwnerStartup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.env.MockEnvironment;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Deterministic regressions for the {@link CoreOwnerContextManager} ownership
 * handoff protocol. Every created startup must be owned by exactly one of the
 * startAll caller, the timeout path, or the late-completing callable — close
 * count per startup is 0 (never created) or 1, never more, a timed-out module
 * must never become READY, and no executor thread may outlive the manager.
 */
class CoreOwnerContextManagerLifecycleTest {

    /** Manager with gated startup creation and a pluggable await outcome. */
    static final class Harness extends CoreOwnerContextManager {
        enum AwaitMode { REAL, TIMEOUT_AFTER_DONE, INTERRUPT_AFTER_DONE }

        /** Held before startup creation (simulates a slow child boot). */
        final AtomicReference<CountDownLatch> createGate =
                new AtomicReference<>(new CountDownLatch(0));
        /** Held after creation, before the ownership handoff attempt. */
        final AtomicReference<CountDownLatch> returnGate =
                new AtomicReference<>(new CountDownLatch(0));
        final Supplier<OwnerStartup> factory;
        volatile AwaitMode awaitMode = AwaitMode.REAL;
        /** Set when a cancellation signal interrupts the worker mid-start. */
        volatile boolean cancelInterruptObserved;

        Harness(CoreModuleRegistry registry,
                long timeoutMs,
                Supplier<OwnerStartup> factory) {
            super(registry, new MockEnvironment(), true, timeoutMs);
            this.factory = factory;
        }

        @Override
        OwnerStartup start(CoreModuleDefinition module, StartupAttempt ignoredAttempt) {
            try {
                createGate.get().await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            OwnerStartup startup = factory.get();
            try {
                // Parked with startup created but not yet handed off:
                // the exact window the timeout path may cancel the future in.
                returnGate.get().await();
            } catch (InterruptedException interrupted) {
                // startup.cancel(true) or slot shutdownNow landed here — a
                // real cancellation signal observed while holding a startup.
                cancelInterruptObserved = true;
                Thread.currentThread().interrupt();
            }
            return startup;
        }

        @Override
        OwnerStartup awaitStartup(
                Future<OwnerStartup> startup,
                CoreModuleDefinition module)
                throws InterruptedException, ExecutionException, TimeoutException {
            if (awaitMode == AwaitMode.REAL) {
                return super.awaitStartup(startup, module);
            }
            // Deterministic lost-result race: the future already completed
            // with the published startup, yet the caller's get() reports a
            // timeout (or is interrupted) and never receives the startup.
            while (!startup.isDone()) {
                Thread.sleep(5);
            }
            if (awaitMode == AwaitMode.TIMEOUT_AFTER_DONE) {
                throw new TimeoutException("test-simulated lost-result race");
            }
            throw new InterruptedException("test-simulated caller interrupt");
        }
    }

    private final List<Harness> managers = new ArrayList<>();

    private Harness newHarness(String module, long timeoutMs,
            Supplier<OwnerStartup> factory) {
        Harness manager = new Harness(single(module), timeoutMs, factory);
        managers.add(manager);
        return manager;
    }

    @AfterEach
    void stopManagersAndAwaitThreadExit() {
        for (Harness manager : managers) {
            // Idempotent: the stopping CAS makes repeat calls no-ops. Failed
            // and stopped managers alike drain their executors here.
            manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        }
        awaitNoThreads("core-owner-bootstrap", "core-owner-context-startup-");
        managers.clear();
    }

    @Test
    void normalSuccessPublishesReadyAndStopClosesExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        Harness manager = newHarness("ok", 120_000L, ownerStartup(context, classLoader));

        manager.startOwnerModules();
        awaitState(manager, "ok", CoreOwnerContextManager.State.READY);
        assertThat(manager.contextsSnapshot()).containsOnlyKeys("ok");
        verify(context, never()).close();
        verify(classLoader, never()).close();

        manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        awaitState(manager, "ok", CoreOwnerContextManager.State.STOPPED);
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
    }

    @Test
    void repeatedApplicationReadyEventsSubmitOwnerStartupOnlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        AtomicInteger starts = new AtomicInteger();
        Harness manager = newHarness("once", 120_000L, () -> {
            starts.incrementAndGet();
            return ownerStartup(context, classLoader).get();
        });

        // Parent and child contexts can each publish ApplicationReadyEvent.
        manager.startOwnerModules();
        manager.startOwnerModules();

        awaitState(manager, "once", CoreOwnerContextManager.State.READY);
        assertThat(starts).hasValue(1);

        manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        awaitState(manager, "once", CoreOwnerContextManager.State.STOPPED);
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
    }

    @Test
    void stopContinuesClosingOtherOwnersAfterOneCloseFailure() throws Exception {
        ConfigurableApplicationContext normalContext = mock(ConfigurableApplicationContext.class);
        ConfigurableApplicationContext failingContext = mock(ConfigurableApplicationContext.class);
        doThrow(new IllegalStateException("close failed")).when(failingContext).close();
        URLClassLoader normalLoader = mock(URLClassLoader.class);
        URLClassLoader failingLoader = mock(URLClassLoader.class);
        AtomicInteger starts = new AtomicInteger();
        CoreModuleRegistry registry = new CoreModuleRegistry(List.of(
                new CoreModuleDefinition("one", "TEST", CoreApplication.class, null, "backend-one"),
                new CoreModuleDefinition("two", "TEST", CoreApplication.class, null, "backend-two")));
        Harness manager = new Harness(registry, 120_000L, () ->
                (starts.getAndIncrement() == 0
                        ? ownerStartup(normalContext, normalLoader)
                        : ownerStartup(failingContext, failingLoader)).get());
        managers.add(manager);

        manager.startOwnerModules();
        awaitState(manager, "one", CoreOwnerContextManager.State.READY);
        awaitState(manager, "two", CoreOwnerContextManager.State.READY);

        manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));

        awaitState(manager, "one", CoreOwnerContextManager.State.STOPPED);
        awaitState(manager, "two", CoreOwnerContextManager.State.STOPPED);
        verify(failingContext, times(1)).close();
        verify(failingLoader, times(1)).close();
        verify(normalContext, times(1)).close();
        verify(normalLoader, times(1)).close();
    }

    @Test
    void timeoutAfterContextCreatedClosesStrandedStartupExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        Harness manager = newHarness("race", 120_000L, ownerStartup(context, classLoader));
        manager.awaitMode = Harness.AwaitMode.TIMEOUT_AFTER_DONE;

        manager.startOwnerModules();
        awaitState(manager, "race", CoreOwnerContextManager.State.FAILED);
        verify(context, timeout(5_000)).close();
        verify(context, times(1)).close();
        verify(classLoader, timeout(5_000)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("race"))
                .isEqualTo(CoreOwnerContextManager.State.FAILED);
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void interruptedCallerHandoffStillClosesPublishedStartupExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        Harness manager = newHarness("intr", 120_000L, ownerStartup(context, classLoader));
        manager.awaitMode = Harness.AwaitMode.INTERRUPT_AFTER_DONE;

        manager.startOwnerModules();
        awaitState(manager, "intr", CoreOwnerContextManager.State.FAILED);
        verify(context, timeout(5_000)).close();
        verify(context, times(1)).close();
        verify(classLoader, timeout(5_000)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("intr"))
                .isEqualTo(CoreOwnerContextManager.State.FAILED);
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void createdStartupUnhandedWhenTimeoutCancelsIsClosedExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        CountDownLatch returnGate = new CountDownLatch(1);
        Harness manager = newHarness("unhanded", 150L, ownerStartup(context, classLoader));
        manager.returnGate.set(returnGate);

        manager.startOwnerModules();
        // start() created the startup but is parked before the handoff; the
        // timeout fires, claims closing duty, and cancel(true) interrupts the
        // worker that still holds the startup.
        awaitState(manager, "unhanded", CoreOwnerContextManager.State.FAILED);
        returnGate.countDown();
        verify(context, timeout(5_000)).close();
        verify(classLoader, timeout(5_000)).close();
        assertThat(manager.cancelInterruptObserved)
                .as("cancel(true) must interrupt the worker holding the startup")
                .isTrue();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("unhanded"))
                .isEqualTo(CoreOwnerContextManager.State.FAILED);
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void startupExceptionMarksFailedWithoutPublishing() throws Exception {
        Harness manager = newHarness("boom", 120_000L, () -> {
            throw new IllegalStateException("child boot failure");
        });

        manager.startOwnerModules();
        awaitState(manager, "boom", CoreOwnerContextManager.State.FAILED);
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void stopDuringStartupClosesPublishedStartupExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        CountDownLatch returnGate = new CountDownLatch(1);
        Harness manager = newHarness("stop", 120_000L, ownerStartup(context, classLoader));
        manager.returnGate.set(returnGate);

        manager.startOwnerModules();
        // start() holds the created startup inside get(); issue the stop from
        // another thread (onContextClosed blocks until its queued cleanup
        // drains behind the running startAll task).
        Thread stopper = new Thread(() -> manager.onContextClosed(
                new ContextClosedEvent(mock(ApplicationContext.class))), "test-stopper");
        stopper.start();
        // With stopping flagged, the released startup must be closed by
        // startAll's stopping branch exactly once — never published READY,
        // never closed again by the queued stop snapshot.
        returnGate.countDown();
        stopper.join(15_000);
        assertThat(stopper.isAlive()).as("stop must complete").isFalse();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("stop"))
                .isEqualTo(CoreOwnerContextManager.State.STOPPED);
    }

    private static Supplier<OwnerStartup> ownerStartup(
            ConfigurableApplicationContext context,
            URLClassLoader classLoader) {
        return () -> new OwnerStartup(context, classLoader);
    }

    private static CoreModuleRegistry single(String name) {
        return new CoreModuleRegistry(List.of(new CoreModuleDefinition(
                name, "TEST", CoreApplication.class, null, "backend-" + name)));
    }

    private static void awaitState(
            CoreOwnerContextManager manager, String module,
            CoreOwnerContextManager.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            CoreOwnerContextManager.State state = manager.states().get(module);
            if (state == expected) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Interrupted while awaiting state " + expected);
            }
        }
        fail("Module " + module + " never reached state " + expected + "; was "
                + manager.states().get(module));
    }

    private static void awaitNoThreads(String... prefixes) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> matchesAny(thread.getName(), prefixes));
            if (!alive) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Interrupted while awaiting thread exit");
            }
        }
        fail("Threads leaked with prefixes " + String.join(", ", prefixes));
    }

    private static boolean matchesAny(String name, String... prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
