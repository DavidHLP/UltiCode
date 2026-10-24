package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.rpc.RpcResult;

final class AdminCommandValidation {

    private AdminCommandValidation() {
    }

    static <T> RpcResult<T> rejectIfNotAdmin(WriteCommand command, AdminActorAuthorizer actorAuthorizer) {
        if (command == null) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, null);
        }
        ActorDelegation actor = command.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (!"ADMIN".equalsIgnoreCase(actor.actorType())
                && !"SUPER_ADMIN".equalsIgnoreCase(actor.actorType()))) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        if (!actorAuthorizer.isAuthorized(actor)) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        return null;
    }

    static String traceId(WriteCommand command) {
        return command == null || command.trace() == null ? null : command.trace().traceId();
    }
}
