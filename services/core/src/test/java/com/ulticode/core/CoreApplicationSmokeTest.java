package com.ulticode.core;

import com.ulticode.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CORE_OWNER_CONTEXTS_ENABLED=false",
        "CORE_JUDGE_REQUIRED=false",
        "spring.main.web-application-type=servlet"
})
class CoreApplicationSmokeTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithExplicitOwnerRegistryAndCoreReadinessEndpoint() {
        CoreModuleRegistry registry = new CoreModuleRegistry();
        assertThat(registry.modules()).extracting(CoreModuleDefinition::name)
                .containsExactly("auth", "admin", "app", "submission", "notification", "search");
        assertThat(registry.modules()).extracting(CoreModuleDefinition::transactionManagerBean)
                .containsExactly("authTransactionManager", "adminTransactionManager",
                        "appTransactionManager", "submissionTransactionManager",
                        "notificationTransactionManager", null);
    }

    @Test
    void appChildDoesNotUseCrossOwnerBroadModuleScan() {
        assertThat(componentScan(CoreOwnerBootConfigurations.App.class))
                .doesNotContain("com.ulticode.modules");
        assertThat(componentScan(CoreOwnerBootConfigurations.App.class))
                .contains(
                        "com.ulticode.modules.contest",
                        "com.ulticode.modules.event.inbox",
                        "com.ulticode.modules.reconciliation.port",
                        "com.ulticode.modules.submission.port");
        assertThat(componentScan(CoreOwnerBootConfigurations.Admin.class))
                .contains("com.ulticode.modules.event.inbox")
                .doesNotContain("com.ulticode.modules.reconciliation");
        assertThat(componentScan(CoreOwnerBootConfigurations.Submission.class))
                .doesNotContain("com.ulticode.modules.submission");
        assertThat(componentScan(CoreOwnerBootConfigurations.Notification.class))
                .doesNotContain("com.ulticode.modules.notification");
    }

    private static String[] componentScan(Class<?> configuration) {
        org.springframework.context.annotation.ComponentScan scan =
                configuration.getAnnotation(
                        org.springframework.context.annotation.ComponentScan.class);
        return scan.value().length == 0 ? scan.basePackages() : scan.value();
    }
    @Test
    void bindsOneDatasourceAndTransactionManagerPerDataOwner() {
        DataSource auth = context.getBean("authDataSource", DataSource.class);
        DataSource admin = context.getBean("adminDataSource", DataSource.class);
        DataSource app = context.getBean("appDataSource", DataSource.class);
        DataSource submission = context.getBean("submissionDataSource", DataSource.class);
        DataSource notification = context.getBean("notificationDataSource", DataSource.class);

        assertThat(auth).isNotSameAs(admin).isNotSameAs(app)
                .isNotSameAs(submission).isNotSameAs(notification);
        assertThat(context.getBean("authTransactionManager"))
                .isNotSameAs(context.getBean("adminTransactionManager"))
                .isNotSameAs(context.getBean("appTransactionManager"))
                .isNotSameAs(context.getBean("submissionTransactionManager"))
                .isNotSameAs(context.getBean("notificationTransactionManager"));
        assertThat(context.getBean(CoreLocalAuthorizationMutationAdapter.class)).isNotNull();
        assertThat(context.containsBean("backendAuthApplication")).isFalse();
        assertThat(context.getClassLoader()
                .getResource("com/ulticode/modules/submission/runtime/async/AsyncSandboxExecutor.class"))
                .isNull();
    }

    @Test
    void disabledOwnerContextsFailReadinessClosed() {
        CoreOwnerContextManager manager = new CoreOwnerContextManager(
                new CoreModuleRegistry(),
                new org.springframework.mock.env.MockEnvironment(),
                false,
                1_000L);
        CoreReadinessService readiness = new CoreReadinessService(
                manager, new com.ulticode.common.lifecycle.DrainGate(), "", false);
        CoreReadinessController controller = new CoreReadinessController(readiness);

        ResponseEntity<Result<CoreReadinessService.Snapshot>> response = controller.ready();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().owners().values())
                .allMatch(state -> state == CoreOwnerContextManager.State.DISABLED);
    }

    @Test
    void requiredJudgeWithoutReadinessEndpointFailsClosed() {
        CoreOwnerContextManager manager = new CoreOwnerContextManager(
                new CoreModuleRegistry(),
                new org.springframework.mock.env.MockEnvironment(),
                false,
                1_000L);
        CoreReadinessService readiness = new CoreReadinessService(
                manager, new com.ulticode.common.lifecycle.DrainGate(), "", true);

        CoreReadinessService.Snapshot snapshot = readiness.snapshot();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.judgeState()).isEqualTo("NOT_CONFIGURED");
    }
}
