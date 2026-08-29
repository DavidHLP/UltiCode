package com.ulticode.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.common.security.DelegationAssertionContract;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DelegationAssertionConsumerFilterTest {

    @AfterEach
    void clearAttachment() {
        RpcContext.getClientAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void configuredSignerAssertionIsAttachedOnlyForDownstreamCall() throws Exception {
        DelegationAssertionSigner signer = mock(DelegationAssertionSigner.class);
        when(signer.issueForTarget("backend-app")).thenReturn("signed-assertion");
        Invoker<?> invoker = mock(Invoker.class);
        URL target = mock(URL.class);
        when(target.getParameter("application")).thenReturn("backend-app");
        when(invoker.getUrl()).thenReturn(target);
        Invocation invocation = mock(Invocation.class);
        when(invoker.invoke(any())).thenAnswer(ignored -> {
            assertThat(RpcContext.getClientAttachment().getAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY)).isEqualTo("signed-assertion");
            return mock(Result.class);
        });

        DelegationAssertionConsumerFilter filter = new DelegationAssertionConsumerFilter();
        filter.setDelegationAssertionSigner(signer);

        filter.invoke(invoker, invocation);

        assertThat(RpcContext.getClientAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY)).isNull();
    }
}
