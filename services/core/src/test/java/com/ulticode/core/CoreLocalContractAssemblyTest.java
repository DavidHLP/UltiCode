package com.ulticode.core;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class CoreLocalContractAssemblyTest {

    @Test
    void adminRegistrationCreatesAndValidatesCompleteContractSet() {
        CoreOwnerContextManager ownerContexts = manager();
        CoreModuleDefinition admin = admin();
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            CoreLocalContractAssembly.register(child, admin, ownerContexts);
            child.refresh();

            CoreLocalContractAssembly.validate(child, admin);

            assertThat(child.getBean("coreOwnerContextManager")).isSameAs(ownerContexts);
            assertThat(child.getBean("coreLocalIdentityQueryAdapter"))
                    .isInstanceOf(CoreLocalIdentityQueryAdapter.class);
            assertThat(child.getBean("coreLocalAuthorizationMutationAdapter"))
                    .isInstanceOf(CoreLocalAuthorizationMutationAdapter.class);
            assertThat(child.getBean("coreLocalAccountQueryAdapter"))
                    .isInstanceOf(CoreLocalAccountQueryAdapter.class);
        } finally {
            child.close();
        }
    }

    @Test
    void nonAdminRegistrationAndValidationAreNoOps() {
        CoreOwnerContextManager ownerContexts = manager();
        CoreModuleDefinition auth = new CoreModuleDefinition(
                "auth", "AUTH", CoreOwnerBootConfigurations.Auth.class,
                "authTransactionManager", "backend-auth");
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            CoreLocalContractAssembly.register(child, auth, ownerContexts);
            child.refresh();
            CoreLocalContractAssembly.validate(child, auth);

            assertThat(child.getBeanFactory().containsSingleton("coreOwnerContextManager")).isFalse();
            assertThat(child.getBeanFactory().containsSingleton("coreLocalIdentityQueryAdapter"))
                    .isFalse();
            assertThat(child.getBeanFactory().containsSingleton("coreLocalAuthorizationMutationAdapter"))
                    .isFalse();
            assertThat(child.getBeanFactory().containsSingleton("coreLocalAccountQueryAdapter"))
                    .isFalse();
        } finally {
            child.close();
        }
    }

    @Test
    void adminValidationFailsClosedOnPartialRegistration() {
        CoreOwnerContextManager ownerContexts = manager();
        CoreModuleDefinition admin = admin();
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            child.getBeanFactory().registerSingleton("coreOwnerContextManager", ownerContexts);
            child.getBeanFactory().registerSingleton(
                    "coreLocalIdentityQueryAdapter",
                    new CoreLocalIdentityQueryAdapter(ownerContexts));

            assertThatThrownBy(() -> CoreLocalContractAssembly.validate(child, admin))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing coreLocalAuthorizationMutationAdapter")
                    .hasMessageContaining("missing coreLocalAccountQueryAdapter");
        } finally {
            child.close();
        }
    }

    @Test
    void adminRegistrationRejectsOccupiedSlotWithoutPartialAssembly() {
        CoreOwnerContextManager ownerContexts = manager();
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            child.getBeanFactory().registerSingleton("coreLocalAccountQueryAdapter", new Object());

            assertThatThrownBy(() -> CoreLocalContractAssembly.register(child, admin(), ownerContexts))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("coreLocalAccountQueryAdapter");

            assertThat(child.getBeanFactory().containsSingleton("coreOwnerContextManager")).isFalse();
            assertThat(child.getBeanFactory().containsSingleton("coreLocalIdentityQueryAdapter"))
                    .isFalse();
            assertThat(child.getBeanFactory().containsSingleton("coreLocalAuthorizationMutationAdapter"))
                    .isFalse();
        } finally {
            child.close();
        }
    }

    @Test
    void childCloseDoesNotStopParentOwnerManager() {
        CoreModuleDefinition enabledAuth = new CoreModuleDefinition(
                "auth", "AUTH", CoreOwnerBootConfigurations.Auth.class,
                "authTransactionManager", "backend-auth", true);
        CoreOwnerContextManager ownerContexts = new CoreOwnerContextManager(
                new CoreModuleRegistry(java.util.List.of(enabledAuth)),
                new MockEnvironment(),
                true,
                1_000L);
        ApplicationContext parent = mock(ApplicationContext.class);
        ownerContexts.setApplicationContext(parent);
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            CoreLocalContractAssembly.register(child, admin(), ownerContexts);
            child.refresh();
            CoreLocalContractAssembly.validate(child, admin());
        } finally {
            child.close();
        }

        try {
            assertThat(ownerContexts.states())
                    .containsEntry("auth", CoreOwnerContextManager.State.STARTING);
        } finally {
            ownerContexts.onContextClosed(new ContextClosedEvent(parent));
        }
    }

    private static CoreOwnerContextManager manager() {
        return spy(new CoreOwnerContextManager(
                new CoreModuleRegistry(java.util.List.of()),
                new MockEnvironment(),
                false,
                1_000L));
    }

    private static CoreModuleDefinition admin() {
        return new CoreModuleDefinition(
                "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                "adminTransactionManager", "backend-admin");
    }
}
