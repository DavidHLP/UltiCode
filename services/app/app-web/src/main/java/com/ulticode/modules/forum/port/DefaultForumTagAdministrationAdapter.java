package com.ulticode.modules.forum.port;

import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * ADMIN-007: app-side implementation of {@link ForumTagAdministrationService}
 * owning the {@code forum_tags} writes (create / update / delete / merge).
 *
 * <p>Name / slug conflict detection is race-free here (provider side) and
 * surfaces as {@code FORUM_TAG_NAME_CONFLICT} / {@code FORUM_TAG_SLUG_CONFLICT}
 * failures; missing tags surface as {@code CONTENT_NOT_FOUND}. The Admin
 * consumer maps these onto its own error codes.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultForumTagAdministrationAdapter implements ForumTagAdministrationService {

    private final ForumTagMapper forumTagMapper;

    @Override
    @Transactional
    public RpcResult<ForumTagDTO> mutate(ForumTagMutationCommand command) {
        String traceId = command.trace() != null ? command.trace().traceId() : null;
        try {
            switch (command.action()) {
                case CREATE -> {
                    if (!StringUtils.hasText(command.name()) || !StringUtils.hasText(command.slug())) {
                        return RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
                    }
                    if (forumTagMapper.existsByName(command.name())) {
                        return RpcResult.failure(AppErrorCode.FORUM_TAG_NAME_CONFLICT, traceId);
                    }
                    if (forumTagMapper.existsBySlug(command.slug())) {
                        return RpcResult.failure(AppErrorCode.FORUM_TAG_SLUG_CONFLICT, traceId);
                    }
                    ForumTag tag = new ForumTag();
                    tag.setName(command.name());
                    tag.setSlug(command.slug());
                    tag.setDescription(command.description());
                    tag.setColor(command.color());
                    tag.setUsageCount(0);
                    tag.setCreatedAt(LocalDateTime.now());
                    forumTagMapper.insert(tag);
                    log.info("Created forum tag {}", tag.getId());
                    return RpcResult.success(toDTO(tag), traceId);
                }
                case UPDATE -> {
                    ForumTag existing = forumTagMapper.selectByIdForUpdate(command.tagId());
                    if (existing == null) {
                        return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                    }
                    if (StringUtils.hasText(command.name()) && !command.name().equals(existing.getName())) {
                        if (forumTagMapper.existsByName(command.name())) {
                            return RpcResult.failure(AppErrorCode.FORUM_TAG_NAME_CONFLICT, traceId);
                        }
                        existing.setName(command.name());
                    }
                    if (StringUtils.hasText(command.slug()) && !command.slug().equals(existing.getSlug())) {
                        if (forumTagMapper.existsBySlug(command.slug())) {
                            return RpcResult.failure(AppErrorCode.FORUM_TAG_SLUG_CONFLICT, traceId);
                        }
                        existing.setSlug(command.slug());
                    }
                    if (command.description() != null) {
                        existing.setDescription(command.description());
                    }
                    if (command.color() != null) {
                        existing.setColor(command.color());
                    }
                    int updated = forumTagMapper.updateById(existing);
                    if (updated == 0) {
                        ForumTag current = forumTagMapper.selectByIdForUpdate(command.tagId());
                        if (current == null) {
                            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                        }
                        existing = current;
                    }
                    log.info("Updated forum tag {}", existing.getId());
                    return RpcResult.success(toDTO(existing), traceId);
                }
                case DELETE -> {
                    ForumTag existing = forumTagMapper.selectByIdForUpdate(command.tagId());
                    if (existing == null) {
                        return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                    }
                    int deleted = forumTagMapper.deleteById(command.tagId());
                    if (deleted == 0) {
                        return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                    }
                    log.info("Deleted forum tag {}", command.tagId());
                    return RpcResult.success(toDTO(existing), traceId);
                }
                case MERGE -> {
                    String sourceId = command.sourceTagId();
                    String targetId = command.targetTagId();
                    if (!StringUtils.hasText(sourceId) || !StringUtils.hasText(targetId)
                            || sourceId.equals(targetId)) {
                        return RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
                    }
                    String firstId = sourceId.compareTo(targetId) < 0 ? sourceId : targetId;
                    ForumTag first = forumTagMapper.selectByIdForUpdate(firstId);
                    ForumTag second = forumTagMapper.selectByIdForUpdate(
                            sourceId.equals(firstId) ? targetId : sourceId);
                    ForumTag source = sourceId.equals(firstId) ? first : second;
                    ForumTag target = targetId.equals(firstId) ? first : second;
                    if (source == null || target == null) {
                        return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                    }
                    int deleted = forumTagMapper.deleteById(sourceId);
                    if (deleted == 0) {
                        return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                    }
                    log.info("Merged forum tag {} into {}", sourceId, targetId);
                    return RpcResult.success(toDTO(target), traceId);
                }
                default -> throw new IllegalArgumentException("Unsupported action: " + command.action());
            }
        } catch (DuplicateKeyException exception) {
            String message = exception.getMostSpecificCause() != null
                    ? exception.getMostSpecificCause().getMessage() : exception.getMessage();
            if (message != null && message.toLowerCase().contains("slug")) {
                return RpcResult.failure(AppErrorCode.FORUM_TAG_SLUG_CONFLICT, traceId);
            }
            return RpcResult.failure(AppErrorCode.FORUM_TAG_NAME_CONFLICT, traceId);
        }
    }

    private ForumTagDTO toDTO(ForumTag tag) {
        return new ForumTagDTO(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                tag.getDescription(),
                tag.getColor(),
                tag.getUsageCount(),
                tag.getCreatedAt());
    }
}
