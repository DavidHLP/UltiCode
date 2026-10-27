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
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!ready()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Submission projection providers were not discovered before readiness deadline");
            }
            Thread.sleep(250);
        }
    }
}
