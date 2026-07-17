package com.ulticode.modules.moderation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.CreateAppealDTO;
import com.ulticode.modules.moderation.dto.CreateReportDTO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.PerformModerationActionDTO;
import com.ulticode.modules.moderation.entity.Appeal;
import com.ulticode.modules.moderation.entity.ModerationAction;
import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.Report;
import com.ulticode.modules.moderation.entity.UserBan;
import com.ulticode.modules.moderation.entity.UserWarning;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import com.ulticode.modules.moderation.mapper.AppealMapper;
import com.ulticode.modules.moderation.mapper.ModerationActionMapper;
import com.ulticode.modules.moderation.mapper.ModerationQueueMapper;
import com.ulticode.modules.moderation.mapper.ReportMapper;
import com.ulticode.modules.moderation.mapper.UserBanMapper;
import com.ulticode.modules.moderation.mapper.UserWarningMapper;
import com.ulticode.modules.moderation.port.ContentModerationPort;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * State-machine unit test for the absorbed moderation write module. Mocks only the mappers,
 * projection, and content port — the read concern lives on {@link ModerationProjection} and is not
 * exercised here. Locks the guards (author, appealable state, duplicate report, appeal
 * authorization) and the queue / appeal transitions.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceImplTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-17T10:15:30Z");
  private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

  @Mock private ModerationQueueMapper queueMapper;
  @Mock private ModerationActionMapper actionMapper;
  @Mock private ReportMapper reportMapper;
  @Mock private AppealMapper appealMapper;
  @Mock private UserWarningMapper warningMapper;
  @Mock private UserBanMapper banMapper;
  @Mock private UserMapper userMapper;
  @Mock private ContentModerationPort contentModerationPort;
  @Mock private ModerationProjection moderationProjection;
  @Mock private CurrentUserProvider currentUserProvider;

  private ModerationServiceImpl service() {
    return new ModerationServiceImpl(
        queueMapper,
        actionMapper,
        reportMapper,
        appealMapper,
        warningMapper,
        banMapper,
        userMapper,
        contentModerationPort,
        moderationProjection,
        CLOCK,
        currentUserProvider);
  }

  // ----- createAppeal guards + transition -----

  @Test
  void createAppealRejectsNonAuthor() {
    when(queueMapper.selectById("q-1")).thenReturn(queue("q-1", "author-1", "RESOLVED"));

    assertThatThrownBy(() -> service().createAppeal(appealDto("q-1", "other-user"), "other-user"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODERATION_NOT_AUTHOR);

    verify(appealMapper, never()).insert(any(Appeal.class));
    verify(queueMapper, never()).updateById(any(ModerationQueue.class));
  }

  @Test
  void createAppealRejectsNonResolvedQueue() {
    when(queueMapper.selectById("q-1")).thenReturn(queue("q-1", "author-1", "PENDING"));

    assertThatThrownBy(() -> service().createAppeal(appealDto("q-1", "author-1"), "author-1"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODERATION_CANNOT_APPEAL);

    verify(appealMapper, never()).insert(any(Appeal.class));
  }

  @Test
  void createAppealFlipsQueueToAppealPending() {
    when(queueMapper.selectById("q-1")).thenReturn(queue("q-1", "author-1", "RESOLVED"));
    when(moderationProjection.toAppealVO(any())).thenReturn(new AppealVO());

    service().createAppeal(appealDto("q-1", "author-1"), "author-1");

    ArgumentCaptor<ModerationQueue> captor = ArgumentCaptor.forClass(ModerationQueue.class);
    verify(queueMapper).updateById(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("APPEAL_PENDING");
    verify(appealMapper).insert(any(Appeal.class));
  }

  // ----- createReport intake -----

  @Test
  void createReportCreatesQueueAndLinksReport() {
    CreateReportDTO dto = reportDto("FORUM_POST", "post-1", "SPAM");
    when(contentModerationPort.resolveAuthorId("FORUM_POST", "post-1")).thenReturn("author-1");
    when(queueMapper.findByEntity("FORUM_POST", "post-1")).thenReturn(null);

    service().createReport(dto, "reporter-1");

    ArgumentCaptor<ModerationQueue> queueCaptor = ArgumentCaptor.forClass(ModerationQueue.class);
    verify(queueMapper).insert(queueCaptor.capture());
    ModerationQueue created = queueCaptor.getValue();
    assertThat(created.getStatus()).isEqualTo("PENDING");
    assertThat(created.getReportCount()).isEqualTo(1);
    assertThat(created.getAuthorId()).isEqualTo("author-1");
    verify(reportMapper).insert(any(Report.class));
    verify(reportMapper).updateById(any(Report.class));
  }

  @Test
  void createReportRejectsDuplicate() {
    CreateReportDTO dto = reportDto("FORUM_POST", "post-1", "SPAM");
    doThrow(new DuplicateKeyException("dup")).when(reportMapper).insert(any(Report.class));

    assertThatThrownBy(() -> service().createReport(dto, "reporter-1"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODERATION_ALREADY_REPORTED);

    verify(queueMapper, never()).insert(any(ModerationQueue.class));
  }

  // ----- getAppeal authorization -----

  @Test
  void getAppealReturnsForbiddenForNonOwnerNonModerator() {
    Appeal appeal = new Appeal();
    appeal.setId("a-1");
    appeal.setAppellantId("owner-1");
    when(appealMapper.selectById("a-1")).thenReturn(appeal);
    when(currentUserProvider.hasRole(anyString())).thenReturn(false);

    assertThatThrownBy(() -> service().getAppeal("a-1", "stranger"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  void getAppealAllowsOwner() {
    Appeal appeal = new Appeal();
    appeal.setId("a-1");
    appeal.setAppellantId("owner-1");
    when(appealMapper.selectById("a-1")).thenReturn(appeal);
    AppealVO vo = new AppealVO();
    when(moderationProjection.toAppealVO(appeal)).thenReturn(vo);

    assertThat(service().getAppeal("a-1", "owner-1")).isSameAs(vo);
  }

  @Test
  void getAppealNotFound() {
    when(appealMapper.selectById("missing")).thenReturn(null);

    assertThatThrownBy(() -> service().getAppeal("missing", "owner-1"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODERATION_APPEAL_NOT_FOUND);
  }

  // ----- performAction strategy dispatch -----

  @Test
  void performActionWithWarnWritesWarningAndResolvesReports() {
    when(queueMapper.selectById("q-1")).thenReturn(queueItem("q-1"));
    when(moderationProjection.queueItemById("q-1")).thenReturn(new ModerationQueueVO());

    PerformModerationActionDTO dto = new PerformModerationActionDTO();
    dto.setAction(ModerationActionType.WARNED);
    dto.setNote("spam warning");

    service().performAction("q-1", dto, "mod-1");

    verify(actionMapper).insert(any(ModerationAction.class));
    verify(warningMapper).insert(any(UserWarning.class));
    verify(banMapper, never()).insert(any(UserBan.class));
    verify(queueMapper).updateById(any(ModerationQueue.class));
    verify(reportMapper).updateStatusByQueueId(eq("q-1"), eq("RESOLVED"));
  }

  @Test
  void performActionRejectsMissingQueue() {
    when(queueMapper.selectById("missing")).thenReturn(null);

    PerformModerationActionDTO dto = new PerformModerationActionDTO();
    dto.setAction(ModerationActionType.WARNED);

    assertThatThrownBy(() -> service().performAction("missing", dto, "mod-1"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODERATION_QUEUE_NOT_FOUND);

    verify(actionMapper, never()).insert(any(ModerationAction.class));
  }

  @Test
  void performActionWithHideFlagsContent() {
    when(queueMapper.selectById("q-1")).thenReturn(queueItem("q-1"));
    when(moderationProjection.queueItemById("q-1")).thenReturn(new ModerationQueueVO());

    PerformModerationActionDTO dto = new PerformModerationActionDTO();
    dto.setAction(ModerationActionType.HIDDEN);
    dto.setNote("hide note");

    service().performAction("q-1", dto, "mod-1");

    verify(contentModerationPort)
        .updateFlagStatus(eq("FORUM_POST"), eq("post-1"), eq(true), eq("hide note"));
    verify(warningMapper, never()).insert(any(UserWarning.class));
  }

  // ----- fixtures -----

  private static ModerationQueue queue(String id, String authorId, String status) {
    ModerationQueue q = new ModerationQueue();
    q.setId(id);
    q.setAuthorId(authorId);
    q.setStatus(status);
    return q;
  }

  private static ModerationQueue queueItem(String id) {
    ModerationQueue q = new ModerationQueue();
    q.setId(id);
    q.setEntityType("FORUM_POST");
    q.setEntityId("post-1");
    q.setAuthorId("author-1");
    q.setPrimaryCategory("SPAM");
    return q;
  }

  private static CreateAppealDTO appealDto(String queueId, String appellantId) {
    CreateAppealDTO dto = new CreateAppealDTO();
    dto.setQueueId(queueId);
    dto.setReason("reason");
    // appellantId is a separate createAppeal parameter (sourced from the principal), not a DTO field
    return dto;
  }

  private static CreateReportDTO reportDto(String entityType, String entityId, String category) {
    CreateReportDTO dto = new CreateReportDTO();
    dto.setEntityType(entityType);
    dto.setEntityId(entityId);
    dto.setCategory(category);
    dto.setReason("reason");
    return dto;
  }
}
