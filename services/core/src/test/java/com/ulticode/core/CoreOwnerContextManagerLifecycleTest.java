package com.ulticode.core;

import com.ulticode.core.CoreOwnerContextManager.OwnerStartup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.env.MockEnvironment;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Deterministic regressions for the {@link CoreOwnerContextManager} single
 * attempt protocol. Every created startup is owned by exactly one
 * {@link OwnerStartup} object from before thread submission: the close claim
 * wins the admission race, cancellation always precedes blocking closes, a
 * timed-out module never becomes READY, and no executor thread may outlive
 * the test. Manager-level tests assert publication and rollback; attempt-level
 * tests assert the handoff races inside the attempt.
 */
class CoreOwnerContextManagerLifecycleTest {

    /** Manager whose controllable boot installs resources into the attempt. */
    static final class Harness extends CoreOwnerContextManager {
        /** Fired once the controllable boot has installed its resources. */
        volatile CountDownLatch startedSignal;
        /** Held after installation; lets a test park the boot mid-flight. */
        final AtomicReference<CountDownLatch> returnGate =
                new AtomicReference<>(new CountDownLatch(0));
        /** Set when a cancellation signal interrupts the worker mid-boot. */
        volatile boolean cancelInterruptObserved;
        private final BiConsumer<CoreModuleDefinition, OwnerStartup> bootAdapter;

        Harness(CoreModuleRegistry registry,
                long timeoutMs,
                BiConsumer<CoreModuleDefinition, OwnerStartup> bootAdapter) {
            super(registry, new MockEnvironment(), true, timeoutMs);
            this.bootAdapter = bootAdapter;
        }

