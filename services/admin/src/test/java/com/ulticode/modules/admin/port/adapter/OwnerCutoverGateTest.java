package com.ulticode.modules.admin.port.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Owner cutover decisions")
class OwnerCutoverGateTest {

    @Test
    @DisplayName("FAIL_CLOSED maps a disabled flag to DENY")
    void failClosedDeniesWhenDisabled() {
        assertThat(gate(OwnerCutoverGate.Policy.FAIL_CLOSED, true).decide())
                .isEqualTo(OwnerCutoverDecision.REMOTE);
        assertThat(gate(OwnerCutoverGate.Policy.FAIL_CLOSED, false).decide())
                .isEqualTo(OwnerCutoverDecision.DENY);
    }

    @Test
    @DisplayName("DELEGATE_LOCAL falls back to the local path when disabled")
    void delegateLocalFallsBackLocally() {
        assertThat(gate(OwnerCutoverGate.Policy.DELEGATE_LOCAL, true).decide())
                .isEqualTo(OwnerCutoverDecision.REMOTE);
        assertThat(gate(OwnerCutoverGate.Policy.DELEGATE_LOCAL, false).decide())
                .isEqualTo(OwnerCutoverDecision.LOCAL);
    }

    @Test
    @DisplayName("permanent-remote policies ignore the flag value")
    void permanentRemotePoliciesIgnoreTheFlag() {
        assertThat(gate(OwnerCutoverGate.Policy.ALWAYS_REMOTE, false).decide())
                .isEqualTo(OwnerCutoverDecision.REMOTE);
        assertThat(gate(OwnerCutoverGate.Policy.CUTOVER_REMOVED, false).decide())
                .isEqualTo(OwnerCutoverDecision.REMOTE);
    }

    private static OwnerCutoverGate gate(OwnerCutoverGate.Policy policy, boolean enabled) {
        return new OwnerCutoverGate("test", null, enabled, enabled, policy);
    }
}
