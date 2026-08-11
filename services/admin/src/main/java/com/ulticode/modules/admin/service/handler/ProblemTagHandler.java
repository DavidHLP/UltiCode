package com.ulticode.modules.admin.service.handler;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.app.api.service.ProblemTagOwnerPort.TagWrite;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin tag-domain handler for {@code PROBLEM} tags.
 *
 * <p>ADMIN-003: both the reads (list / getById / uniqueness conflict
 * checks) and the writes (create / update / delete / merge) flow through
 * the public {@link ProblemAdminReadPort} / {@link ProblemTagOwnerPort}
 * owner contracts; the App-private tag entities and mappers are no longer
 * imported.
 */
@Component
@RequiredArgsConstructor
public class ProblemTagHandler implements TagDomainHandler {

    private final ProblemAdminReadPort problemReadPort;
    private final ProblemTagOwnerPort problemTagOwnerPort;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    @Override
    public String type() {
        return TagTypes.PROBLEM;
    }

    @Override
    public TagListResponse list(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        PageResult<ProblemAdminTagDTO> result =
                problemReadPort.listTags(search, pageNum, pageSize, sortBy, sortOrder);
        List<TagVO> data = result.getItems().stream().map(this::toTagVO).collect(Collectors.toList());
        return TagListResponse.of(data, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public TagVO getById(String id) {
        ProblemAdminTagDTO tag = problemReadPort.getTagById(id);
        if (tag == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        return toTagVO(tag);
    }

    @Override
    public TagVO create(CreateTagDTO dto, String slug) {
        checkNameConflict(dto.getName());
        checkSlugConflict(slug);
        LocalDateTime now = LocalDateTime.now(clock);
        TagWrite write = new TagWrite(
                uuidGenerator.newId(), dto.getName(), slug, dto.getDescription(),
                dto.getColor(), 0, now, now);
        problemTagOwnerPort.createTag(write);
        return toTagVO(toDto(write));
    }

    @Override
    public TagVO update(String id, UpdateTagDTO dto) {
        ProblemAdminTagDTO existing = problemReadPort.getTagById(id);
        if (existing == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        String label = existing.label();
        String slug = existing.slug();
        String description = existing.description();
        String color = existing.color();
        if (StringUtils.hasText(dto.getName()) && !dto.getName().equals(existing.label())) {
            checkNameConflict(dto.getName());
            label = dto.getName();
        }
        if (StringUtils.hasText(dto.getSlug()) && !dto.getSlug().equals(existing.slug())) {
            checkSlugConflict(dto.getSlug());
            slug = dto.getSlug();
        }
        if (dto.getDescription() != null) {
            description = dto.getDescription();
        }
        if (dto.getColor() != null) {
            color = dto.getColor();
        }
        TagWrite write = new TagWrite(
                id, label, slug, description, color, existing.usageCount(),
                existing.createdAt(), LocalDateTime.now(clock));
        problemTagOwnerPort.updateTag(write);
        return toTagVO(toDto(write));
    }

    @Override
    public void delete(String id) {
        if (problemReadPort.getTagById(id) == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        problemTagOwnerPort.deleteTag(id);
    }

    @Override
    public void merge(MergeTagDTO dto) {
        ProblemAdminTagDTO source = problemReadPort.getTagById(dto.getSourceId());
        ProblemAdminTagDTO target = problemReadPort.getTagById(dto.getTargetTagId());
        if (source == null || target == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        problemTagOwnerPort.mergeTags(dto.getSourceId(), dto.getTargetTagId());
    }

    private void checkNameConflict(String name) {
        if (problemReadPort.tagNameExists(name)) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NAME_EXISTS);
        }
    }

    private void checkSlugConflict(String slug) {
        if (problemReadPort.tagSlugExists(slug)) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_SLUG_EXISTS);
        }
    }

    private static ProblemAdminTagDTO toDto(TagWrite write) {
        return new ProblemAdminTagDTO(
                write.id(), write.label(), write.slug(), write.description(), write.color(),
                write.usageCount(), write.createdAt(), write.updatedAt());
    }

    private TagVO toTagVO(ProblemAdminTagDTO tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.id());
        vo.setName(tag.label());
        vo.setSlug(tag.slug());
        vo.setDescription(tag.description());
        vo.setColor(tag.color());
        vo.setUsageCount(tag.usageCount());
        vo.setType(TagTypes.PROBLEM);
        vo.setCreatedAt(tag.createdAt());
        vo.setUpdatedAt(tag.updatedAt());
        return vo;
    }
}
