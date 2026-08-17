package com.ulticode.admin.security;

import com.ulticode.common.security.DelegationAssertionContract;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/** Propagates a freshly signed Admin identity assertion on Dubbo calls. */
public class DelegationAssertionConsumerFilter implements Filter {

    private DelegationAssertionSigner signer;

    public DelegationAssertionConsumerFilter() {
    }

    /** Injected by Dubbo's Spring extension injector after SPI construction. */
    public void setDelegationAssertionSigner(DelegationAssertionSigner signer) {
        this.signer = signer;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String assertion = signer == null ? null : signer.issueForCurrentUser();
        if (assertion != null) {
            RpcContext.getClientAttachment().setAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY, assertion);
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            RpcContext.getClientAttachment().removeAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY);
        }
    }
}
