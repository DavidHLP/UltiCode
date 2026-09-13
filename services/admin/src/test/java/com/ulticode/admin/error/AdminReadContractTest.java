package com.ulticode.admin.error;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminReadContractTest {

    private final CancellableQueryExecutor queryExecutor =
            new CancellableQueryExecutor("test-admin-read-contract", 2);

    @AfterEach
    void closeExecutor() {
        queryExecutor.close();
    }

    @Test
    void classifyMapsOwnerPermissionTableAndTransportFailures() {
        AdminReadContract.OwnerRead<String> success = AdminReadContract.classify(
                "Auth", RpcResult.success("value", "trace"));
        AdminReadContract.OwnerRead<String> unavailable = AdminReadContract.classify(
                "Auth", RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, "trace"));

        assertThat(success).isEqualTo(
                new AdminReadContract.OwnerRead<>(true, "value", null));
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.reason()).isEqualTo("Auth owner query unavailable");
        assertThatThrownBy(() -> AdminReadContract.classify(
                "App", RpcResult.failure(AppErrorCode.FORBIDDEN, "trace")))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AdminErrorCode.FORBIDDEN));
    }

    @Test
    void classifyTreatsAuthAccountBanAsPermissionFailure() {
        assertThatThrownBy(() -> AdminReadContract.classify(
                "Auth", RpcResult.failure(AuthErrorCode.ACCOUNT_BANNED, "trace")))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AdminErrorCode.FORBIDDEN));
    }

    @Test
    void awaitAndClassifyDegradesTransportPerCompletedQuery() {
        CancellableQueryExecutor.Query<String> failed = queryExecutor.submit(() -> {
            throw new IllegalStateException("provider down");
        });
        CancellableQueryExecutor.Query<String> healthy =
                queryExecutor.submit(() -> "healthy");
        healthy.result().join();

        List<AdminReadContract.OwnerRead<String>> reads = AdminReadContract.awaitAndClassify(
                queryExecutor,
                "App",
                1,
                TimeUnit.SECONDS,
                failed,
                healthy);

        assertThat(reads.get(0).available()).isFalse();
        assertThat(reads.get(1).value()).isEqualTo("healthy");
    }

    @Test
    void awaitAndClassifyPropagatesPermissionFromACompletedQuery() {
        CancellableQueryExecutor.Query<String> forbidden = queryExecutor.submit(() -> {
            throw new BusinessException(BaseErrorCode.FORBIDDEN);
        });
        CancellableQueryExecutor.Query<String> healthy =
                queryExecutor.submit(() -> "healthy");

        assertThatThrownBy(() -> AdminReadContract.<String>awaitAndClassify(
                queryExecutor,
                "Admin user detail",
                1,
                TimeUnit.SECONDS,
                forbidden,
                healthy))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AdminErrorCode.FORBIDDEN));
    }
}
