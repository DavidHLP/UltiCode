package com.ulticode.app.user.port;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultUserFactsReadProjectionWhitespaceCrTest {

    @Test
    void selectByIdsTrimsWhitespace() {
        UserProfileReadMapper profileMapper = mock(UserProfileReadMapper.class);
        DefaultUserFactsReadProjection adapter = new DefaultUserFactsReadProjection(profileMapper);
        AccountQueryService auth = mock(AccountQueryService.class);
        adapter.setAccountQueryService(auth);

        AuthAccountDTO account = new AuthAccountDTO("u-1", "alice", "a@test.com", "USER", true, false,
                null, null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), 1L);
        when(auth.getAccountsByIds(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            // auth trims internally, but we verify adapter already trimmed
            assertThat(arg.toString()).doesNotContain(" u-1 ");
            return new RpcResult<>(true, List.of(account), null, null, "t-1", null, null);
        });
        when(profileMapper.findByAccountIds(any())).thenReturn(List.of(UserProfileDTO.empty("u-1")));

        Map<String, UserSummaryView> result = adapter.selectByIds(java.util.Arrays.asList(" u-1 ", "  ", null));
        assertThat(result).hasSize(1);
    }

}
