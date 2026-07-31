package com.ulticode.modules.admin.service.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ForumTagHandler implements TagDomainHandler {

    private final ForumTagMapper forumTagMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    @Override
    public String type() {
        return TagTypes.FORUM;
    }

    @Override
    public TagListResponse list(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
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
        List<TagVO> data = result.getRecords().stream().map(this::toTagVO).collect(Collectors.toList());
        return TagListResponse.of(data, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public TagVO getById(String id) {
        ForumTag tag = forumTagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
        }
        return toTagVO(tag);
    }

    @Override
    public TagVO create(CreateTagDTO dto, String slug) {
        if (forumTagMapper.existsByName(dto.getName())) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NAME_EXISTS);
        }
        if (forumTagMapper.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.FORUM_TAG_SLUG_EXISTS);
        }
        ForumTag tag = new ForumTag();
        tag.setId(uuidGenerator.newId());
        tag.setName(dto.getName());
        tag.setSlug(slug);
        tag.setDescription(dto.getDescription());
        tag.setColor(dto.getColor());
        tag.setUsageCount(0);
        tag.setCreatedAt(LocalDateTime.now(clock));
        forumTagMapper.insert(tag);
        return toTagVO(tag);
    }

    @Override
    public TagVO update(String id, UpdateTagDTO dto) {
        ForumTag existing = forumTagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
        }
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
        return toTagVO(existing);
    }

    @Override
    public void delete(String id) {
        if (forumTagMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
        }
        forumTagMapper.deleteById(id);
    }

    @Override
    public void merge(MergeTagDTO dto) {
        ForumTag source = forumTagMapper.selectById(dto.getSourceId());
        ForumTag target = forumTagMapper.selectById(dto.getTargetTagId());
        if (source == null || target == null) {
            throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
        }
        forumTagMapper.deleteById(dto.getSourceId());
    }

    public Map<String, Object> auditValues(ForumTag tag) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", tag.getName());
        values.put("slug", tag.getSlug());
        values.put("description", tag.getDescription());
        values.put("color", tag.getColor());
        return values;
    }

    private TagVO toTagVO(ForumTag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setDescription(tag.getDescription());
        vo.setColor(tag.getColor());
        vo.setUsageCount(tag.getUsageCount());
        vo.setType(TagTypes.FORUM);
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }
}
