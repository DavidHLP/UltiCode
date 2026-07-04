package com.ulticode.modules.notification.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.notification.dto.NotificationPreferenceVO;
import com.ulticode.modules.notification.dto.UpdateNotificationDTO;
import com.ulticode.modules.notification.dto.UpdateNotificationPreferenceDTO;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.NotificationPreference;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.notification.port.NotificationPushPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests covering the P0 fixes in
 * {@link NotificationServiceImpl}: cross-user ownership, Q6 readAt clearing,
 * Q18/Q19 preferences defaults, and soft-delete SQL filter contract.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOwnershipTest {

    private static final String OWNER_ID = "user-owner";
    private static final String ATTACKER_ID = "user-attacker";
    private static final String NOTIF_ID = "notif-1";

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationPreferenceMapper preferenceMapper;
    @Mock
    private NotificationPushPort notificationPushPort;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationMapper, preferenceMapper, notificationPushPort);
    }

    private Notification seedNotification(String ownerId) {
        Notification n = new Notification();
        n.setId(NOTIF_ID);
        n.setUserId(ownerId);
        n.setType("COMMENT");
        n.setCategory("COMMUNICATION");
        n.setTitle("t");
        n.setBody("b");
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    // ===== Q7: cross-user PATCH/DELETE returns FORBIDDEN =====

    @Test
    @DisplayName("Q7: PATCH another user's notification throws FORBIDDEN")
    void updateNotification_otherUser_throwsForbidden() {
        when(notificationMapper.selectById(NOTIF_ID)).thenReturn(seedNotification(OWNER_ID));

        UpdateNotificationDTO dto = new UpdateNotificationDTO();
        dto.setIsRead(true);

        assertThatThrownBy(() -> service.updateNotification(ATTACKER_ID, NOTIF_ID, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        // No update should have been issued.
        verify(notificationMapper, never()).updateById((Notification) any());
    }

    @Test
    @DisplayName("Q7: DELETE another user's notification throws FORBIDDEN")
    void deleteNotification_otherUser_throwsForbidden() {
        when(notificationMapper.selectById(NOTIF_ID)).thenReturn(seedNotification(OWNER_ID));

        assertThatThrownBy(() -> service.deleteNotification(ATTACKER_ID, NOTIF_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(notificationMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    // ===== Q6: isRead=false clears readAt =====

    @Test
    @DisplayName("Q6: PATCH isRead=true on unread notification sets readAt")
    void updateNotification_markRead_setsReadAt() {
        Notification n = seedNotification(OWNER_ID);
        n.setIsRead(false);
        n.setReadAt(null);
        when(notificationMapper.selectById(NOTIF_ID)).thenReturn(n);

        UpdateNotificationDTO dto = new UpdateNotificationDTO();
        dto.setIsRead(true);

        service.updateNotification(OWNER_ID, NOTIF_ID, dto);

        assertThat(n.getIsRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationMapper).updateById(n);
    }

    @Test
    @DisplayName("Q6: PATCH isRead=false on read notification clears readAt")
    void updateNotification_unmark_clearsReadAt() {
        Notification n = seedNotification(OWNER_ID);
        n.setIsRead(true);
        n.setReadAt(LocalDateTime.now());
        when(notificationMapper.selectById(NOTIF_ID)).thenReturn(n);

        UpdateNotificationDTO dto = new UpdateNotificationDTO();
        dto.setIsRead(false);

        service.updateNotification(OWNER_ID, NOTIF_ID, dto);

        assertThat(n.getIsRead()).isFalse();
        assertThat(n.getReadAt()).isNull();
        verify(notificationMapper).updateById(n);
    }

    // ===== Q18/Q19: preferences defaults without row creation =====

    @Test
    @DisplayName("Q18/Q19: GET preferences with no row returns DDL defaults (no INSERT)")
    void getPreferences_noRow_returnsDefaults() {
        when(preferenceMapper.findByUserId(OWNER_ID)).thenReturn(Optional.empty());

        NotificationPreferenceVO vo = service.getPreferences(OWNER_ID);

        assertThat(vo.getCommunication()).isTrue();
        assertThat(vo.getMarketing()).isFalse();
        assertThat(vo.getSecurity()).isTrue();
        assertThat(vo.getSystemEnabled()).isTrue();
        verify(preferenceMapper, never()).insert((NotificationPreference) any());
    }

    @Test
    @DisplayName("Q18/Q19: PATCH preferences with no existing row creates one with provided + defaults")
    void updatePreferences_noRow_createsThenPatches() {
        when(preferenceMapper.findByUserId(OWNER_ID)).thenReturn(Optional.empty());
        when(preferenceMapper.insert(any(NotificationPreference.class))).thenAnswer(inv -> {
            NotificationPreference arg = inv.getArgument(0);
            // Mirror MyBatis-Plus ASSIGN_UUID behavior so the second insert in
            // updatePreferences is correctly skipped (preference.getId() != null).
            if (arg.getId() == null) {
                arg.setId("uuid-mocked");
            }
            return 1;
        });

        UpdateNotificationPreferenceDTO dto = new UpdateNotificationPreferenceDTO();
        dto.setCommunication(false);

        service.updatePreferences(OWNER_ID, dto);

        // Verify INSERT was called (new row created) with merged values.
        var captor = org.mockito.ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceMapper).insert(captor.capture());
        NotificationPreference saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(OWNER_ID);
        assertThat(saved.getCommunication()).isFalse();    // overridden
        assertThat(saved.getMarketing()).isFalse();        // default
        assertThat(saved.getSecurity()).isTrue();          // default
        assertThat(saved.getSystemEnabled()).isTrue();     // default
    }

    @Test
    @DisplayName("Q21: PATCH preferences with empty body leaves existing values intact (no-op)")
    void updatePreferences_emptyBody_preservesExistingValues() {
        NotificationPreference existing = new NotificationPreference();
        existing.setId("pref-1");
        existing.setUserId(OWNER_ID);
        existing.setCommunication(true);
        existing.setMarketing(false);
        existing.setSecurity(true);
        existing.setSystemEnabled(true);
        when(preferenceMapper.findByUserId(OWNER_ID)).thenReturn(Optional.of(existing));

        UpdateNotificationPreferenceDTO dto = new UpdateNotificationPreferenceDTO(); // all nulls

        service.updatePreferences(OWNER_ID, dto);

        verify(preferenceMapper).updateById(existing);
        verify(preferenceMapper, never()).insert((NotificationPreference) any());
        // Existing values preserved.
        assertThat(existing.getCommunication()).isTrue();
        assertThat(existing.getMarketing()).isFalse();
    }
}
