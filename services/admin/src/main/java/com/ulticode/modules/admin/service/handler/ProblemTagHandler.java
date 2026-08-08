package com.ulticode.modules.admin.service.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
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
public class ProblemTagHandler implements TagDomainHandler {

    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    @Override
    public String type() {
        return TagTypes.PROBLEM;
    }

    @Override
    public TagListResponse list(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.like(ProblemTag::getLabel, search).or().like(ProblemTag::getSlug, search);
        }
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if ("usageCount".equalsIgnoreCase(sortBy) || "usage_count".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getUsageCount);
        } else if ("createdAt".equalsIgnoreCase(sortBy) || "created_at".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getCreatedAt);
        } else if ("slug".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getSlug);
        } else {
            wrapper.orderBy(true, isAsc, ProblemTag::getLabel);
        }
        IPage<ProblemTag> page = new Page<>(pageNum, pageSize);
        IPage<ProblemTag> result = problemTagMapper.selectPage(page, wrapper);
        List<TagVO> data = result.getRecords().stream().map(this::toTagVO).collect(Collectors.toList());
        return TagListResponse.of(data, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public TagVO getById(String id) {
        ProblemTag tag = problemTagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        return toTagVO(tag);
    }

    @Override
    public TagVO create(CreateTagDTO dto, String slug) {
        checkNameConflict(dto.getName());
        checkSlugConflict(slug);
        ProblemTag tag = new ProblemTag();
        tag.setId(uuidGenerator.newId());
        tag.setLabel(dto.getName());
        tag.setSlug(slug);
        tag.setDescription(dto.getDescription());
        tag.setColor(dto.getColor());
        tag.setUsageCount(0);
        tag.setCreatedAt(LocalDateTime.now(clock));
        tag.setUpdatedAt(LocalDateTime.now(clock));
        problemTagMapper.insert(tag);
        return toTagVO(tag);
    }

    @Override
    public TagVO update(String id, UpdateTagDTO dto) {
        ProblemTag existing = problemTagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        if (StringUtils.hasText(dto.getName()) && !dto.getName().equals(existing.getLabel())) {
            checkNameConflict(dto.getName());
            existing.setLabel(dto.getName());
        }
        if (StringUtils.hasText(dto.getSlug()) && !dto.getSlug().equals(existing.getSlug())) {
            checkSlugConflict(dto.getSlug());
            existing.setSlug(dto.getSlug());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        if (dto.getColor() != null) {
            existing.setColor(dto.getColor());
        }
        existing.setUpdatedAt(LocalDateTime.now(clock));
        problemTagMapper.updateById(existing);
        return toTagVO(existing);
    }

    @Override
    public void delete(String id) {
        if (problemTagMapper.selectById(id) == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        problemTagMapper.deleteById(id);
    }

    @Override
    public void merge(MergeTagDTO dto) {
        ProblemTag source = problemTagMapper.selectById(dto.getSourceId());
        ProblemTag target = problemTagMapper.selectById(dto.getTargetTagId());
        if (source == null || target == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND);
        }
        LambdaUpdateWrapper<ProblemTagRelation> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ProblemTagRelation::getTagId, dto.getSourceId())
                .set(ProblemTagRelation::getTagId, dto.getTargetTagId());
        problemTagRelationMapper.update(updateWrapper);
        problemTagMapper.deleteById(dto.getSourceId());
        LambdaQueryWrapper<ProblemTagRelation> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ProblemTagRelation::getTagId, dto.getTargetTagId());
        target.setUsageCount(Math.toIntExact(problemTagRelationMapper.selectCount(countWrapper)));
        target.setUpdatedAt(LocalDateTime.now(clock));
        problemTagMapper.updateById(target);
    }

    public Map<String, Object> auditValues(ProblemTag tag) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", tag.getLabel());
        values.put("slug", tag.getSlug());
        values.put("description", tag.getDescription());
        values.put("color", tag.getColor());
        return values;
    }

    private void checkNameConflict(String name) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getLabel, name);
        if (problemTagMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_NAME_EXISTS);
        }
    }

    private void checkSlugConflict(String slug) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getSlug, slug);
        if (problemTagMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(AdminErrorCode.PROBLEM_TAG_SLUG_EXISTS);
        }
    }

    private TagVO toTagVO(ProblemTag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getLabel());
        vo.setSlug(tag.getSlug());
        vo.setDescription(tag.getDescription());
        vo.setColor(tag.getColor());
        vo.setUsageCount(tag.getUsageCount());
        vo.setType(TagTypes.PROBLEM);
        vo.setCreatedAt(tag.getCreatedAt());
        vo.setUpdatedAt(tag.getUpdatedAt());
        return vo;
    }
}
