package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.forum.port.ForumOwnerPort;
import com.ulticode.modules.forum.port.ForumOwnerPort.ToggleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumPostFieldToggleImplTest {

    @Mock
    private ForumOwnerPort forumOwnerPort;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private ForumPostFieldToggleImpl policy;

    @BeforeEach
    void setUp() {
        when(forumOwnerPort.setPinned("p1", true)).thenReturn(new ToggleResult("u1", false));
        when(forumOwnerPort.setPinned("p1", false)).thenReturn(new ToggleResult("u1", true));
        when(forumOwnerPort.setLocked("p1", true)).thenReturn(new ToggleResult("u1", false));
        when(forumOwnerPort.setLocked("p1", false)).thenReturn(new ToggleResult("u1", true));
    }

    @Test
    @DisplayName("toggle(PIN) delegates to ForumOwnerPort and writes audit")
    void toggle_pin_writesAuditAndPersists() {
        policy.toggle("p1", FieldToggle.PIN);

        verify(forumOwnerPort).setPinned("p1", true);
        verify(auditRecorder).recordForUser(
            eq("PIN_POST"),
            eq("FORUM_POST"),
            eq("p1"),
            eq("u1"),
            anyMap(),
            anyMap()
        );
    }

    @Test
    @DisplayName("toggle(LOCK) delegates to ForumOwnerPort and writes audit")
    void toggle_lock_writesAuditAndPersists() {
        policy.toggle("p1", FieldToggle.LOCK);

        verify(forumOwnerPort).setLocked("p1", true);
        verify(auditRecorder).recordForUser(
            eq("LOCK_POST"),
            eq("FORUM_POST"),
            eq("p1"),
            eq("u1"),
            anyMap(),
            anyMap()
        );
    }
}
