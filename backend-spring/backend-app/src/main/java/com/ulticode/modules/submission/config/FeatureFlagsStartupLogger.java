package com.ulticode.modules.submission.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Logs the ADR-003 judge feature-flag values at startup so operators can confirm
 * whether the M3a outbox shadow write and the M3b generation fence / lease path
 * are active for this deployment (see ADR-003 §3.3 and ADR-005 §3.3).
 *
 * <p>Both flags default to {@code false}; the production judging path is the
 * legacy RQueue + selectById/updateById flow until an operator explicitly opts in.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class FeatureFlagsStartupLogger implements ApplicationRunner {

    private final FeatureFlagsProperties featureFlags;

    @Override
    public void run(ApplicationArguments args) {
        log.info("ADR-003 judge feature flags: useJudgeOutbox={}, useGenerationFence={}",
                featureFlags.isUseJudgeOutbox(),
                featureFlags.isUseGenerationFence());
    }
}
