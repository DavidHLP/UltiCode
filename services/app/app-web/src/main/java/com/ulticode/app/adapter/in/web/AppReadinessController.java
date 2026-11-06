package com.ulticode.app.adapter.in.web;

import com.ulticode.common.health.ReadinessChecks;
import com.ulticode.common.storage.StorageReadiness;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness probe for the backend-app owner.
 *
 * <p>Review 2026-08-25 P0: production Compose gates {@code service_healthy}
 * on this endpoint. Unlike the static liveness endpoint, it verifies the
 * runtime dependencies the owner needs to serve traffic &mdash; the owner
 * database and Redis &mdash; and returns 503 when either is unavailable so
 * dependents do not start against a broken instance.
 */
@RestController
@RequestMapping("/api/v1/app")
@lombok.extern.slf4j.Slf4j
public class AppReadinessController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final StorageReadiness storageReadiness;
    private final ReferenceBeanManager references;
    private final Map<String, DubboDiscoveryReadiness.Observation> lastDiscovery = new LinkedHashMap<>();
    private final Map<String, DubboDiscoveryReadiness.ApplicationObservation> lastApplicationDiscovery = new LinkedHashMap<>();

    /**
     * The DataSource is resolved through {@link ObjectProvider} so contexts
     * that exclude datasource autoconfiguration (unit-test profile) still
     * boot; with no DataSource the probe reports db=false instead of failing
     * the whole context.
     */
    public AppReadinessController(org.springframework.beans.factory.ObjectProvider<DataSource> dataSourceProvider,
                                  StringRedisTemplate redisTemplate,
                                  org.springframework.beans.factory.ObjectProvider<StorageReadiness> storageReadinessProvider,
                                  org.springframework.beans.factory.ObjectProvider<ReferenceBeanManager> referenceProvider) {
        this.dataSource = dataSourceProvider.getIfAvailable();
        this.redisTemplate = redisTemplate;
        // Absent only in contexts that do not carry the shared storage module.
        this.storageReadiness = storageReadinessProvider.getIfAvailable();
        this.references = referenceProvider.getIfAvailable();
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Boolean> components = new LinkedHashMap<>();
        components.put("db", ReadinessChecks.dataSourceUp(dataSource));
        components.put("redis", redisUp());
        // Re-probe asynchronously after a runtime failure so health checks can recover an idle instance.
        if (storageReadiness != null) {
            storageReadiness.probeIfFailed();
        }
        components.put("storage", storageReadiness == null || storageReadiness.isReady());
        observeDiscovery();
        boolean allUp = components.values().stream().allMatch(Boolean::booleanValue);
        return ResponseEntity
                .status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ReadinessChecks.report(components));
    }

    private synchronized void observeDiscovery() {
        try {
            observeInterface("submission_user_query", SubmissionUserQueryPort.class.getName());
            observeInterface("auth_account_query", AccountQueryService.class.getName());
            observeApplicationInterface("submission_user_query", SubmissionUserQueryPort.class.getName());
            observeApplicationInterface("auth_account_query", AccountQueryService.class.getName());
        } catch (RuntimeException ignored) {
            // Diagnostic failures never affect HTTP readiness or invoke business RPC.
        }
    }

    private void observeInterface(String alias, String interfaceName) {
        var observation = DubboDiscoveryReadiness.inspect(references, interfaceName);
        if (observation.equals(lastDiscovery.put(alias, observation))) return;
        // Two fixed aliases, metadata only, logged on change rather than on every probe.
        log.info("AppDiscoveryObservation interface={} state={} invoker_kind={} directory_kind={} destroyed={} all={} valid={} reconnect={} disabled={}",
                alias, observation.state(), observation.invokerKind(), observation.directoryKind(),
                observation.destroyed(), observation.all().token(), observation.valid().token(),
                observation.reconnect().token(), observation.disabled().token());
    }

    private void observeApplicationInterface(String alias, String interfaceName) {
        var application = DubboDiscoveryReadiness.inspectApplication(references, interfaceName);
        if (application.equals(lastApplicationDiscovery.put(alias, application))) return;
        var observation = application.directory();
        log.info("AppApplicationDiscoveryObservation interface={} state={} invoker_kind={} directory_kind={} destroyed={} all={} valid={} reconnect={} disabled={} listener_state={} mapped_apps={} observed_apps={} instances={}",
                alias, observation.state(), observation.invokerKind(), observation.directoryKind(),
                observation.destroyed(), observation.all().token(), observation.valid().token(),
                observation.reconnect().token(), observation.disabled().token(), application.listenerState(),
                application.mappedApplications().token(), application.observedApplications().token(),
                application.instances().token());
    }

    private boolean redisUp() {
        try {
            String pong = redisTemplate.execute(
                    (RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
