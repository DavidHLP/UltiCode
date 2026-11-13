package com.ulticode.rpc.resilience;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import org.objenesis.ObjenesisStd;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Pure-memory listener shutdown edge contracts. Constructors/startup/RPC are deliberately not exercised. */
class NacosListenerShutdownEdgeTest {
    static final int WAIT = 3;
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    interface Step { void run() throws Exception; }
    @org.junit.jupiter.api.Test
    void selfShutdown() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService pool = f.ensure("self");
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            Future<?> callback = pool.submit(() -> { entered.countDown(); await(release); f.closeSdkUnchecked(); check(!Thread.currentThread().isInterrupted(), "Callback interrupted"); });
            await(entered);
            AtomicInteger queued = new AtomicInteger();
            Future<?> pending = pool.submit(queued::incrementAndGet);
            release.countDown();
            callback.get(WAIT, TimeUnit.SECONDS);
            pending.get(WAIT, TimeUnit.SECONDS);
            check(queued.get() == 1, "Already accepted work was discarded");
            check(pool.isShutdown(), "Self shutdown left pool open");
            check(pool.awaitTermination(WAIT, TimeUnit.SECONDS), "Self callback did not terminate");
        }
    }
    @org.junit.jupiter.api.Test
    void blockedCallback() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService pool = f.ensure("blocked");
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();
            Future<?> callback = pool.submit(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { interrupted.set(true); Thread.currentThread().interrupt(); } });
            await(entered);
            FutureTask<Void> close = task(f::closeSdk); Thread actor = start("edge-blocked-close", close);
            try {
                close.get(WAIT, TimeUnit.SECONDS);
                check(pool.isShutdown() && !pool.isTerminated(), "Shutdown must close admission without waiting for running callback");
                check(!interrupted.get(), "Graceful shutdown interrupted callback");
                f.expectRejected("blocked"); f.expectRejected("late");
            } finally { release.countDown(); join(actor); }
            callback.get(WAIT, TimeUnit.SECONDS);
            check(pool.awaitTermination(WAIT, TimeUnit.SECONDS), "Blocked callback did not finish after release");
        }
    }
    @org.junit.jupiter.api.Test
    void interruptedCloser() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService pool = f.ensure("interrupt");
            pool.submit(() -> {}).get(WAIT, TimeUnit.SECONDS);
            FutureTask<Void> close = task(() -> {
                Thread.currentThread().interrupt();
                try { f.closeSdk(); check(Thread.currentThread().isInterrupted(), "Caller interrupt flag cleared"); }
                finally { Thread.interrupted(); }
            });
            Thread actor = start("edge-interrupted-close", close);
            try { close.get(WAIT, TimeUnit.SECONDS); } finally { join(actor); }
            check(pool.isShutdown(), "Pre-interrupted shutdown left pool open");
            check(pool.awaitTermination(WAIT, TimeUnit.SECONDS), "Pre-interrupted shutdown did not terminate idle pool");
        }
    }
    @org.junit.jupiter.api.Test
    void concurrentClosers() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            HookPool pool = f.hook("gate", () -> { if (calls.incrementAndGet() == 1) { entered.countDown(); await(release); } });
            FutureTask<Void> one = task(f::closeSdk), two = task(f::closeSdk);
            Thread first = start("edge-first-close", one), second = null;
            try {
                await(entered);
                second = start("edge-second-close", two);
                two.get(WAIT, TimeUnit.SECONDS);
                check(calls.get() >= 2 && pool.isShutdown(), "Second close returned before trying/closing first snapshot");
                check(!one.isDone(), "First close must still be gated");
                f.expectRejected("after-concurrent-close");
            } finally { release.countDown(); join(first); if (second != null) join(second); }
            one.get(WAIT, TimeUnit.SECONDS);
        }
    }
    @org.junit.jupiter.api.Test
    void listenerFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useSortedMap();
            RuntimeException sentinel = new SecurityException("synthetic-listener-denied-once");
            AtomicInteger calls = new AtomicInteger();
            HookPool failed = f.hook("a-fail", () -> { if (calls.incrementAndGet() == 1) throw sentinel; });
            ExecutorService peer = f.ensure("z-peer");
            Throwable actual = capture(f::closeSdk);
            check(contains(actual, sentinel), "Original listener failure lost: " + actual);
            check(peer.isShutdown(), "First listener failure skipped later peer");
            check(f.security.calls.get() >= 1, "Listener failure skipped superclass cleanup");
            check(f.scheduler.isShutdown(), "Listener failure skipped upstream scheduler cleanup");
            f.expectRejected("post-failure");
            check(!failed.isShutdown(), "Failure injection did not occur before real pool shutdown");
            f.closeSdk();
            check(failed.isShutdown(), "Retry did not close failed pool");
        }
    }
    @org.junit.jupiter.api.Test
    void combinedFailures() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useSortedMap();
            RuntimeException listener = new SecurityException("synthetic-listener-failure");
            NacosException security = new NacosException(500, "synthetic-security-failure");
            AtomicBoolean first = new AtomicBoolean(true);
            f.hook("a-fail", () -> { if (first.getAndSet(false)) throw listener; });
            ExecutorService peer = f.ensure("z-peer");
            f.security.nextFailure = security;
            Throwable actual = capture(f::closeSdk);
            check(contains(actual, listener), "Listener exception identity lost");
            check(contains(actual, security), "Security exception identity lost");
            check(peer.isShutdown(), "Combined errors skipped listener peer");
            check(f.security.calls.get() == 1, "Combined errors skipped superclass");
            f.expectRejected("combined-error-late");
            f.closeSdk();
            check(f.scheduler.isShutdown(), "Retry after combined errors skipped upstream cleanup");
        }
    }
    @org.junit.jupiter.api.Test
    void outsideMonitor() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger hooks = new AtomicInteger();
            Step hook = () -> {
                check(!Thread.holdsLock(f.transport), "External cleanup called while admission monitor held");
                FutureTask<Void> rejection = task(() -> f.expectRejected("from-cleanup-hook"));
                Thread actor = start("edge-cleanup-reentry", rejection);
                try { rejection.get(WAIT, TimeUnit.SECONDS); } finally { join(actor); }
                hooks.incrementAndGet();
            };
            f.hook("hook", hook);
            f.security.hook = hook;
            f.closeSdk();
            check(hooks.get() == 2, "Expected pool and security hooks");
        }
    }
    @org.junit.jupiter.api.Test
    void securityReentrant() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService pool = f.ensure("recursive");
            AtomicBoolean recurse = new AtomicBoolean(true);
            f.security.hook = () -> { if (recurse.getAndSet(false)) f.closeSdk(); };
            FutureTask<Void> close = task(f::closeSdk); Thread actor = start("edge-reentrant-close", close);
            try { close.get(WAIT, TimeUnit.SECONDS); } finally { join(actor); }
            check(f.security.calls.get() == 2 && pool.isShutdown(), "Reentrant shutdown skipped required cleanup");
        }
    }
    @org.junit.jupiter.api.Test
    void unrelatedTransport() throws Exception {
        try (Fixture closed = new Fixture(); Fixture alive = new Fixture()) {
            ExecutorService first = closed.ensure("shared-id"), second = alive.ensure("shared-id");
            closed.closeSdk();
            check(first.isShutdown() && !second.isShutdown(), "Shutdown crossed transport ownership");
            check(second.submit(() -> 7).get(WAIT, TimeUnit.SECONDS) == 7, "Other transport cannot run");
            check(alive.ensure("new-id").submit(() -> 9).get(WAIT, TimeUnit.SECONDS) == 9, "Other transport admission closed");
        }
    }
    static Throwable capture(Step step) { try { step.run(); return null; } catch (Throwable failure) { return failure; } }
    static boolean contains(Throwable tree, Throwable target) { return contains(tree, target, Collections.newSetFromMap(new IdentityHashMap<>())); }
    static boolean contains(Throwable tree, Throwable target, Set<Throwable> seen) {
        if (tree == null || !seen.add(tree)) return false;
        if (tree == target || contains(tree.getCause(), target, seen)) return true;
        for (Throwable child : tree.getSuppressed()) if (contains(child, target, seen)) return true;
        return false;
    }
    static void await(CountDownLatch latch) { try { check(latch.await(WAIT * 3L, TimeUnit.SECONDS), "Fixture gate timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    static FutureTask<Void> task(Step step) { return new FutureTask<>(() -> { step.run(); return null; }); }
    static Thread start(String name, Runnable runnable) { Thread thread = new Thread(runnable, name); thread.setDaemon(true); thread.start(); return thread; }
    static void join(Thread thread) throws InterruptedException { thread.join(WAIT * 4000L); check(!thread.isAlive(), "Leaked actor: " + thread.getName()); }
    static void field(Class<?> type, Object object, String name, Object value) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
    static Map<String, RpcClient> snapshot() { Map<String, RpcClient> result = new HashMap<>(); RpcClientFactory.getAllClientEntries().forEach(e -> result.put(e.getKey(), e.getValue())); return result; }
    static class HookPool extends ThreadPoolExecutor {
        final Step hook;
        HookPool(Step hook) { super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> { Thread t = new Thread(r, "edge-hook-pool"); t.setDaemon(true); return t; }); this.hook = hook; }
        public void shutdown() { try { hook.run(); } catch (RuntimeException | Error e) { throw e; } catch (Exception e) { throw new AssertionError(e); } super.shutdown(); }
    }
    static class RecordingSecurity extends SecurityProxy {
        AtomicInteger calls; volatile Step hook; volatile NacosException nextFailure;
        private RecordingSecurity() { super(null, null); throw new AssertionError("Constructor forbidden"); }
        public void shutdown() throws NacosException {
            calls.incrementAndGet();
            if (hook != null) try { hook.run(); } catch (NacosException | RuntimeException | Error e) { throw e; } catch (Exception e) { throw new AssertionError(e); }
            NacosException failure = nextFailure; nextFailure = null; if (failure != null) throw failure;
        }
    }
    static class Fixture implements AutoCloseable {
        final ClientWorker worker; final Object transport; final Method ensure; final RecordingSecurity security;
        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> { Thread t = new Thread(r, "edge-scheduler"); t.setDaemon(true); return t; });
        Map<String, ExecutorService> pools = new ConcurrentHashMap<>();
        final Map<String, RpcClient> before = snapshot();
        Fixture() throws Exception {
            ObjenesisStd allocator = new ObjenesisStd();
            worker = allocator.newInstance(ClientWorker.class);
            Class<?> type = Class.forName(ClientWorker.class.getName() + "$ConfigRpcTransportClient");
            transport = allocator.newInstance(type); security = allocator.newInstance(RecordingSecurity.class); security.calls = new AtomicInteger();
            field(ClientWorker.class, worker, "uuid", "edge-" + UUID.randomUUID());
            field(ClientWorker.class, worker, "cacheMap", new AtomicReference<>(new HashMap<>()));
            field(ClientWorker.class, worker, "agent", transport);
            field(type, transport, "this$0", worker); field(type, transport, "multiTaskExecutor", pools);
            field(type.getSuperclass(), transport, "executor", scheduler); field(type.getSuperclass(), transport, "securityProxy", security);
            ensure = type.getDeclaredMethod("ensureSyncExecutor", String.class); ensure.setAccessible(true);
        }
        void useSortedMap() throws Exception { check(pools.isEmpty(), "Cannot replace populated map"); pools = new ConcurrentSkipListMap<>(); field(transport.getClass(), transport, "multiTaskExecutor", pools); }
        ExecutorService ensure(String id) throws Exception { try { return (ExecutorService) ensure.invoke(transport, id); } catch (InvocationTargetException e) { if (e.getCause() instanceof Exception cause) throw cause; throw e; } }
        HookPool hook(String id, Step callback) { HookPool pool = new HookPool(callback); pools.put(id, pool); return pool; }
        void expectRejected(String id) throws Exception { try { ensure(id); throw new AssertionError("Admission remained open: " + id); } catch (RejectedExecutionException expected) {} }
        void closeSdk() throws Exception { worker.shutdown(); check(snapshot().equals(before), "Fixture changed real RPC factory"); }
        void closeSdkUnchecked() { try { closeSdk(); } catch (Exception e) { throw new AssertionError(e); } }
        public void close() throws Exception {
            // Safety-only cleanup after assertions, never counted as SDK behavior.
            for (ExecutorService pool : pools.values()) pool.shutdownNow(); scheduler.shutdownNow();
            for (ExecutorService pool : pools.values()) check(pool.awaitTermination(WAIT, TimeUnit.SECONDS), "Leaked fixture pool");
            check(scheduler.awaitTermination(WAIT, TimeUnit.SECONDS), "Leaked scheduler");
            check(snapshot().equals(before), "Real RPC factory changed");
        }
    }
}