        @Override
        void bootOwnerModule(CoreModuleDefinition module, OwnerStartup attempt) {
            bootAdapter.accept(module, attempt);
            CountDownLatch started = startedSignal;
            if (started != null) {
                started.countDown();
            }
            try {
                // Parked with resources installed but the boot future not
                // yet completed: the exact window the await timeout or a
                // stop cancellation may overtake delivery in.
                returnGate.get().await();
            } catch (InterruptedException interrupted) {
                // future.cancel(true) or slot shutdownNow landed here — a
                // real cancellation signal observed while holding resources.
                cancelInterruptObserved = true;
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Attempt whose await deterministically loses an already-completed boot
     * outcome, so the lost-result handoff race is reproducible without
     * timing luck.
     */
    private static final class LostResultStartup extends OwnerStartup {
        private final boolean interruptCaller;

        LostResultStartup(String moduleName, boolean interruptCaller) {
            super(moduleName, 120_000L);
            this.interruptCaller = interruptCaller;
        }

        @Override
        void awaitStartup(Future<?> startup)
                throws InterruptedException, ExecutionException, TimeoutException {
            while (!startup.isDone()) {
                Thread.sleep(5);
            }
            if (interruptCaller) {
                throw new InterruptedException("test-simulated caller interrupt");
            }
            throw new TimeoutException("test-simulated lost-result race");
        }
    }

    private final List<Harness> managers = new ArrayList<>();

    private Harness newHarness(String module, long timeoutMs,
            BiConsumer<CoreModuleDefinition, OwnerStartup> boot) {
        Harness manager = new Harness(single(module), timeoutMs, boot);
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
        awaitNoThreads("core-owner");
        managers.clear();
    }

    // ---- manager interface: publication and rollback ----

    @Test
    void normalSuccessPublishesReadyAndStopClosesExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        Harness manager = newHarness("ok", 120_000L, installed(context, classLoader));

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
        Harness manager = newHarness("once", 120_000L, (module, attempt) -> {
            starts.incrementAndGet();
            installResources(attempt, context, classLoader);
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
        Harness manager = new Harness(registry, 120_000L, (module, attempt) -> {
            if (starts.getAndIncrement() == 0) {
                installResources(attempt, normalContext, normalLoader);
            } else {
                installResources(attempt, failingContext, failingLoader);
            }
        });
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
    void startupTimeoutMarksFailedAndClosesInstalledStartup() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        CountDownLatch returnGate = new CountDownLatch(1);
        Harness manager = newHarness("race", 150L, installed(context, classLoader));
        manager.returnGate.set(returnGate);

        manager.startOwnerModules();
        // The boot installed its resources but never completed; the await
        // times out on its bounded budget and the losing attempt closes what
        // it registered.
        awaitState(manager, "race", CoreOwnerContextManager.State.FAILED);
        assertThat(manager.cancelInterruptObserved)
                .as("the timeout claim must interrupt the worker holding the startup")
                .isTrue();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("race"))
                .isEqualTo(CoreOwnerContextManager.State.FAILED);
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void startupExceptionMarksFailedWithoutPublishing() throws Exception {
        Harness manager = newHarness("boom", 120_000L, (module, attempt) -> {
            throw new IllegalStateException("child boot failure");
        });

        manager.startOwnerModules();
        awaitState(manager, "boom", CoreOwnerContextManager.State.FAILED);
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void partialStartupRollsBackReadySiblingWhenAnotherOwnerFails() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        AtomicInteger starts = new AtomicInteger();
        CoreModuleRegistry registry = new CoreModuleRegistry(List.of(
                new CoreModuleDefinition(
                        "first", "TEST", CoreApplication.class, null, "backend-first"),
                new CoreModuleDefinition(
                        "second", "TEST", CoreApplication.class, null, "backend-second")));
        Harness manager = new Harness(registry, 120_000L, (module, attempt) -> {
            if (starts.getAndIncrement() == 0) {
                installResources(attempt, context, classLoader);
            } else {
                throw new IllegalStateException("second owner failed");
            }
        });
        managers.add(manager);

        manager.startOwnerModules();

        awaitState(manager, "second", CoreOwnerContextManager.State.FAILED);
        awaitState(manager, "first", CoreOwnerContextManager.State.STOPPED);
        verify(context, timeout(5_000)).close();
        verify(classLoader, timeout(5_000)).close();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.allReady()).isFalse();
    }

    @Test
    void stopDuringStartupClosesActiveAttemptExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        CountDownLatch returnGate = new CountDownLatch(1);
        CountDownLatch startedSignal = new CountDownLatch(1);
        Harness manager = newHarness("stop", 120_000L, installed(context, classLoader));
        manager.returnGate.set(returnGate);
        manager.startedSignal = startedSignal;

        manager.startOwnerModules();
        // The boot holds installed resources inside the await; issue the stop
        // from another thread (onContextClosed blocks until its queued
        // cleanup runs behind the startAll task). Waiting for the started
        // signal keeps the stop strictly after the attempt registration —
        // otherwise a losing race never creates the resources and "closes
        // exactly once" would mean "closes never".
        assertThat(startedSignal.await(15, TimeUnit.SECONDS))
                .as("worker reached the in-boot window")
                .isTrue();
        Thread stopper = new Thread(() -> manager.onContextClosed(
                new ContextClosedEvent(mock(ApplicationContext.class))), "test-stopper");
        stopper.start();
        // The stop passes cancel the registered attempt before closing it,
        // so the startAll waiter returns, refuses publication and never
        // marks FAILED; the attempt's resources go away exactly once. The
        // gate stays unreleased: the cancel signal alone ends the boot.
        stopper.join(15_000);
        assertThat(stopper.isAlive()).as("stop must complete").isFalse();
        assertThat(manager.cancelInterruptObserved)
                .as("stop must deliver its cancellation signal to the worker")
                .isTrue();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(manager.contextsSnapshot()).isEmpty();
        assertThat(manager.states().get("stop"))
                .isEqualTo(CoreOwnerContextManager.State.STOPPED);
    }

    @Test
    void managerBatchStopSignalsEveryActiveAttemptBeforeItsFirstBlockingClose() throws Exception {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseCloses = new CountDownLatch(1);
        ConfigurableApplicationContext firstContext = blockingClose(closeEntered, releaseCloses);
        ConfigurableApplicationContext secondContext = blockingClose(closeEntered, releaseCloses);
        URLClassLoader firstLoader = mock(URLClassLoader.class);
        URLClassLoader secondLoader = mock(URLClassLoader.class);
        // The production loop starts one module at a time, so several attempts
        // active at once only exist through the admission seam.
        Harness manager = newHarness("manager-stop", 120_000L, (module, attempt) -> {
            throw new AssertionError("the manager stop path must own these attempts");
        });
        OwnerStartup first = manager.admitStartupAttempt("manager-batch-one");
        OwnerStartup second = manager.admitStartupAttempt("manager-batch-two");
        CountDownLatch firstDelivered = new CountDownLatch(1);
        CountDownLatch secondDelivered = new CountDownLatch(1);
        ExecutorService awaiters = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "core-owner-manager-stop-await");
            thread.setDaemon(true);
            return thread;
        });
        awaiters.submit(() -> {
            try {
                first.startAndAwait(attempt -> installResources(attempt, firstContext, firstLoader));
            } catch (Throwable unexpected) {
                // A delivered boot has nothing left to fail on.
            }
            firstDelivered.countDown();
        });
        awaiters.submit(() -> {
            try {
                second.startAndAwait(
                        attempt -> installResources(attempt, secondContext, secondLoader));
            } catch (Throwable unexpected) {
                // Same.
            }
            secondDelivered.countDown();
        });
        assertThat(firstDelivered.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(secondDelivered.await(15, TimeUnit.SECONDS)).isTrue();

        // Stop through the manager API: whichever attempt it closes first is
        // parked on the gate, so whatever is observable now was claimed in the
        // cancellation pass rather than by a close that ran earlier.
        Thread stopper = new Thread(() -> manager.onContextClosed(
                new ContextClosedEvent(mock(ApplicationContext.class))),
                "core-owner-manager-stopper");
        stopper.start();
        assertThat(closeEntered.await(15, TimeUnit.SECONDS))
                .as("the manager's close pass is blocked on the first attempt")
                .isTrue();
        assertThat(first.stopRequested())
                .as("pass one must have reached every active attempt")
                .isTrue();
        assertThat(second.stopRequested())
                .as("pass one must have reached every active attempt")
                .isTrue();

        releaseCloses.countDown();
        stopper.join(15_000);
        assertThat(stopper.isAlive()).as("stop must complete once the gate releases").isFalse();
        verify(firstContext, times(1)).close();
        verify(firstLoader, times(1)).close();
        verify(secondContext, times(1)).close();
        verify(secondLoader, times(1)).close();
        awaiters.shutdown();
        awaitNoThreads("core-owner-manager-stop", "core-owner-context-startup-manager-batch");
    }

