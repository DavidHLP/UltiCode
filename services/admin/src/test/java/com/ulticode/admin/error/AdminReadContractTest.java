package com.ulticode.admin.error;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
    void requirePageRejectsMalformedOrUnavailablePagesWithTheOwnerError() {
        PageResult<String> missingTotal = PageResult.of(List.of(), 0L, 1, 10);
        missingTotal.setTotal(null);
        PageResult<String> unavailable = PageResult.of(List.of(), 0L, 1, 10);
        unavailable.setDegradationStatus(DegradationStatus.UNAVAILABLE);
        List<PageResult<String>> invalidPages = Arrays.asList(
                null,
                PageResult.of(null, 0L, 1, 10),
                PageResult.of(Arrays.asList("item", null), 2L, 1, 10),
                missingTotal,
                PageResult.of(List.of(), -1L, 1, 10),
                unavailable);

        for (String owner : List.of("App problem", "App submission", "Auth")) {
            for (PageResult<String> page : invalidPages) {
                assertThatThrownBy(() -> AdminReadContract.requirePage(page, owner))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage(owner + " owner query unavailable")
                        .hasNoCause()
                        .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
            }
        }
    }

    @Test
    void requirePageAcceptsHealthyAndPartialPagesWithoutChangingTheirValues() {
        for (List<String> items : List.of(List.<String>of(), List.of("item"))) {
            for (DegradationStatus status : Arrays.asList(
                    null, DegradationStatus.OK, DegradationStatus.PARTIAL)) {
                PageResult<String> page = PageResult.of(items, (long) items.size(), 2, 10);
                page.setDegradationStatus(status);
                PageResult<String> expected = PageResult.of(items, (long) items.size(), 2, 10);
                expected.setDegradationStatus(status);

                AdminReadContract.requirePage(page, "App problem");

                assertThat(page).isEqualTo(expected);
                assertThat(page.getItems()).isSameAs(items);
            }
        }
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
