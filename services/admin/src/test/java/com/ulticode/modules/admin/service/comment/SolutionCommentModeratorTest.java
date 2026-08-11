package com.ulticode.modules.admin.service.comment;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolutionCommentModeratorTest {

    @Mock
    private SolutionCommentReadPort solutionCommentReadPort;

    @Mock
    private SolutionCommentOwnerPort solutionCommentOwnerPort;

    @Mock
    private AdminCommentReadPort adminCommentReadPort;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private SolutionCommentModerator moderator;

    @BeforeEach
    void setUp() {
        moderator = new SolutionCommentModerator(
                solutionCommentReadPort, solutionCommentOwnerPort, adminCommentReadPort, currentUserProvider);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    @Test
    void deleteComment_requiresAuthenticatedActorBeforeRemoteWrite() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> moderator.deleteComment("comment-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.UNAUTHORIZED.code());

        verifyNoInteractions(solutionCommentOwnerPort);
    }

    @Test
    void deleteComment_forwardsActorAndCapturesOwnerAuditState() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(solutionCommentOwnerPort.deleteComment("comment-1", "admin-1"))
                .thenReturn(new SolutionCommentOwnerPort.DeleteResult("user-1", false));

        moderator.deleteComment("comment-1");

        verify(solutionCommentOwnerPort).deleteComment("comment-1", "admin-1");
        assertThat(AuditContext.getUserId()).isEqualTo("user-1");
        assertThat(AuditContext.getOldValues()).containsEntry("isDeleted", false);
        assertThat(AuditContext.getNewValues()).containsEntry("isDeleted", true);
    }
}
