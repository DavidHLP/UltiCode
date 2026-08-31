package com.ulticode.common.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RPC total resilience budgets")
class RpcPolicyBudgetTest {

    @Test
    void totalBudgetsAndFailureControlsStayBounded() {
        assertThat(RpcPolicy.QUERY_TOTAL_BUDGET_MS)
                .isEqualTo(RpcPolicy.QUERY_TIMEOUT_MS * (RpcPolicy.QUERY_RETRIES + 1));
        assertThat(RpcPolicy.WRITE_TOTAL_BUDGET_MS).isEqualTo(RpcPolicy.WRITE_TIMEOUT_MS);
        assertThat(RpcPolicy.EXECUTION_TOTAL_BUDGET_MS).isEqualTo(RpcPolicy.EXECUTION_TIMEOUT_MS);
        assertThat(RpcPolicy.MAX_CONCURRENT_CALLS).isBetween(1, 64);
        assertThat(RpcPolicy.CIRCUIT_FAILURE_THRESHOLD).isBetween(2, 20);
        assertThat(RpcPolicy.CIRCUIT_OPEN_MS).isBetween(1_000L, 60_000L);
    }
}
