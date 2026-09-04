package com.ulticode.common.security;

/**
 * Scoped assertion carrier for same-process Owner calls.
 *
 * <p>Local calls do not traverse Dubbo, so they cannot use an RPC attachment.
 * The value is still a signed assertion and is consumed only for the current
 * thread and scope; missing values remain fail closed in the Auth verifier.
 */
public final class LocalDelegationAssertionContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private LocalDelegationAssertionContext() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static Scope install(String assertion) {
        if (assertion == null || assertion.isBlank()) {
            throw new IllegalArgumentException("delegation assertion is required");
        }
        String previous = CURRENT.get();
        CURRENT.set(assertion);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
