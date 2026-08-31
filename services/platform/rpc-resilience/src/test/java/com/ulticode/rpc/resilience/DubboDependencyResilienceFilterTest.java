package com.ulticode.rpc.resilience;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dubbo dependency resilience filter")
class DubboDependencyResilienceFilterTest {

    @Test
    void spiActivatesForConsumers() {
        ExtensionLoader<ClusterFilter> loader =
                ExtensionLoader.getExtensionLoader(ClusterFilter.class);

        assertThat(loader.getExtension("dependency-resilience"))
                .isInstanceOf(DubboDependencyResilienceFilter.class);
        assertThat(loader.getActivateExtension(
                serviceUrl(), "reference.filter", "consumer"))
                .anyMatch(DubboDependencyResilienceFilter.class::isInstance);
    }

    @Test
    void transportFailuresOpenAndOneProbeRecovers() {
        AtomicLong now = new AtomicLong();
        DubboDependencyResilienceFilter filter = new DubboDependencyResilienceFilter(
                2, 2, Duration.ofSeconds(5), now::get);
        StubInvoker invoker = new StubInvoker(ignored -> {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "slow dependency");
        });

        assertThatThrownBy(() -> filter.invoke(invoker, invocation())).isInstanceOf(RpcException.class);
        assertThatThrownBy(() -> filter.invoke(invoker, invocation())).isInstanceOf(RpcException.class);
        int callsBeforeOpenRejection = invoker.calls.get();

        assertThatThrownBy(() -> filter.invoke(invoker, invocation()))
                .isInstanceOf(RpcException.class)
                .hasMessageContaining("circuit");
        assertThat(invoker.calls).hasValue(callsBeforeOpenRejection);

        now.addAndGet(Duration.ofSeconds(5).toMillis());
        invoker.behavior = ignored -> new AppResponse("ok");
        assertThat(filter.invoke(invoker, invocation()).getValue()).isEqualTo("ok");
        assertThat(filter.stateFor(serviceUrl().getServiceKey()))
                .isEqualTo(com.ulticode.common.resilience.DependencyGuard.State.CLOSED);
    }

    @Test
    void businessFailuresDoNotTripTheCircuit() {
        DubboDependencyResilienceFilter filter = new DubboDependencyResilienceFilter(
                2, 2, Duration.ofSeconds(5), System::currentTimeMillis);
        StubInvoker invoker = new StubInvoker(ignored ->
                new AppResponse(new RpcException(RpcException.BIZ_EXCEPTION, "rejected command")));

        for (int attempt = 0; attempt < 5; attempt++) {
            Result result = filter.invoke(invoker, invocation());
            assertThat(result.hasException()).isTrue();
        }

        assertThat(filter.stateFor(serviceUrl().getServiceKey()))
                .isEqualTo(com.ulticode.common.resilience.DependencyGuard.State.CLOSED);
    }

    private static RpcInvocation invocation() {
        return new RpcInvocation("lookup", TestService.class.getName(), "1.0.0",
                new Class<?>[0], new Object[0]);
    }

    private static URL serviceUrl() {
        return URL.valueOf("dubbo://127.0.0.1:20880/" + TestService.class.getName()
                + "?group=test&version=1.0.0");
    }

    private interface TestService {
    }

    private static final class StubInvoker implements Invoker<TestService> {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile Function<Invocation, Result> behavior;

        private StubInvoker(Function<Invocation, Result> behavior) {
            this.behavior = behavior;
        }

        @Override
        public Class<TestService> getInterface() {
            return TestService.class;
        }

        @Override
        public Result invoke(Invocation invocation) {
            calls.incrementAndGet();
            return behavior.apply(invocation);
        }

        @Override
        public URL getUrl() {
            return serviceUrl();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
        }
    }
}
