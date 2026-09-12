package com.ulticode.app.config;

import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.port.ProblemWritePort;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import com.ulticode.modules.problem.service.impl.ProblemAdministrationDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean registration for the extracted administration domain service
 * (P7-RELOCATE).
 *
 * <p>The extracted Problem domain is Spring-free: its
 * {@code ProblemAdministrationDomainServiceImpl} carries no stereotype
 * annotation, so the
 * Dubbo providers in {@code com.ulticode.app.dubbo.provider} (which inject
 * the domain-service interfaces) cannot boot without explicit registration
 * here. In-process consumers that assemble the impls manually (e.g.
 * {@code ProblemServiceImpl}) are unaffected by these beans.
 *
 * <p>Kept in the app-owned config package: only the app shell activates
 * these providers (the admin shell scans a different Dubbo provider package
 * and excludes this package from its component scan).
 */
@Configuration
public class AppDomainServiceConfig {

    @Bean
    public ProblemAdministrationDomainService problemAdministrationDomainService(
            ProblemWritePort writePort,
            ProblemDetailDomainPort detailPort,
            ProblemVersionPort versionPort,
            Clock clock) {
        return new ProblemAdministrationDomainServiceImpl(writePort, detailPort, versionPort, clock);
    }

}
