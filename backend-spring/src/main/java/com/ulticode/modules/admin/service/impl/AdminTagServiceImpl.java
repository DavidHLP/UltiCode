package com.ulticode.modules.admin.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.admin.dto.tag.TagTypes;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTagServiceImpl implements AdminTagService {
 private final ProblemTagMapper problemTagMapper;
 private final ProblemTagRelationMapper problemTagRelationMapper;
 private final ForumTagMapper forumTagMapper;
 private final Clock clock;
 private final UuidGenerator uuidGenerator;
 /**
 * Defense-in-depth whitelist check: rejects null / unknown type values that the
 * controller @Pattern should have already filtered. Throws BAD_REQUEST so direct
 * Service callers (e.g. scheduled jobs, future internal APIs) get the same
 * error shape as HTTP consumers. See docs/admin-tags-test-plan.md §7 Bug #2.
 *
 * <p>The single source of truth for the whitelist lives in
 * {@link com.ulticode.modules.admin.dto.tag.TagTypes}.</p>
 *
 * @return uppercased, canonical type ("PROBLEM" or "FORUM")
 */
 private static String normalizeType(String type) {
 if (type == null) {
 throw new BusinessException(ErrorCode.BAD_REQUEST,
 "Invalid tag type: required. Allowed: PROBLEM, FORUM");
 }
 if (TagTypes.WHITELIST.stream().noneMatch(t -> t.equalsIgnoreCase(type))) {
 throw new BusinessException(ErrorCode.BAD_REQUEST,
 "Invalid tag type: '" + type + "'. Allowed: PROBLEM, FORUM");
 }
 return type.toUpperCase();
 }
 @Override
 public TagListResponse getTags(TagQueryDTO query) {
 int pageNum = query.getPage() != null && query.getPage() >0 ? query.getPage() :1;
 int pageSize = query.getLimit() != null && query.getLimit() >0 ? query.getLimit() :20;
 String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "name";
 String sortOrder = "desc".equalsIgnoreCase(query.getSortOrder()) ? "desc" : "asc";
 String type = normalizeType(query.getType());
 if (TagTypes.FORUM.equals(type)) {
 return getForumTags(query.getSearch(), pageNum, pageSize, sortBy, sortOrder);
 }
 return getProblemTags(query.getSearch(), pageNum, pageSize, sortBy, sortOrder);
 }
 private TagListResponse getProblemTags(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
 LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
 if (StringUtils.hasText(search)) {
 wrapper.like(ProblemTag::getLabel, search).or().like(ProblemTag::getSlug, search);
 }
 boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
 // Bug #3 fix: honor sortBy for PROBLEM tags (was hardcoded to label sort).
 // Mirrors the getForumTags branch. Unknown sortBy falls back to label to preserve
 // backward compatibility with existing callers.
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
 String normalized = normalizeType(type);
 if (TagTypes.FORUM.equals(normalized)) {
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
 @Audited(action = AuditVocabulary.CREATE_TAG, entityType = AuditVocabulary.ENTITY_TAG, captureOldState = false)
 public TagVO createTag(CreateTagDTO dto) {
 String normalized = normalizeType(dto.getType());
 String slug = StringUtils.hasText(dto.getSlug()) ? dto.getSlug() : generateSlug(dto.getName());
 if (TagTypes.FORUM.equals(normalized)) {
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
 AuditContext.setNewValues(Map.of("name", tag.getName(), "type", TagTypes.FORUM));
 return toTagVO(tag);
 }
 LambdaQueryWrapper<ProblemTag> nameWrapper = new LambdaQueryWrapper<>();
 nameWrapper.eq(ProblemTag::getLabel, dto.getName());
 if (problemTagMapper.selectCount(nameWrapper) >0) {
 throw new BusinessException(ErrorCode.PROBLEM_TAG_NAME_EXISTS);
 }
 LambdaQueryWrapper<ProblemTag> slugWrapper = new LambdaQueryWrapper<>();
 slugWrapper.eq(ProblemTag::getSlug, slug);
 if (problemTagMapper.selectCount(slugWrapper) >0) {
 throw new BusinessException(ErrorCode.PROBLEM_TAG_SLUG_EXISTS);
 }
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
 AuditContext.setNewValues(Map.of("name", tag.getLabel(), "type", TagTypes.PROBLEM));
 return toTagVO(tag);
 }
 @Override
 @Transactional
 @Audited(action = AuditVocabulary.UPDATE_TAG, entityType = AuditVocabulary.ENTITY_TAG, entityIdFrom = "id")
 public TagVO updateTag(String id, UpdateTagDTO dto) {
 String normalized = normalizeType(dto.getType());
 if (TagTypes.FORUM.equals(normalized)) {
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
 AuditContext.setOldValues(oldValues);
 AuditContext.setNewValues(Map.of("name", existing.getName(), "type", TagTypes.FORUM));
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
 if (problemTagMapper.selectCount(wrapper) >0) {
 throw new BusinessException(ErrorCode.PROBLEM_TAG_NAME_EXISTS);
 }
 existing.setLabel(dto.getName());
 }
 if (StringUtils.hasText(dto.getSlug()) && !dto.getSlug().equals(existing.getSlug())) {
 LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
 wrapper.eq(ProblemTag::getSlug, dto.getSlug());
 if (problemTagMapper.selectCount(wrapper) >0) {
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
  existing.setUpdatedAt(LocalDateTime.now(clock));
  problemTagMapper.updateById(existing);
  AuditContext.setOldValues(oldValues);
  AuditContext.setNewValues(Map.of("name", existing.getLabel(), "type", TagTypes.PROBLEM));
 return toTagVO(existing);
 }
 @Override
 @Transactional
 @Audited(action = AuditVocabulary.DELETE_TAG, entityType = AuditVocabulary.ENTITY_TAG, entityIdFrom = "id")
 public void deleteTag(String id, String type) {
 String normalized = normalizeType(type);
 if (TagTypes.FORUM.equals(normalized)) {
 ForumTag existing = forumTagMapper.selectById(id);
 if (existing == null) {
 throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
 }
 AuditContext.setOldValues(Map.of("name", existing.getName(), "type", TagTypes.FORUM));
 AuditContext.setNewValues(null);
 forumTagMapper.deleteById(id);
 return;
 }
 ProblemTag existing = problemTagMapper.selectById(id);
 if (existing == null) {
 throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND);
 }
 AuditContext.setOldValues(Map.of("name", existing.getLabel(), "type", TagTypes.PROBLEM));
 AuditContext.setNewValues(null);
 problemTagMapper.deleteById(id);
 }
 @Override
 @Transactional
 @Audited(action = AuditVocabulary.UPDATE_TAG, entityType = AuditVocabulary.ENTITY_TAG)
 public void mergeTag(MergeTagDTO dto) {
 if (dto.getSourceId().equals(dto.getTargetTagId())) {
 throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot merge tag into itself");
 }
 String normalized = normalizeType(dto.getType());
 if (TagTypes.FORUM.equals(normalized)) {
 ForumTag source = forumTagMapper.selectById(dto.getSourceId());
 ForumTag target = forumTagMapper.selectById(dto.getTargetTagId());
 if (source == null || target == null) {
 throw new BusinessException(ErrorCode.FORUM_TAG_NOT_FOUND);
 }
 AuditContext.setOldValues(Map.of("name", source.getName(), "mergedInto", dto.getTargetTagId()));
 AuditContext.setNewValues(null);
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
 AuditContext.setOldValues(Map.of("name", source.getLabel(), "mergedInto", dto.getTargetTagId()));
 AuditContext.setNewValues(null);
 problemTagMapper.deleteById(dto.getSourceId());
 LambdaQueryWrapper<ProblemTagRelation> countWrapper =
 new LambdaQueryWrapper<>();
 countWrapper.eq(ProblemTagRelation::getTagId, dto.getTargetTagId());
 long count = problemTagRelationMapper.selectCount(countWrapper);
  target.setUsageCount((int) count);
  target.setUpdatedAt(LocalDateTime.now(clock));
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
 vo.setType(TagTypes.PROBLEM);
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
 vo.setType(TagTypes.FORUM);
 vo.setCreatedAt(tag.getCreatedAt());
 return vo;
 }
 private String generateSlug(String name) {
 return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
 }
}
