package com.ulticode.notification.dubbo.provider;

import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.modules.notification.mapper.NotificationReconciliationReadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationReconciliationReadProviderTest {

    @Mock
    private NotificationReconciliationReadMapper mapper;

    @Test
    void forwardsFullAndIncrementalBoundedQueries() {
        NotificationReconciliationReadProvider provider =
                new NotificationReconciliationReadProvider(mapper);
        List<NotificationUserReferenceCountDTO> facts = List.of(
                new NotificationUserReferenceCountDTO("user-1", 2),
                new NotificationUserReferenceCountDTO("user-2", 1));
        LocalDateTime since = LocalDateTime.of(2026, 8, 30, 0, 0);
        when(mapper.findUserReferenceCounts("", since, NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(facts);

        assertThat(provider.findUserReferenceCounts(
                "", since, NotificationReconciliationReadPort.MAX_PAGE_SIZE))
                .containsExactlyElementsOf(facts);
        verify(mapper).findUserReferenceCounts(
                "", since, NotificationReconciliationReadPort.MAX_PAGE_SIZE);
    }

    @Test
    void rejectsInvalidPageAndFacts() {
        NotificationReconciliationReadProvider provider =
                new NotificationReconciliationReadProvider(mapper);

        assertThatThrownBy(() -> provider.findUserReferenceCounts(null, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.findUserReferenceCounts(" ", null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.findUserReferenceCounts(
                "", null, NotificationReconciliationReadPort.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);

        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(null);
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);

        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(List.of(
                new NotificationUserReferenceCountDTO("user-2", 1),
                new NotificationUserReferenceCountDTO("user-1", 1)));
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOversizedAndNegativePages() {
        NotificationReconciliationReadProvider provider =
                new NotificationReconciliationReadProvider(mapper);
        List<NotificationUserReferenceCountDTO> oversized = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            oversized.add(new NotificationUserReferenceCountDTO("user-" + i, 1));
        }
        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(oversized);
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);

        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(List.of(
                new NotificationUserReferenceCountDTO("user-1", -1)));
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);
    }
}
