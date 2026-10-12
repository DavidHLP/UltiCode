package com.ulticode.modules.learningplan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.learningplan.mapper.LearningPlanMapper;
import com.ulticode.modules.learningplan.port.LearningPlanAccessPort;
import com.ulticode.modules.learningplan.service.LearningPlanService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Explicit bean registration for the app-private learning-plan domain.
 *
 * <p>The domain service carries no stereotype, mirroring the Problem domain
 * registration in {@code AppDomainServiceConfig}. The transaction template is
 * built tolerantly: profiles with no transaction manager (the shell smoke
 * test excludes the datasource autoconfiguration) still boot, and {@code save}
 * fails closed when the manager is absent instead of running a non-atomic
 * write.
 */
@Configuration
public class LearningPlanConfiguration {

    @Bean
    public LearningPlanService learningPlanService(
            LearningPlanMapper learningPlanMapper,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            LearningPlanAccessPort learningPlanAccessPort,
            ObjectMapper objectMapper,
            Clock clock) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
        return new LearningPlanService(
                learningPlanMapper, transactionTemplate, learningPlanAccessPort, objectMapper, clock);
    }
}
