package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.port.adapter.OwnerCutoverGate;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestCutoverService")
class ContestCutoverServiceTest {

    @Mock private AdminContestProjection adminContestProjection;
    @Mock private CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("DENY decision fails closed before any remote call")
    void denyDecisionFailsTheWriteClosed() {
        ContestCutoverService service = new ContestCutoverService(
                adminContestProjection, currentUserProvider,
                new OwnerCutoverGate("contest", null, false, false,
                        OwnerCutoverGate.Policy.FAIL_CLOSED));

        assertThatThrownBy(() -> service.startContest("contest-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.CONFLICT);
    }
}
