package com.ulticode.submission.config;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.rpc.resilience.DubboDiscoveryReadiness;
import java.time.Duration;
import java.util.Set;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Do not announce the worker ready before its projection providers are discovered. */
@lombok.extern.slf4j.Slf4j
@Component
@Profile("!test")
public class SubmissionDiscoveryStartupGate implements ApplicationRunner {
    private final ReferenceBeanManager references;
    private static final Set<String> REQUIRED = Set.of(
            ProblemFactsPort.class.getName(), SubmissionUserReadPort.class.getName());

    public SubmissionDiscoveryStartupGate(ObjectProvider<ReferenceBeanManager> references) {
        this.references = references.getIfAvailable();
    }

    boolean ready() { return DubboDiscoveryReadiness.available(references, REQUIRED); }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        await(Duration.ofMinutes(2));
        log.info("Submission projection providers discovered");
    }

    void await(Duration timeout) throws InterruptedException {
        long started = System.nanoTime();
        long deadline = started + timeout.toNanos();
        while (!ready()) {
            if (System.nanoTime() >= deadline) {
                observe("deadline", started);
                throw new IllegalStateException("Submission projection providers were not discovered before readiness deadline");
            }
            Thread.sleep(250);
        }
        observe("ready", started);
    }

    private void observe(String outcome, long started) {
        // Separate observations may race with discovery; they never determine the outcome.
        try {
            long elapsed = Math.min(86_400_000L, Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
            observeInterface(outcome, elapsed, "problem_facts", ProblemFactsPort.class.getName());
            observeInterface(outcome, elapsed, "user_read", SubmissionUserReadPort.class.getName());
        } catch (RuntimeException ignored) {
            // Diagnostics must not change startup success/failure, including logging failures.
        }
    }

    private void observeInterface(String outcome, long elapsed, String alias, String interfaceName) {
        var observation = DubboDiscoveryReadiness.inspect(references, interfaceName);
        log.info("SubmissionDiscoveryObservation outcome={} elapsed_ms={} interface={} state={} invoker_kind={} directory_kind={} destroyed={} all={} valid={} reconnect={} disabled={}",
                outcome, elapsed, alias, observation.state(), observation.invokerKind(), observation.directoryKind(),
                observation.destroyed(), observation.all().token(), observation.valid().token(),
                observation.reconnect().token(), observation.disabled().token());
    }
}
