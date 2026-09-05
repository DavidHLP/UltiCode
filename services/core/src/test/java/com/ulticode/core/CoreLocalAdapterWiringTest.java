package com.ulticode.core;

import com.ulticode.admin.security.jwt.AccountReadAdapter;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class CoreLocalAdapterWiringTest {

    @Test
    void adminConsumerUsesLocalIdentityContractAndMutationContract() {
        CoreOwnerContextManager ownerContexts = spy(new CoreOwnerContextManager(
                new CoreModuleRegistry(java.util.List.of()),
                new MockEnvironment(),
                false,
                1_000L));
        IdentityQueryService authIdentity = mock(IdentityQueryService.class);
        doReturn(authIdentity).when(ownerContexts).bean("auth", IdentityQueryService.class);
        doReturn(RpcResult.success(
                new UserIdentityDTO("account-1", "alice", "ADMIN", true, false), "trace-1"))
                .when(authIdentity).getIdentity("account-1");

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        try {
            ownerContexts.registerChildContracts(child, new CoreModuleDefinition(
                    "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                    "adminTransactionManager", "backend-admin"));
            child.registerBean(AccountReadAdapter.class);
            child.refresh();

            assertThat(child.getBean(AuthorizationMutationService.class))
                    .isInstanceOf(CoreLocalAuthorizationMutationAdapter.class);
            assertThat(child.getBean(AccountReadAdapter.class).findById("account-1"))
                    .get()
                    .extracting(account -> account.id(), account -> account.username())
                    .containsExactly("account-1", "alice");
        } finally {
            child.close();
            ownerContexts.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
        }
    }
}
