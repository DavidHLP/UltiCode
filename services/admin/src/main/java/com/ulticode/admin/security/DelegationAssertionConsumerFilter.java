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
        String targetApplication = targetApplication(invoker);
        String assertion = null;
        if (signer != null) {
            assertion = isBootstrapInvocation(invocation)
                    ? signer.issueForBootstrap(targetApplication)
                    : signer.issueForTarget(targetApplication);
        }
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

    private static boolean isBootstrapInvocation(Invocation invocation) {
        if (invocation == null || invocation.getMethodName() == null) {
            return false;
        }
        boolean provisioningOperation = switch (invocation.getMethodName()) {
            case "createAccount", "updateCredentials", "resetPassword", "changeState" -> true;
            default -> false;
        };
        if (!provisioningOperation || invocation.getArguments() == null) {
            return false;
        }
        for (Object argument : invocation.getArguments()) {
            boolean provisioningCommand = argument instanceof com.ulticode.auth.api.command.CreateAccountCommand
                    || argument instanceof com.ulticode.auth.api.command.UpdateAccountCredentialsCommand
                    || argument instanceof com.ulticode.auth.api.command.ResetPasswordCommand
                    || argument instanceof com.ulticode.auth.api.command.ChangeAccountStateCommand;
            if (provisioningCommand && argument instanceof com.ulticode.auth.api.command.WriteCommand command) {
                com.ulticode.auth.api.command.ActorDelegation actor = command.actor();
                if (actor != null
                        && "BOOTSTRAP".equalsIgnoreCase(actor.actorType())
                        && "bootstrap".equals(actor.actorId())
                        && "bootstrap".equals(actor.delegatorId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String targetApplication(Invoker<?> invoker) {
        if (invoker == null || invoker.getUrl() == null) {
            return null;
        }
        String application = invoker.getUrl().getParameter("application");
        return application == null || application.isBlank() ? null : application.trim();
    }
}
