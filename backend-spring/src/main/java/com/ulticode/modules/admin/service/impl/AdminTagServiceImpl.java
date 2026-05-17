package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.admin.service.AdminTagService;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTagServiceImpl implements AdminTagService {

    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ForumTagMapper forumTagMapper;
    private final AuditHelper auditHelper;

    private static final String TYPE_PROBLEM = "PROBLEM";
    private static final String TYPE_FORUM = "FORUM";

    @Override
    public TagListResponse getTags(TagQueryDTO query) {
        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 20;
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "name";
        String sortOrder = "desc".equalsIgnoreCase(query.getSortOrder()) ? "desc" : "asc";

        if (TYPE_FORUM.equalsIgnoreCase(query.getType())) {
            return getForumTags(query.getSearch(), pageNum, pageSize, sortBy, sortOrder);
        }
        return getProblemTags(query.getSearch(), pageNum, pageSize, sortBy, sortOrder);
    }

    private TagListResponse getProblemTags(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.like(ProblemTag::getLabel, search).or().like(ProblemTag::getSlug, search);
        }
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), ProblemTag::getLabel);

        IPage<ProblemTag> page = new Page<>(pageNum, pageSize);
        IPage<ProblemTag> result = problemTagMapper.selectPage(page, wrapper);

        List<TagVO> data = result.getRecords().stream()
                .map(this::toTagVO)
                .collect(Collectors.toList());

        return TagListResponse.of(data, result.getTotal(), pageNum, pageSize);
    }

    private TagListResponse getForumTags(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ForumTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.like(ForumTag::getName, search).or().like(ForumTag::getSlug, search);
        }
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if ("usageCount".equalsIgnoreCase(sortBy) || "usage_count".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ForumTag::getUsageCount);
        } else {
            wrapper.orderBy(true, isAsc, ForumTag::getName);
        }

        IPage<ForumTag> page = new Page<>(pageNum, pageSize);
        IPage<ForumTag> result = forumTagMapper.selectPage(page, wrapper);

        List<TagVO> data = result.getRecords().stream()
                .map(this::toTagVO)
                .collect(Collectors.toList());

        return TagListResponse.of(data, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public TagVO getTag(String id, String type) {
        if (TYPE_FORUM.equalsIgnoreCase(type)) {
            ForumTag tag = forumTagMapper.selectById(id);
            if (tag == null) {
                throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
            }
            return toTagVO(tag);
        }
        ProblemTag tag = problemTagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        return toTagVO(tag);
    }

    @Override
    @Transactional
    public TagVO createTag(CreateTagDTO dto) {
        String slug = StringUtils.hasText(dto.getSlug()) ? dto.getSlug() : generateSlug(dto.getName());

        if (TYPE_FORUM.equalsIgnoreCase(dto.getType())) {
            if (forumTagMapper.existsByName(dto.getName())) {
                throw new BusinessException(ErrorCode.FORUM_TAG_NAME_EXISTS);
            }
            if (forumTagMapper.existsBySlug(slug)) {
                throw new BusinessException(ErrorCode.FORUM_TAG_SLUG_EXISTS);
            }
            ForumTag tag = new ForumTag();
            tag.setId(UUID.randomUUID().toString());
            tag.setName(dto.getName());
            tag.setSlug(slug);
            tag.setDescription(dto.getDescription());
            tag.setColor(dto.getColor());
            tag.setUsageCount(0);
            tag.setCreatedAt(LocalDateTime.now());
            forumTagMapper.insert(tag);

            auditHelper.log(
                AuditActionUtil.CREATE_TAG,
                AuditActionUtil.ENTITY_TAG,
                tag.getId(),
                null,
                Map.of("name", tag.getName(), "type", TYPE_FORUM)
            );

            return toTagVO(tag);
        }

        LambdaQueryWrapper<ProblemTag> nameWrapper = new LambdaQueryWrapper<>();
        nameWrapper.eq(ProblemTag::getLabel, dto.getName());
        if (problemTagMapper.selectCount(nameWrapper) > 0) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_NAME_EXISTS);
        }
        LambdaQueryWrapper<ProblemTag> slugWrapper = new LambdaQueryWrapper<>();
        slugWrapper.eq(ProblemTag::getSlug, slug);
        if (problemTagMapper.selectCount(slugWrapper) > 0) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_SLUG_EXISTS);
        }

        ProblemTag tag = new ProblemTag();
        tag.setId(UUID.randomUUID().toString());
        tag.setLabel(dto.getName());
        tag.setSlug(slug);
        tag.setDescription(dto.getDescription());
        tag.setColor(dto.getColor());
        tag.setUsageCount(0);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setUpdatedAt(LocalDateTime.now());
        problemTagMapper.insert(tag);

        auditHelper.log(
            AuditActionUtil.CREATE_TAG,
            AuditActionUtil.ENTITY_TAG,
            tag.getId(),
            null,
            Map.of("name", tag.getLabel(), "type", TYPE_PROBLEM)
        );

        return toTagVO(tag);
    }

    @Override
    @Transactional
    public TagVO updateTag(String id, UpdateTagDTO dto) {
        if (TYPE_FORUM.equalsIgnoreCase(dto.getType())) {
            ForumTag existing = forumTagMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
            }

            java.util.Map<String, Object> oldValues = new java.util.HashMap<>();
            oldValues.put("name", existing.getName());
            oldValues.put("slug", existing.getSlug());
            oldValues.put("description", existing.getDescription());
            oldValues.put("color", existing.getColor());

            if (StringUtils.hasText(dto.getName()) && !dto.getName().equals(existing.getName())) {
                if (forumTagMapper.existsByName(dto.getName())) {
                    throw new BusinessException(ErrorCode.FORUM_TAG_NAME_EXISTS);
                }
                existing.setName(dto.getName());
            }
            if (StringUtils.hasText(dto.getSlug()) && !dto.getSlug().equals(existing.getSlug())) {
                if (forumTagMapper.existsBySlug(dto.getSlug())) {
                    throw new BusinessException(ErrorCode.FORUM_TAG_SLUG_EXISTS);
                }
                existing.setSlug(dto.getSlug());
            }
            if (dto.getDescription() != null) {
                existing.setDescription(dto.getDescription());
            }
            if (dto.getColor() != null) {
                existing.setColor(dto.getColor());
            }
            forumTagMapper.updateById(existing);

            auditHelper.log(
                AuditActionUtil.UPDATE_TAG,
                AuditActionUtil.ENTITY_TAG,
                id,
                oldValues,
                Map.of("name", existing.getName(), "type", TYPE_FORUM)
            );

            return toTagVO(existing);
        }

        ProblemTag existing = problemTagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND);
        }

        java.util.Map<String, Object> oldValues = new java.util.HashMap<>();
        oldValues.put("name", existing.getLabel());
        oldValues.put("slug", existing.getSlug());
        oldValues.put("description", existing.getDescription());
        oldValues.put("color", existing.getColor());

        if (StringUtils.hasText(dto.getName()) && !dto.getName().equals(existing.getLabel())) {
            LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemTag::getLabel, dto.getName());
            if (problemTagMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PROBLEM_TAG_NAME_EXISTS);
            }
            existing.setLabel(dto.getName());
        }
        if (StringUtils.hasText(dto.getSlug()) && !dto.getSlug().equals(existing.getSlug())) {
            LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemTag::getSlug, dto.getSlug());
            if (problemTagMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PROBLEM_TAG_SLUG_EXISTS);
            }
            existing.setSlug(dto.getSlug());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        if (dto.getColor() != null) {
            existing.setColor(dto.getColor());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        problemTagMapper.updateById(existing);

        auditHelper.log(
            AuditActionUtil.UPDATE_TAG,
            AuditActionUtil.ENTITY_TAG,
            id,
            oldValues,
            Map.of("name", existing.getLabel(), "type", TYPE_PROBLEM)
        );

        return toTagVO(existing);
    }

    @Override
    @Transactional
    public void deleteTag(String id, String type) {
        if (TYPE_FORUM.equalsIgnoreCase(type)) {
            ForumTag existing = forumTagMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
            }
            forumTagMapper.deleteById(id);

            auditHelper.log(
                AuditActionUtil.DELETE_TAG,
                AuditActionUtil.ENTITY_TAG,
                id,
                Map.of("name", existing.getName(), "type", TYPE_FORUM),
                null
            );
            return;
        }
        ProblemTag existing = problemTagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        problemTagMapper.deleteById(id);

        auditHelper.log(
            AuditActionUtil.DELETE_TAG,
            AuditActionUtil.ENTITY_TAG,
            id,
            Map.of("name", existing.getLabel(), "type", TYPE_PROBLEM),
            null
        );
    }

    @Override
    @Transactional
    public void mergeTag(MergeTagDTO dto) {
        if (dto.getSourceId().equals(dto.getTargetTagId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot merge tag into itself");
        }

        if (TYPE_FORUM.equalsIgnoreCase(dto.getType())) {
            ForumTag source = forumTagMapper.selectById(dto.getSourceId());
            ForumTag target = forumTagMapper.selectById(dto.getTargetTagId());
            if (source == null || target == null) {
                throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
            }
            forumTagMapper.deleteById(dto.getSourceId());
            return;
        }

        ProblemTag source = problemTagMapper.selectById(dto.getSourceId());
        ProblemTag target = problemTagMapper.selectById(dto.getTargetTagId());
        if (source == null || target == null) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND);
        }

        LambdaUpdateWrapper<ProblemTagRelation> updateWrapper =
                new LambdaUpdateWrapper<>();
        updateWrapper.eq(ProblemTagRelation::getTagId, dto.getSourceId())
                .set(ProblemTagRelation::getTagId, dto.getTargetTagId());
        problemTagRelationMapper.update(updateWrapper);

        problemTagMapper.deleteById(dto.getSourceId());

        auditHelper.log(
            AuditActionUtil.UPDATE_TAG,
            AuditActionUtil.ENTITY_TAG,
            dto.getSourceId(),
            Map.of("name", source.getLabel(), "mergedInto", dto.getTargetTagId()),
            null
        );

        LambdaQueryWrapper<ProblemTagRelation> countWrapper =
                new LambdaQueryWrapper<>();
        countWrapper.eq(ProblemTagRelation::getTagId, dto.getTargetTagId());
        long count = problemTagRelationMapper.selectCount(countWrapper);
        target.setUsageCount((int) count);
        target.setUpdatedAt(LocalDateTime.now());
        problemTagMapper.updateById(target);
    }

    private TagVO toTagVO(ProblemTag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getLabel());
        vo.setSlug(tag.getSlug());
        vo.setDescription(tag.getDescription());
        vo.setColor(tag.getColor());
        vo.setUsageCount(tag.getUsageCount());
        vo.setType(TYPE_PROBLEM);
        vo.setCreatedAt(tag.getCreatedAt());
        vo.setUpdatedAt(tag.getUpdatedAt());
        return vo;
    }

    private TagVO toTagVO(ForumTag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setDescription(tag.getDescription());
        vo.setColor(tag.getColor());
        vo.setUsageCount(tag.getUsageCount());
        vo.setType(TYPE_FORUM);
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }

    private String generateSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
