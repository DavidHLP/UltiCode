package com.ulticode.common.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-RPC-002: pins the RPC timeout / retry / idempotency policy values
 * so a future accidental change is caught at the contract level.
 *
 * <p>The values are compile-time constants used by
 * {@code @DubboReference(timeout = RpcPolicy.WRITE_TIMEOUT_MS, ...)} in
 * the P4-CUTOVER tasks. If these numbers change, the cutover wiring
 * silently picks up the new value &mdash; this test forces an explicit
 * review by failing.
 */
@DisplayName("RpcPolicy (§6.4 timeout/retry constants)")
class RpcPolicyTest {

    @Test
    @DisplayName("write timeout is 3000ms (upper bound of §6.4 '1-3s')")
    void writeTimeout() {
        assertThat(RpcPolicy.WRITE_TIMEOUT_MS).isEqualTo(3000);
    }

    @Test
    @DisplayName("write retries is 0 (§6.4: '写调用自动 retry=0')")
    void writeRetries() {
        assertThat(RpcPolicy.WRITE_RETRIES).isZero();
    }

    @Test
    @DisplayName("query timeout is 800ms (upper bound of §6.4 '300-800ms')")
    void queryTimeout() {
        assertThat(RpcPolicy.QUERY_TIMEOUT_MS).isEqualTo(800);
    }

    @Test
    @DisplayName("query retries is 1 (§6.4: '查询最多 1 次有抖动退避')")
    void queryRetries() {
        assertThat(RpcPolicy.QUERY_RETRIES).isEqualTo(1);
    }

    @Test
    @DisplayName("write timeout is strictly greater than query timeout")
    void writeTimeoutExceedsQuery() {
        assertThat(RpcPolicy.WRITE_TIMEOUT_MS)
                .as("write calls get more headroom than reads")
                .isGreaterThan(RpcPolicy.QUERY_TIMEOUT_MS);
    }
}
