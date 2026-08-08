package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.app.api.service.ForumOwnerPort.FlagResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumFlagPolicyImplTest {

    @Mock
    private ForumOwnerPort forumOwnerPort;

    @Mock
    private AuditRecorder auditRecorder;

    private Clock clock = Clock.system(ZoneId.of("UTC"));

    private ForumFlagPolicyImpl policy;

    @BeforeEach
    void setUp() {
        policy = new ForumFlagPolicyImpl(forumOwnerPort, auditRecorder, clock);
        when(forumOwnerPort.flagPost(eq("p1"), any(), any())).thenReturn(new FlagResult("u1", false, ""));
        when(forumOwnerPort.unflagPost(eq("p1"))).thenReturn(new FlagResult("u1", true, "spam"));
    }

    @Test
    @DisplayName("flag delegates to ForumOwnerPort and writes audit with oldValues map")
    void flag_writesAuditAndDelegates() {
        policy.flag("p1", "Spam");

        verify(forumOwnerPort).flagPost(eq("p1"), eq("Spam"), any());
        verify(auditRecorder).recordForUser(
            eq("FLAG_POST"),
            eq("FORUM_POST"),
            eq("p1"),
            eq("u1"),
            eq(Map.of("isFlagged", false, "flaggedReason", "")),
            eq(Map.of("isFlagged", true, "flaggedReason", "Spam"))
        );
    }

    @Test
    @DisplayName("unflag delegates to ForumOwnerPort and writes audit with oldValues map")
    void unflag_clearsFieldsAndWritesAudit() {
        policy.unflag("p1");

        verify(forumOwnerPort).unflagPost("p1");
        verify(auditRecorder).recordForUser(
            eq("UNFLAG_POST"),
            eq("FORUM_POST"),
            eq("p1"),
            eq("u1"),
            eq(Map.of("isFlagged", true, "flaggedReason", "spam")),
            eq(Map.of("isFlagged", false, "flaggedReason", ""))
        );
    }
}
