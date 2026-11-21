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
 * <p>Registers the Spring-free Problem domain service for the App shell's
 * Problem mutation module.
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