    @Test
    void publishedOwnersCloseInReversePublicationOrder() throws Exception {
        ConfigurableApplicationContext earlierContext = mock(ConfigurableApplicationContext.class);
        URLClassLoader earlierLoader = mock(URLClassLoader.class);
        ConfigurableApplicationContext laterContext = mock(ConfigurableApplicationContext.class);
        URLClassLoader laterLoader = mock(URLClassLoader.class);
        AtomicInteger starts = new AtomicInteger();
        CoreModuleRegistry registry = new CoreModuleRegistry(List.of(
                new CoreModuleDefinition(
                        "earlier", "TEST", CoreApplication.class, null, "backend-earlier"),
                new CoreModuleDefinition(
                        "later", "TEST", CoreApplication.class, null, "backend-later")));
        Harness manager = new Harness(registry, 120_000L, (module, attempt) -> {
            if (starts.getAndIncrement() == 0) {
                installResources(attempt, earlierContext, earlierLoader);
            } else {
                installResources(attempt, laterContext, laterLoader);
            }
        });
        managers.add(manager);
        manager.startOwnerModules();
        awaitState(manager, "earlier", CoreOwnerContextManager.State.READY);
        awaitState(manager, "later", CoreOwnerContextManager.State.READY);

        // The stop returns only after its queued cleanup ran, so the recorded
        // order below is the published-owner exit order.
        manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        awaitState(manager, "later", CoreOwnerContextManager.State.STOPPED);

        InOrder reverseOrder = inOrder(
                earlierContext, earlierLoader, laterContext, laterLoader);
        reverseOrder.verify(laterContext).close();
        reverseOrder.verify(laterLoader).close();
        reverseOrder.verify(earlierContext).close();
        reverseOrder.verify(earlierLoader).close();
        reverseOrder.verifyNoMoreInteractions();
    }

