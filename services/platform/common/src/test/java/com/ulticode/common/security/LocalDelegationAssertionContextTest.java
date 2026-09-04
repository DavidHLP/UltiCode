package com.ulticode.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDelegationAssertionContextTest {

    @Test
    void restoresNestedAssertionAfterScopeCloses() {
        assertThat(LocalDelegationAssertionContext.current()).isNull();
        try (LocalDelegationAssertionContext.Scope outer =
                     LocalDelegationAssertionContext.install("outer")) {
            assertThat(LocalDelegationAssertionContext.current()).isEqualTo("outer");
            try (LocalDelegationAssertionContext.Scope inner =
                         LocalDelegationAssertionContext.install("inner")) {
                assertThat(LocalDelegationAssertionContext.current()).isEqualTo("inner");
            }
            assertThat(LocalDelegationAssertionContext.current()).isEqualTo("outer");
        }
        assertThat(LocalDelegationAssertionContext.current()).isNull();
    }

    @Test
    void rejectsBlankAssertion() {
        assertThatThrownBy(() -> LocalDelegationAssertionContext.install(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
