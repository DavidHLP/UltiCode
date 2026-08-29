package com.ulticode.modules.admin.service.handler;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import com.ulticode.modules.admin.dto.tag.MergeTagDTO;
import com.ulticode.modules.admin.dto.tag.TagListResponse;
import com.ulticode.modules.admin.dto.tag.TagTypes;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.dto.tag.UpdateTagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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
        ForumTagDTO result = mutate(new ForumTagMutationCommand(
                commandId(), idempotency(), actor("forum tag create"),
                currentTrace(),
                ForumTagMutationCommand.Action.CREATE,
                null, null, null,
                dto.getName(), slug, dto.getDescription(), dto.getColor()));
        return toTagVO(result);
    }

    @Override
    public TagVO update(String id, UpdateTagDTO dto) {
        ForumTagDTO result = mutate(new ForumTagMutationCommand(
                commandId(), idempotency(), actor("forum tag update"),
                currentTrace(),
                ForumTagMutationCommand.Action.UPDATE,
                id, null, null,
                dto.getName(), dto.getSlug(), dto.getDescription(), dto.getColor()));
        return toTagVO(result);
    }

    @Override
    public void delete(String id) {
        mutate(new ForumTagMutationCommand(
                commandId(), idempotency(), actor("forum tag delete"),
                currentTrace(),
                ForumTagMutationCommand.Action.DELETE,
                id, null, null, null, null, null, null));
    }

    @Override
    public void merge(MergeTagDTO dto) {
        mutate(new ForumTagMutationCommand(
                commandId(), idempotency(), actor("forum tag merge"),
                currentTrace(),
                ForumTagMutationCommand.Action.MERGE,
                null, dto.getSourceId(), dto.getTargetTagId(),
                null, null, null, null));
    }

    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    private ForumTagDTO mutate(ForumTagMutationCommand command) {
        RpcResult<ForumTagDTO> result = forumTagAdministrationService.mutate(command);
        if (result == null || !result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    private static BusinessException mapError(RpcResult<?> result) {
        if (result == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC result is null (transport failure)");
        }
        var err = result.error();
        if (err == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }
        return switch (err.code()) {
            case 40000 -> new BusinessException(AdminErrorCode.BAD_REQUEST, err.message());
            case 40100 -> new BusinessException(AdminErrorCode.UNAUTHORIZED, err.message());
            case 40300 -> new BusinessException(AdminErrorCode.FORBIDDEN, err.message());
            case 40401 -> new BusinessException(AdminErrorCode.FORUM_TAG_NOT_FOUND, err.message());
            case 40904 -> new BusinessException(AdminErrorCode.FORUM_TAG_NAME_EXISTS, err.message());
            case 40905 -> new BusinessException(AdminErrorCode.FORUM_TAG_SLUG_EXISTS, err.message());
            default -> new BusinessException(AdminErrorCode.UNKNOWN_ERROR, err.message());
        };
    }

    private static String commandId() {
        return UUID.randomUUID().toString();
    }

    private static IdMetadata idempotency() {
        return IdMetadata.mint();
    }

    private ActorDelegation actor(String rationale) {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return new ActorDelegation(AdminActors.typeOf(currentUserProvider), actorId, actorId, rationale);
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
