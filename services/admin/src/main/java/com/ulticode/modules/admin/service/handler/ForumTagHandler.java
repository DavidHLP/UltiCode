package com.ulticode.modules.admin.service.handler;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import com.ulticode.modules.admin.dto.tag.MergeTagDTO;
import com.ulticode.modules.admin.dto.tag.TagListResponse;
import com.ulticode.modules.admin.dto.tag.TagTypes;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.dto.tag.UpdateTagDTO;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Forum-branch implementation of {@link TagDomainHandler}.
 *
 * <p>ADMIN-007: the forum module's entities/mappers are no longer on the
 * admin classpath. Reads go through {@link ForumTagReadPort}; writes go
 * through {@link ForumTagAdministrationService} — a Dubbo provider
 * carrying full command / idempotency / actor / trace metadata.
 * {@code RpcResult} failures are mapped explicitly onto
 * {@link AdminErrorCode} (missing tag &rarr;
 * {@code FORUM_TAG_NOT_FOUND}; name / slug conflicts &rarr;
 * {@code FORUM_TAG_NAME_EXISTS} / {@code FORUM_TAG_SLUG_EXISTS}).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class ForumTagHandler implements TagDomainHandler {

    private final ForumTagReadPort forumTagReadPort;
    private final ForumTagAdministrationService forumTagAdministrationService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String type() {
        return TagTypes.FORUM;
    }

    @Override
    public TagListResponse list(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        ForumTagPage page = forumTagReadPort.page(search, pageNum, pageSize, sortBy, sortOrder);
        List<TagVO> data = page.rows().stream().map(this::toTagVO).toList();
        return TagListResponse.of(data, page.total(), pageNum, pageSize);
    }

    @Override
    public TagVO getById(String id) {
        ForumTagRow tag = forumTagReadPort.getById(id);
        if (tag == null) {
            throw new BusinessException(AdminErrorCode.FORUM_TAG_NOT_FOUND);
        }
        return toTagVO(tag);
    }

    @Override
    public TagVO create(CreateTagDTO dto, String slug) {
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-tag-create", null, currentUserProvider.getCurrentUserId(),
                currentUserProvider, "forum tag create");
        ForumTagDTO result = mutate(new ForumTagMutationCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                ForumTagMutationCommand.Action.CREATE,
                null, null, null,
                dto.getName(), slug, dto.getDescription(), dto.getColor()));
        return toTagVO(result);
    }

    @Override
    public TagVO update(String id, UpdateTagDTO dto) {
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-tag-update", null, currentUserProvider.getCurrentUserId(),
                currentUserProvider, "forum tag update");
        ForumTagDTO result = mutate(new ForumTagMutationCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                ForumTagMutationCommand.Action.UPDATE,
                id, null, null,
                dto.getName(), dto.getSlug(), dto.getDescription(), dto.getColor()));
        return toTagVO(result);
    }

    @Override
    public void delete(String id) {
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-tag-delete", null, currentUserProvider.getCurrentUserId(),
                currentUserProvider, "forum tag delete");
        mutate(new ForumTagMutationCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                ForumTagMutationCommand.Action.DELETE,
                id, null, null, null, null, null, null));
    }

    @Override
    public void merge(MergeTagDTO dto) {
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-tag-merge", null, currentUserProvider.getCurrentUserId(),
                currentUserProvider, "forum tag merge");
        mutate(new ForumTagMutationCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                ForumTagMutationCommand.Action.MERGE,
                null, dto.getSourceId(), dto.getTargetTagId(),
                null, null, null, null));
    }

    private ForumTagDTO mutate(ForumTagMutationCommand command) {
        RpcResult<ForumTagDTO> result = forumTagAdministrationService.mutate(command);
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.FORUM_TAG, result);
        }
        return result.data();
    }

    private TagVO toTagVO(ForumTagRow tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.id());
        vo.setName(tag.name());
        vo.setSlug(tag.slug());
        vo.setDescription(tag.description());
        vo.setColor(tag.color());
        vo.setUsageCount(tag.usageCount());
        vo.setType(TagTypes.FORUM);
        vo.setCreatedAt(tag.createdAt());
        return vo;
    }

    private TagVO toTagVO(ForumTagDTO tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.id());
        vo.setName(tag.name());
        vo.setSlug(tag.slug());
        vo.setDescription(tag.description());
        vo.setColor(tag.color());
        vo.setUsageCount(tag.usageCount());
        vo.setType(TagTypes.FORUM);
        vo.setCreatedAt(tag.createdAt());
        return vo;
    }
}
