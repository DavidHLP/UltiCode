package com.ulticode.core;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

/** Explicit local contract wiring for enabled Core Owner child contexts. */
final class CoreLocalContractAssembly {
    private static final String ADMIN_MODULE = "admin";
    static final String LOCAL_CONTRACTS_ENABLED_PROPERTY = "core.local-contracts.enabled";
    private static final String OWNER_CONTEXT_MANAGER = "coreOwnerContextManager";
    private static final String IDENTITY_QUERY_ADAPTER = "coreLocalIdentityQueryAdapter";
    private static final String AUTHORIZATION_MUTATION_ADAPTER =
            "coreLocalAuthorizationMutationAdapter";
    private static final String ACCOUNT_QUERY_ADAPTER = "coreLocalAccountQueryAdapter";

    private CoreLocalContractAssembly() {
    }

    static void register(
            ConfigurableApplicationContext child,
            CoreModuleDefinition module,
            CoreOwnerContextManager ownerContexts) {
        if (!ADMIN_MODULE.equals(module.name())) {
            return;
        }

        ConfigurableListableBeanFactory beanFactory = child.getBeanFactory();
        List<String> occupied = occupiedNames(beanFactory);
        if (!occupied.isEmpty()) {
            throw new IllegalStateException(
                    "Core local contract assembly already contains: " + String.join(", ", occupied));
        }

        CoreLocalIdentityQueryAdapter identityAdapter =
                new CoreLocalIdentityQueryAdapter(ownerContexts);
        CoreLocalAuthorizationMutationAdapter authorizationAdapter =
                new CoreLocalAuthorizationMutationAdapter(ownerContexts);
        CoreLocalAccountQueryAdapter accountAdapter =
                new CoreLocalAccountQueryAdapter(ownerContexts);

        beanFactory.registerSingleton(OWNER_CONTEXT_MANAGER, ownerContexts);
        beanFactory.registerSingleton(IDENTITY_QUERY_ADAPTER, identityAdapter);
        beanFactory.registerSingleton(AUTHORIZATION_MUTATION_ADAPTER, authorizationAdapter);
        beanFactory.registerSingleton(ACCOUNT_QUERY_ADAPTER, accountAdapter);
    }

    static void validate(
            ConfigurableApplicationContext child,
            CoreModuleDefinition module) {
        if (!ADMIN_MODULE.equals(module.name())) {
            return;
        }

        ConfigurableListableBeanFactory beanFactory = child.getBeanFactory();
        List<String> failures = new ArrayList<>();
        requireSingleton(
                beanFactory, OWNER_CONTEXT_MANAGER, CoreOwnerContextManager.class, failures);
        requireSingleton(
                beanFactory, IDENTITY_QUERY_ADAPTER, CoreLocalIdentityQueryAdapter.class, failures);
        requireSingleton(
                beanFactory,
                AUTHORIZATION_MUTATION_ADAPTER,
                CoreLocalAuthorizationMutationAdapter.class,
                failures);
        requireSingleton(
                beanFactory, ACCOUNT_QUERY_ADAPTER, CoreLocalAccountQueryAdapter.class, failures);
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Core local contract assembly is incomplete for admin: "
                            + String.join(", ", failures));
        }
    }

    private static List<String> occupiedNames(ConfigurableListableBeanFactory beanFactory) {
        List<String> occupied = new ArrayList<>();
        addIfOccupied(beanFactory, OWNER_CONTEXT_MANAGER, occupied);
        addIfOccupied(beanFactory, IDENTITY_QUERY_ADAPTER, occupied);
        addIfOccupied(beanFactory, AUTHORIZATION_MUTATION_ADAPTER, occupied);
        addIfOccupied(beanFactory, ACCOUNT_QUERY_ADAPTER, occupied);
        return occupied;
    }

    private static void addIfOccupied(
            ConfigurableListableBeanFactory beanFactory,
            String name,
            List<String> occupied) {
        if (beanFactory.containsSingleton(name) || beanFactory.containsBeanDefinition(name)) {
            occupied.add(name);
        }
    }

    private static void requireSingleton(
            ConfigurableListableBeanFactory beanFactory,
            String name,
            Class<?> expectedType,
            List<String> failures) {
        if (!beanFactory.containsSingleton(name)) {
            failures.add("missing " + name);
            return;
        }
        Object singleton = beanFactory.getSingleton(name);
        if (!expectedType.isInstance(singleton)) {
            failures.add("invalid " + name);
        }
    }
}
