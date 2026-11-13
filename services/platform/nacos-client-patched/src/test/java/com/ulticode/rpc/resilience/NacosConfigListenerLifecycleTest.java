package com.ulticode.rpc.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

/**
 * Bounded SVC-031 listener contract, imported from the immutable eight-case diagnostic.
 * Controlled creation gates support both 2.5.1 put and 2.5.3 computeIfAbsent.
 * Runs with ordinary Surefire test selection; no assertion is skipped.
 *
 * Constructors are bypassed to avoid HTTP, authentication, RPC and subscriber setup.
 * Only real listener-pool allocation and real SDK shutdown are exercised. This is
 * not a substitute for startup/callback/race or complete application lifecycle tests.
 */
class NacosConfigListenerLifecycleTest {
    private static final long WAIT_SECONDS = 2;

    @Test
    void sdkShutdownTerminatesAlreadyCreatedListenerPools() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.createTwoListeners();
            Observation observation = fixture.shutdownAndObserve("termination");
            assertThat(observation.closedListeners()).isEqualTo(2);
            assertThat(observation.terminatedListeners()).isEqualTo(2);
            assertThat(observation.aliveListenerThreads()).isZero();
        }
    }

    @Test
    void capturedListenerPoolRejectsWorkAfterSdkShutdown() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.createTwoListeners();
            Observation observation = fixture.shutdownAndObserve("rejection");
            assertThat(observation.rejectedSubmissions()).isEqualTo(2);
        }
    }

    @Test
    void repeatedFixtureCyclesDoNotRetainTheirOwnedThreads() throws Exception {
        List<Fixture> fixtures = new ArrayList<>();
        List<Integer> aliveAfterEachCycle = new ArrayList<>();
        Set<ExecutorService> distinctPools = Collections.newSetFromMap(new IdentityHashMap<>());
        try (FixtureGroup cleanup = new FixtureGroup(fixtures)) {
            for (int cycle = 1; cycle <= 3; cycle++) {
                Fixture fixture = new Fixture();
                fixtures.add(fixture);
                fixture.createTwoListeners();
                assertThat(distinctPools.addAll(fixture.listeners)).isTrue();
                assertThat(distinctPools).hasSize(cycle * 2);
                fixture.shutdownAndObserve("cycle-" + cycle);
                int alive = fixtures.stream().mapToInt(Fixture::aliveListenerThreads).sum();
                aliveAfterEachCycle.add(alive);
            }
            System.out.println("SVC-031 PRE-CLEANUP cumulative-owned-alive=" + aliveAfterEachCycle);
            assertThat(aliveAfterEachCycle).containsExactly(0, 0, 0);
        }
    }

    @Test
    void shutdownRejectsBothExistingAndNewTaskIdsWithoutGrowingTheMap() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.createTwoListeners();
            fixture.shutdownAndObserve("post-close");
            ExecutorService existing = null;
            ExecutorService fresh = null;
            Throwable existingFailure = null;
            Throwable freshFailure = null;
            try {
                existing = (ExecutorService) fixture.ensureSyncExecutor.invoke(fixture.transport, "one");
            } catch (InvocationTargetException failure) {
                existingFailure = failure.getCause();
            }
            try {
                fresh = (ExecutorService) fixture.ensureSyncExecutor.invoke(fixture.transport, "after-close");
            } catch (InvocationTargetException failure) {
                freshFailure = failure.getCause();
            }
            boolean acceptedNewTask = false;
            if (fresh != null && !fresh.isShutdown()) {
                fixture.listeners.add(fresh);
                Thread thread = fresh.submit(Thread::currentThread).get(WAIT_SECONDS, TimeUnit.SECONDS);
                fixture.listenerThreads.add(thread);
                acceptedNewTask = true;
            }
            boolean existingRejected = existingFailure instanceof RejectedExecutionException;
            boolean freshRejected = freshFailure instanceof RejectedExecutionException;
            int mapSize = fixture.listenerMap().size();
            System.out.println("SVC-031 PRE-CLEANUP post-close details existingEnsureRejected=" + existingRejected
                    + " existingReturnedClosedPool=" + (existing != null && existing.isShutdown())
                    + " newEnsureRejected=" + freshRejected + " newTaskAccepted=" + acceptedNewTask
                    + " mapSize=" + mapSize);
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> assertThat(existingRejected).as("existing taskId ensure rejected").isTrue(),
                    () -> assertThat(freshRejected).as("new taskId ensure rejected").isTrue(),
                    () -> assertThat(mapSize).as("no post-close map expansion").isEqualTo(2));
        }
    }

    @Test
    void creationAlreadyInProgressCannotPublishAnOpenPoolAfterShutdown() throws Exception {
        try (Fixture fixture = new Fixture()) {
            GateMap gate = new GateMap();
            set(fixture.transport.getClass(), fixture.transport, "multiTaskExecutor", gate);
            FutureTask<ExecutorService> creator = new FutureTask<>(() ->
                    (ExecutorService) fixture.ensureSyncExecutor.invoke(fixture.transport, "race"));
            FutureTask<Void> closer = new FutureTask<>(() -> {
                fixture.shutdownAttempted = true;
                fixture.worker.shutdown();
                return null;
            });
            Thread createThread = daemon("svc031-test-create", creator);
            Thread closeThread = daemon("svc031-test-close", closer);
            try {
                createThread.start();
                awaitGate(gate.entered);
                closeThread.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
                while (!closer.isDone() && !blockedOn(closeThread, fixture.transport)
                        && System.nanoTime() < deadline) Thread.yield();
                assertThat(closer.isDone() || blockedOn(closeThread, fixture.transport))
                        .as("closer finishes or blocks on the exact pool-creation monitor").isTrue();
                gate.release.countDown();
                ExecutorService pool = creator.get(WAIT_SECONDS, TimeUnit.SECONDS);
                closer.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(pool.isShutdown()).as("created pool closed after creation-first race").isTrue();
                assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> pool.submit(() -> {})).isInstanceOf(RejectedExecutionException.class);
                assertRejectedEnsure(fixture, "after-race");
                assertThat(fixture.listenerMap()).hasSize(1);
                assertThat(rpcSnapshot()).isEqualTo(fixture.rpcBefore);
                System.out.println("SVC-031 PRE-CLEANUP creation-first: created pool closed, future creation rejected");
            } finally {
                gate.release.countDown();
                joinActor(createThread);
                joinActor(closeThread);
            }
        }
    }

    @Test
    void shutdownAlreadyInProgressRejectsCreationBeforeSecurityShutdownReturns() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.security.entered = new CountDownLatch(1);
            fixture.security.release = new CountDownLatch(1);
            FutureTask<Void> closer = new FutureTask<>(() -> {
                fixture.shutdownAttempted = true;
                fixture.worker.shutdown();
                return null;
            });
            Thread closeThread = daemon("svc031-test-close-first", closer);
            boolean rejected = false;
            try {
                closeThread.start();
                awaitGate(fixture.security.entered);
                try {
                    fixture.ensureSyncExecutor.invoke(fixture.transport, "late");
                } catch (InvocationTargetException failure) {
                    assertThat(failure.getCause()).isInstanceOf(RejectedExecutionException.class);
                    rejected = true;
                }
            } finally {
                fixture.security.release.countDown();
                joinActor(closeThread);
            }
            closer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(rejected).as("reject listener creation while shutdown has already begun").isTrue();
            assertThat(fixture.listenerMap()).isEmpty();
            assertThat(rpcSnapshot()).isEqualTo(fixture.rpcBefore);
            System.out.println("SVC-031 PRE-CLEANUP close-first: no late pool created");
        }
    }

    @Test
    void securityShutdownFailureStillClosesListenersAndDoesNotSuppressCleanupRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.createTwoListeners();
            fixture.security.failNextShutdown = true;
            fixture.shutdownAttempted = true;
            assertThatThrownBy(fixture.worker::shutdown)
                    .isInstanceOf(com.alibaba.nacos.api.exception.NacosException.class)
                    .hasMessageContaining("synthetic shutdown failure");
            assertThat(fixture.scheduler.isShutdown()).as("unchanged upstream failure path remains incomplete").isFalse();
            for (ExecutorService pool : fixture.listeners) {
                assertThat(pool.isShutdown()).as("listener pools close before security failure").isTrue();
                assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
            assertRejectedEnsure(fixture, "after-failure");
            fixture.worker.shutdown();
            assertThat(fixture.security.shutdownCalls).isEqualTo(2);
            assertThat(fixture.scheduler.isShutdown()).as("retry still reaches original cleanup").isTrue();
            fixture.worker.shutdown();
            assertThat(fixture.security.shutdownCalls).isEqualTo(3);
            assertThat(fixture.listenerMap()).hasSize(2);
            assertRejectedEnsure(fixture, "one");
            assertThat(rpcSnapshot()).isEqualTo(fixture.rpcBefore);
            System.out.println("SVC-031 PRE-CLEANUP security-failure: listeners closed; original cleanup retried twice");
        }
    }

    @Test
    void simultaneousCreationForOneTaskAllocatesOnlyOnePool() throws Exception {
        try (Fixture fixture = new Fixture()) {
            GateMap gate = new GateMap();
            set(fixture.transport.getClass(), fixture.transport, "multiTaskExecutor", gate);
            FutureTask<ExecutorService> first = new FutureTask<>(() ->
                    (ExecutorService) fixture.ensureSyncExecutor.invoke(fixture.transport, "same"));
            FutureTask<ExecutorService> second = new FutureTask<>(() ->
                    (ExecutorService) fixture.ensureSyncExecutor.invoke(fixture.transport, "same"));
            Thread one = daemon("svc031-test-first-create", first);
            Thread two = daemon("svc031-test-second-create", second);
            try {
                one.start();
                awaitGate(gate.entered);
                two.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
                while (gate.attempts.get() < 2 && !blockedOn(two, fixture.transport)
                        && System.nanoTime() < deadline) Thread.yield();
                assertThat(gate.attempts.get() >= 2 || blockedOn(two, fixture.transport))
                        .as("second creator reaches the map or exact transport monitor").isTrue();
                gate.release.countDown();
                ExecutorService left = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
                ExecutorService right = second.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(gate.created).as("actual allocation count, not just final map size").hasSize(1);
                assertThat(left).isSameAs(right);
                assertThat(gate).hasSize(1);
                assertThat(rpcSnapshot()).isEqualTo(fixture.rpcBefore);
                System.out.println("SVC-031 PRE-CLEANUP same-task concurrent creation: one allocation, same instance");
            } finally {
                gate.release.countDown();
                joinActor(one);
                joinActor(two);
                // Include any unpublished loser from the deliberately failing 2.5.1 control.
                fixture.listeners.addAll(gate.created);
            }
        }
    }

    private static void assertRejectedEnsure(Fixture fixture, String taskId) {
        assertThatThrownBy(() -> fixture.ensureSyncExecutor.invoke(fixture.transport, taskId))
                .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(RejectedExecutionException.class);
    }

    private static boolean blockedOn(Thread thread, Object lock) {
        ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
        return info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null
                && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(lock);
    }

    private static Thread daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void joinActor(Thread thread) throws InterruptedException {
        if (thread.getState() == Thread.State.NEW) return;
        thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS * 2));
        assertThat(thread.isAlive()).as("test actor terminated: " + thread.getName()).isFalse();
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            assertThat(gate.await(WAIT_SECONDS * 2, TimeUnit.SECONDS)).as("controlled test gate").isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Fixture gate interrupted", interrupted);
        }
    }

    private static final class GateMap extends ConcurrentHashMap<String, ExecutorService> {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.ConcurrentLinkedQueue<ExecutorService> created =
                new java.util.concurrent.ConcurrentLinkedQueue<>();

        @Override
        public ExecutorService put(String key, ExecutorService value) {
            attempts.incrementAndGet();
            created.add(value);
            entered.countDown();
            awaitGate(release);
            return super.put(key, value);
        }

        @Override
        public ExecutorService computeIfAbsent(String key,
                java.util.function.Function<? super String, ? extends ExecutorService> factory) {
            attempts.incrementAndGet();
            return super.computeIfAbsent(key, k -> {
                entered.countDown();
                awaitGate(release);
                ExecutorService result = factory.apply(k);
                created.add(result);
                return result;
            });
        }
    }

    /** Try-with-resources preserves the contract failure and still closes all fixtures. */
    private record FixtureGroup(List<Fixture> fixtures) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            Throwable cleanupFailure = null;
            for (Fixture fixture : fixtures) {
                try {
                    fixture.close();
                } catch (Throwable failure) {
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                }
            }
            if (cleanupFailure instanceof Exception exception) throw exception;
            if (cleanupFailure instanceof Error error) throw error;
        }
    }

    private record Observation(int closedListeners, int terminatedListeners,
            int rejectedSubmissions, int aliveListenerThreads) {}

    private static final class Fixture implements AutoCloseable {
        private final ClientWorker worker;
        private final Object transport;
        private final Method ensureSyncExecutor;
        private final RecordingSecurityProxy security;
        private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "svc031-fixture-main");
            thread.setDaemon(true);
            return thread;
        });
        private final List<ExecutorService> listeners = new ArrayList<>();
        private final List<Thread> listenerThreads = new ArrayList<>();
        private final Map<String, RpcClient> rpcBefore = rpcSnapshot();
        private Thread schedulerThread;
        private boolean shutdownAttempted;

        private Fixture() throws Exception {
            String version = System.getProperty("nacos.lifecycle.expectedVersion", "2.5.3");
            assertThat(version).as("supported private fixture layouts").isIn("2.5.1", "2.5.3");
            String source = ClientWorker.class.getProtectionDomain().getCodeSource().getLocation().toString();
            assertThat(source.endsWith("/nacos-client-patched-2.5.3-ulticode-2.jar")
                    || source.endsWith("/nacos-client-patched/target/classes/")).isTrue();
            String root = source.endsWith(".jar") ? "jar:" + source + "!/" : source;
            try (var input = new java.net.URL(root + "META-INF/nacos-client-patch/provenance.json").openStream()) {
                assertThat(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .contains("pkg:maven/com.ulticode/nacos-client-patched@2.5.3-ulticode-2",
                                "com.alibaba.nacos:nacos-client:jar:pure:2.5.3",
                                "a991843c4d991fc68d4acb82c3bd9a43fe1deee3a76f1bd66260d81d08d03fa6");
            }
            assertThat(Collections.list(ClientWorker.class.getClassLoader()
                    .getResources("com/alibaba/nacos/client/config/impl/ClientWorker.class"))).hasSize(1);
            System.out.println("SVC-031 SDK=" + source);

            ObjenesisStd allocator = new ObjenesisStd();
            worker = allocator.newInstance(ClientWorker.class);
            Class<?> transportClass = Class.forName(ClientWorker.class.getName() + "$ConfigRpcTransportClient");
            transport = allocator.newInstance(transportClass);
            security = allocator.newInstance(RecordingSecurityProxy.class);
            String owner = "svc031-" + UUID.randomUUID();
            assertThat(rpcBefore.keySet()).noneMatch(name -> name.startsWith(owner));
            set(ClientWorker.class, worker, "uuid", owner);
            set(ClientWorker.class, worker, "cacheMap", new AtomicReference<>(new HashMap<>()));
            set(ClientWorker.class, worker, "agent", transport);
            Field outer = transportClass.getDeclaredField("this$0");
            assertThat(outer.isSynthetic()).isTrue();
            set(transportClass, transport, "this$0", worker);
            set(transportClass, transport, "multiTaskExecutor", !version.equals("2.5.1")
                    ? new ConcurrentHashMap<>() : new HashMap<>());
            Class<?> parent = transportClass.getSuperclass();
            assertThat(parent.getName()).isEqualTo("com.alibaba.nacos.client.config.impl.ConfigTransportClient");
            set(parent, transport, "executor", scheduler);
            set(parent, transport, "securityProxy", security);
            // These real startup-only resources are not exercised by this bounded contract.
            assertThat(read(transportClass, transport, "subscriber")).isNull();
            if (version.equals("3.3.0")) {
                assertThat(read(transportClass, transport, "listenExecutor")).isNull();
                assertThat(read(parent, transport, "loginScheduledExecutor")).isNull();
                assertThat(read(ClientWorker.class, worker, "configFuzzyWatchGroupKeyHolder")).isNull();
            }
            ensureSyncExecutor = transportClass.getDeclaredMethod("ensureSyncExecutor", String.class);
            assertThat(ensureSyncExecutor.getReturnType()).isEqualTo(ExecutorService.class);
            ensureSyncExecutor.setAccessible(true);
        }

        private void createTwoListeners() throws Exception {
            schedulerThread = scheduler.submit(Thread::currentThread).get(WAIT_SECONDS, TimeUnit.SECONDS);
            for (String taskId : List.of("one", "two")) {
                ExecutorService executor = (ExecutorService) ensureSyncExecutor.invoke(transport, taskId);
                listeners.add(executor);
                assertThat(ensureSyncExecutor.invoke(transport, taskId)).isSameAs(executor);
                Thread thread = executor.submit(Thread::currentThread).get(WAIT_SECONDS, TimeUnit.SECONDS);
                listenerThreads.add(thread);
                assertThat(executor.isShutdown()).isFalse();
                assertThat(thread.isAlive()).isTrue();
            }
            assertThat(listeners.get(0)).isNotSameAs(listeners.get(1));
            assertThat(listenerThreads.get(0)).isNotSameAs(listenerThreads.get(1));
            assertThat(rpcSnapshot()).isEqualTo(rpcBefore);
        }

        private Observation shutdownAndObserve(String label) throws Exception {
            shutdownAttempted = true;
            worker.shutdown();
            assertThat(security.shutdownCalls).as("real shutdown reaches security boundary").isEqualTo(1);
            assertThat(scheduler.isShutdown()).as("real shutdown closes main scheduler").isTrue();
            assertThat(scheduler.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            schedulerThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            assertThat(schedulerThread.isAlive()).isFalse();
            assertThat(rpcSnapshot()).as("no real RPC clients created or removed").isEqualTo(rpcBefore);
            int closed = 0;
            int terminated = 0;
            int rejected = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            for (int i = 0; i < listeners.size(); i++) {
                ExecutorService executor = listeners.get(i);
                if (executor.isShutdown()) closed++;
                if (executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    terminated++;
                    long remaining = deadline - System.nanoTime();
                    if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(listenerThreads.get(i), remaining);
                }
                try {
                    executor.submit(() -> {}).get(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (RejectedExecutionException expected) {
                    rejected++;
                }
            }
            Observation result = new Observation(closed, terminated, rejected, aliveListenerThreads());
            System.out.println("SVC-031 PRE-CLEANUP " + label + " " + result
                    + " mainTerminated=true securityShutdownCalls=" + security.shutdownCalls + " rpcUnchanged=true");
            return result;
        }

        @SuppressWarnings("unchecked")
        private Map<String, ExecutorService> listenerMap() throws Exception {
            return (Map<String, ExecutorService>) read(transport.getClass(), transport, "multiTaskExecutor");
        }

        private int aliveListenerThreads() {
            return (int) listenerThreads.stream().filter(Thread::isAlive).count();
        }

        @Override
        public void close() throws Exception {
            try {
                if (!shutdownAttempted) {
                    shutdownAttempted = true;
                    worker.shutdown();
                }
            } finally {
                // Fixture safety only. All SDK observations/assertions occur before this cleanup.
                // Include pools returned by a failed post-close/race assertion, even if no task ran.
                Set<ExecutorService> allOwned = Collections.newSetFromMap(new IdentityHashMap<>());
                allOwned.addAll(listeners);
                allOwned.addAll(listenerMap().values());
                for (ExecutorService executor : allOwned) executor.shutdownNow();
                scheduler.shutdownNow();
                for (ExecutorService executor : allOwned) {
                    assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                }
                assertThat(scheduler.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                for (Thread thread : listenerThreads) {
                    thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
                    assertThat(thread.isAlive()).as("fixture-owned thread cleanup").isFalse();
                }
                System.out.println("SVC-031 FIXTURE-CLEANUP owned-listener-alive=" + aliveListenerThreads());
            }
        }
    }

    private static Map<String, RpcClient> rpcSnapshot() {
        Map<String, RpcClient> snapshot = new HashMap<>();
        RpcClientFactory.getAllClientEntries().forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return snapshot;
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        assertThat(field.getType().isInstance(value)).as(owner.getSimpleName() + "." + name + " type").isTrue();
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object read(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class RecordingSecurityProxy extends SecurityProxy {
        private int shutdownCalls;
        private boolean failNextShutdown;
        private CountDownLatch entered;
        private CountDownLatch release;

        private RecordingSecurityProxy() {
            super(null, null);
            throw new AssertionError("Must be allocated without invoking constructors");
        }

        @Override
        public void shutdown() throws com.alibaba.nacos.api.exception.NacosException {
            shutdownCalls++;
            if (entered != null) {
                entered.countDown();
                awaitGate(release);
            }
            if (failNextShutdown) {
                failNextShutdown = false;
                throw new com.alibaba.nacos.api.exception.NacosException(500, "synthetic shutdown failure");
            }
        }

        @Override
        public void login(Properties properties) {
            throw new AssertionError("Authentication is outside this fixture");
        }

        @Override
        public void reLogin() {
            throw new AssertionError("Authentication is outside this fixture");
        }

        @Override
        public Map<String, String> getIdentityContext(RequestResource resource) {
            throw new AssertionError("RPC authentication is outside this fixture");
        }
    }
}