    @Test
    void rollbackAndManagerStopMarkDistinctSourcesAndNeverCloseTwice() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        AtomicInteger starts = new AtomicInteger();
        CoreModuleRegistry registry = new CoreModuleRegistry(List.of(
                new CoreModuleDefinition(
                        "rolled-back", "TEST", CoreApplication.class, null, "backend-rolled-back"),
                new CoreModuleDefinition(
                        "failed", "TEST", CoreApplication.class, null, "backend-failed")));
        Harness manager = new Harness(registry, 120_000L, (module, attempt) -> {
            if (starts.getAndIncrement() == 0) {
                installResources(attempt, context, classLoader);
            } else {
                throw new IllegalStateException("owner boot failed");
            }
        });
        managers.add(manager);

        manager.startOwnerModules();
        // Two different terminal sources from one startup: the owner the
        // rollback released is STOPPED, the owner whose boot failed is FAILED.
        awaitState(manager, "rolled-back", CoreOwnerContextManager.State.STOPPED);
        awaitState(manager, "failed", CoreOwnerContextManager.State.FAILED);
        // The state is claimed before the close runs, so await the release.
        verify(context, timeout(5_000)).close();
        verify(classLoader, timeout(5_000)).close();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();

        // A later manager stop must not re-close what the rollback already
        // released, and must keep the rolled-back owner's source state.
        manager.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        assertThat(manager.states()).containsEntry("rolled-back",
                CoreOwnerContextManager.State.STOPPED);
        assertThat(manager.contextsSnapshot()).isEmpty();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
    }

    // ---- attempt internals: handoff, claims, cancellation and drain ----

    @Test
    void failedAwaitCloseKeepsTheAwaitFailurePrimary() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        doThrow(new IllegalStateException("close failed")).when(context).close();
        URLClassLoader classLoader = mock(URLClassLoader.class);
        LostResultStartup startup = new LostResultStartup("primary", false);

        // The gated await lost an already-completed boot, and the close it
        // then ran throws: the timeout must survive as the primary cause.
        assertThatThrownBy(() ->
                startup.startAndAwait(attempt -> installResources(attempt, context, classLoader)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out")
                .satisfies(primary -> assertThat(primary.getSuppressed()).hasSize(1))
                .satisfies(primary -> assertThat(primary.getSuppressed()[0])
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("close failed"));
        verify(context, times(1)).close();
        // The suppressed cleanup failure did not strand the classloader.
        verify(classLoader, times(1)).close();
    }

    @Test
    void lateInstallReleasesResourcesWithoutHoldingTheAttemptMonitor() throws Exception {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        ConfigurableApplicationContext slowContext = blockingClose(closeEntered, releaseClose);
        OwnerStartup startup = new OwnerStartup("late-monitor", 120_000L);
        startup.close();

        Thread installer = new Thread(
                () -> startup.setContext(slowContext), "core-owner-late-installer");
        installer.setDaemon(true);
        installer.start();
        assertThat(closeEntered.await(15, TimeUnit.SECONDS))
                .as("the late install released its own context")
                .isTrue();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        CountDownLatch observerDone = new CountDownLatch(1);
        Thread observer = new Thread(() -> {
            try {
                startup.stopRequested();
            } catch (Throwable failure) {
                observed.set(failure);
            }
            observerDone.countDown();
        }, "core-owner-monitor-observer");
        observer.setDaemon(true);
        observer.start();
        // A third party's slow close must not stall the attempt monitor: the
        // manager's stop path and the other setters still get in.
        assertThat(observerDone.await(15, TimeUnit.SECONDS))
                .as("the attempt monitor is free while the late close runs")
                .isTrue();
        assertThat(observed.get()).isNull();
        releaseClose.countDown();
        installer.join(15_000);
        assertThat(installer.isAlive()).isFalse();
        verify(slowContext, times(1)).close();
    }

    @Test
    void successfulAttemptDeliversContextAndDuplicateCloseReleasesOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        OwnerStartup startup = new OwnerStartup("ok", 120_000L);

        startup.startAndAwait(attempt -> installResources(attempt, context, classLoader));

        assertThat(startup.context()).isSameAs(context);
        verify(context, never()).close();
        verify(classLoader, never()).close();
        startup.close();
        startup.close();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(startup.context()).isNull();
        // One-shot: a started attempt can never be submitted again.
        assertThatThrownBy(() -> startup.startAndAwait(attempt -> {
            throw new AssertionError("boot must not run after close");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("already used");
    }

    @Test
    void timeoutAfterBootCompletedClosesLostStartupExactlyOnce() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        LostResultStartup startup = new LostResultStartup("lost-timeout", false);

        assertThatThrownBy(() ->
                startup.startAndAwait(attempt -> installResources(attempt, context, classLoader)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
        assertThat(startup.context()).isNull();
    }

    @Test
    void interruptedAwaitClosesLostStartupAndRestoresCallerInterrupt() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        LostResultStartup startup = new LostResultStartup("lost-interrupt", true);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptFlagRestored = new AtomicBoolean();
        CountDownLatch callerDone = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            try {
                startup.startAndAwait(attempt -> installResources(attempt, context, classLoader));
            } catch (Throwable failure) {
                thrown.set(failure);
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
            }
            callerDone.countDown();
        }, "lost-interrupt-caller");
        caller.start();

        assertThat(callerDone.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrupted");
        assertThat(interruptFlagRestored)
                .as("the interrupt flag must be restored after cancel and cleanup")
                .isTrue();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
    }

    @Test
    void lateInstallAfterTimeoutClaimReleasesImmediately() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        URLClassLoader classLoader = mock(URLClassLoader.class);
        AtomicBoolean installedLate = new AtomicBoolean();
        OwnerStartup startup = new OwnerStartup("late", 1_000L);

        assertThatThrownBy(() -> startup.startAndAwait(attempt -> {
            try {
                new CountDownLatch(1).await(); // parked until the cancel signal
            } catch (InterruptedException expectedWake) {
                Thread.currentThread().interrupt();
            }
            // The timeout path claimed close before these installs landed:
            // the late-install rule must release each incoming resource.
            attempt.setClassLoader(classLoader);
            attempt.setContext(context);
            installedLate.set(true);
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("timed out");

        assertThat(installedLate)
                .as("the bounded drain proved the worker installed after the claim")
                .isTrue();
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
    }

    @Test
    void closeOrRequestStopBeforeSubmitStartsNoThreadAndRefusesBoot() throws Exception {
        AtomicBoolean bootRan = new AtomicBoolean();
        OwnerStartup closedFirst = new OwnerStartup("admit", 120_000L);
        closedFirst.close();
        assertThatThrownBy(() -> closedFirst.startAndAwait(attempt -> bootRan.set(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");

        OwnerStartup stoppedFirst = new OwnerStartup("admit2", 120_000L);
        stoppedFirst.requestStop();
        assertThatThrownBy(() -> stoppedFirst.startAndAwait(attempt -> bootRan.set(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
        assertThat(bootRan).isFalse();
        // The stop claim must not consume the close claim: a later close is
        // still a complete (here empty) release.
        stoppedFirst.close();
        awaitNoThreads("core-owner-context-startup-admit");
    }

    @Test
    void bootFailurePreservesPrimaryCauseAndSuppressesCleanupFailure() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        doThrow(new IllegalStateException("context close failed")).when(context).close();
        URLClassLoader classLoader = mock(URLClassLoader.class);
        IllegalStateException primary = new IllegalStateException("child boot failure");
        OwnerStartup startup = new OwnerStartup("boot-boom", 120_000L);

        assertThatThrownBy(() -> startup.startAndAwait(attempt -> {
            attempt.setClassLoader(classLoader);
            attempt.setContext(context);
            throw primary;
        })).isSameAs(primary);

        Throwable[] suppressed = primary.getSuppressed();
        assertThat(suppressed).hasSize(1);
        assertThat(suppressed[0]).isInstanceOf(IllegalStateException.class)
                .hasMessage("context close failed");
        verify(context, times(1)).close();
        // The context close threw, yet the classloader release still ran.
        verify(classLoader, times(1)).close();
        assertThat(startup.context()).isNull();
    }

    @Test
    void closeStillReleasesClassLoaderWhenContextCloseThrows() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        doThrow(new IllegalStateException("close failed")).when(context).close();
        URLClassLoader classLoader = mock(URLClassLoader.class);
        OwnerStartup startup = new OwnerStartup("close-boom", 120_000L);
        startup.startAndAwait(attempt -> installResources(attempt, context, classLoader));

        assertThatThrownBy(startup::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("close failed");
        verify(context, times(1)).close();
        verify(classLoader, times(1)).close();
    }

    @Test
    void everyActiveAttemptIsCancelledBeforeTheFirstBlockingClose() throws Exception {
        ConfigurableApplicationContext firstContext = mock(ConfigurableApplicationContext.class);
        URLClassLoader firstLoader = mock(URLClassLoader.class);
        ConfigurableApplicationContext secondContext = mock(ConfigurableApplicationContext.class);
        URLClassLoader secondLoader = mock(URLClassLoader.class);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch firstCloseBlocked = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstCloseBlocked.countDown();
            gate.await();
            return null;
        }).when(firstContext).close();
        OwnerStartup first = new OwnerStartup("batch-one", 120_000L);
        OwnerStartup second = new OwnerStartup("batch-two", 120_000L);
        AtomicBoolean firstWorkerCancelled = new AtomicBoolean();
        AtomicBoolean secondWorkerCancelled = new AtomicBoolean();
        CountDownLatch firstInstalled = new CountDownLatch(1);
        CountDownLatch secondInstalled = new CountDownLatch(1);
        CountDownLatch firstWaiterDone = new CountDownLatch(1);
        CountDownLatch secondWaiterDone = new CountDownLatch(1);
        ExecutorService awaiters = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "core-owner-batch-await");
            thread.setDaemon(true);
            return thread;
        });
        awaiters.submit(() -> {
            try {
                first.startAndAwait(attempt -> {
                    installResources(attempt, firstContext, firstLoader);
                    firstInstalled.countDown();
                    awaitCancellationSignal(firstWorkerCancelled);
                });
            } catch (Throwable expected) {
                // The await losing to the cancellation signal is the outcome.
            }
            firstWaiterDone.countDown();
        });
        awaiters.submit(() -> {
            try {
                second.startAndAwait(attempt -> {
                    installResources(attempt, secondContext, secondLoader);
                    secondInstalled.countDown();
                    awaitCancellationSignal(secondWorkerCancelled);
                });
            } catch (Throwable expected) {
                // Same: cancellation is delivered, the await gives up.
            }
            secondWaiterDone.countDown();
        });
        assertThat(firstInstalled.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(secondInstalled.await(15, TimeUnit.SECONDS)).isTrue();

        // Batch stop pass 1 — requestStop on every attempt — then the first
        // pass-2 close blocks on its context and holds its caller.
        first.requestStop();
        second.requestStop();
        assertThat(firstCloseBlocked.await(15, TimeUnit.SECONDS))
                .as("the first attempt's close blocks on the gated context")
                .isTrue();
        verify(firstLoader, never()).close();
        // While that close is blocked, the second attempt has already received
        // its cancellation signal and completed its own cleanup.
        assertThat(secondWaiterDone.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(secondWorkerCancelled).isTrue();
        verify(secondContext, times(1)).close();
        verify(secondLoader, times(1)).close();

        gate.countDown();
        assertThat(firstWaiterDone.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(firstWorkerCancelled).isTrue();
        verify(firstContext, times(1)).close();
        verify(firstLoader, times(1)).close();
        awaiters.shutdown();
        awaitNoThreads("core-owner-batch-await", "core-owner-context-startup-batch");
    }

    @Test
    void stoppingOneAttemptLeavesAnotherAttemptsSlotRunning() throws Exception {
        OwnerStartup stopped = new OwnerStartup("slot-one", 120_000L);
        OwnerStartup survivor = new OwnerStartup("slot-two", 120_000L);
        ConfigurableApplicationContext survivorContext = mock(ConfigurableApplicationContext.class);
        URLClassLoader survivorLoader = mock(URLClassLoader.class);
        AtomicReference<String> stoppedWorker = new AtomicReference<>();
        AtomicReference<String> survivorWorker = new AtomicReference<>();
        AtomicReference<Throwable> survivorFailure = new AtomicReference<>();
        CountDownLatch stoppedWorkerRunning = new CountDownLatch(1);
        CountDownLatch survivorWorkerRunning = new CountDownLatch(1);
        CountDownLatch survivorReleased = new CountDownLatch(1);
        CountDownLatch stoppedCallerDone = new CountDownLatch(1);
        CountDownLatch survivorCallerDone = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "core-owner-slot-test-caller");
            thread.setDaemon(true);
            return thread;
        });
        callers.submit(() -> {
            try {
                stopped.startAndAwait(attempt -> {
                    stoppedWorker.set(Thread.currentThread().getName());
                    stoppedWorkerRunning.countDown();
                    awaitCancellationSignal(new AtomicBoolean());
                });
            } catch (Throwable expected) {
                // Stopping the attempt ends its own await.
            }
            stoppedCallerDone.countDown();
        });
        callers.submit(() -> {
            try {
                survivor.startAndAwait(attempt -> {
                    survivorWorker.set(Thread.currentThread().getName());
                    survivorWorkerRunning.countDown();
                    try {
                        survivorReleased.await();
                    } catch (InterruptedException cancelledSlot) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("survivor slot was cancelled", cancelledSlot);
                    }
                    installResources(attempt, survivorContext, survivorLoader);
                });
            } catch (Throwable failure) {
                survivorFailure.set(failure);
            }
            survivorCallerDone.countDown();
        });
        assertThat(stoppedWorkerRunning.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(survivorWorkerRunning.await(15, TimeUnit.SECONDS)).isTrue();

        stopped.close();
        assertThat(stoppedCallerDone.await(15, TimeUnit.SECONDS)).isTrue();
        // The stopped attempt's slot drained to completion ...
        awaitNoThreads("core-owner-context-startup-slot-one");
        assertThat(survivorWorker.get())
                .as("each attempt owns a distinct startup slot thread")
                .isNotEqualTo(stoppedWorker.get());
        assertThat(anyThreadWithName(survivorWorker.get()))
                .as("draining one attempt must not touch another attempt's worker")
                .isTrue();
        verify(survivorContext, never()).close();

        // ... and the other attempt still finishes its own boot untouched.
        survivorReleased.countDown();
        assertThat(survivorCallerDone.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(survivorFailure.get()).isNull();
        assertThat(survivor.context()).isSameAs(survivorContext);
        survivor.close();
        verify(survivorContext, times(1)).close();
        verify(survivorLoader, times(1)).close();
        callers.shutdown();
        awaitNoThreads("core-owner-slot-test", "core-owner-context-startup-slot");
    }

    @Test
    void drainUsesAFreshBoundedBudgetAndRecordsANonTerminatingWorker() throws Exception {
        OwnerStartup startup = new OwnerStartup("drain", 1_000L);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> awaitFailure = new AtomicReference<>();
        CountDownLatch waiterDone = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "core-owner-drain-caller");
            thread.setDaemon(true);
            return thread;
        });
        caller.submit(() -> {
            try {
                startup.startAndAwait(attempt -> {
                    while (true) {
                        try {
                            release.await();
                            return;
                        } catch (InterruptedException ignored) {
                            // A child boot that ignores cancellation: the
                            // awaiter must still come back on a fresh,
                            // bounded drain budget.
                        }
                    }
                });
            } catch (Throwable failure) {
                awaitFailure.set(failure);
            }
            waiterDone.countDown();
        });

        assertThat(waiterDone.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitFailure.get()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
        assertThat(anyThreadWithName("core-owner-context-startup-drain"))
                .as("the drain was bounded: the interrupt-ignoring worker is still alive")
                .isTrue();
        release.countDown();
        caller.shutdown();
        awaitNoThreads("core-owner-context-startup-drain", "core-owner-drain-caller");
    }

    private static BiConsumer<CoreModuleDefinition, OwnerStartup> installed(
            ConfigurableApplicationContext context,
            URLClassLoader classLoader) {
        return (module, attempt) -> installResources(attempt, context, classLoader);
    }

    /** The boot seam rule: install into the attempt passed to the boot, never a rival. */
    private static void installResources(
            OwnerStartup attempt,
            ConfigurableApplicationContext context,
            URLClassLoader classLoader) {
        attempt.setClassLoader(classLoader);
        attempt.setContext(context);
    }

    /** A context whose close parks on a gate, announcing that it got there. */
    private static ConfigurableApplicationContext blockingClose(
            CountDownLatch closeEntered, CountDownLatch release) throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        doAnswer(invocation -> {
            closeEntered.countDown();
            release.await();
            return null;
        }).when(context).close();
        return context;
    }

    private static void awaitCancellationSignal(AtomicBoolean cancelled) {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException expected) {
            cancelled.set(true);
            Thread.currentThread().interrupt();
        }
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

    private static boolean anyThreadWithName(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().startsWith(prefix));
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
