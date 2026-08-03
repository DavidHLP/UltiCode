package com.ulticode.modules.admin.service.impl;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.admin.service.AdminTagService;
import com.ulticode.modules.admin.service.handler.ForumTagHandler;
import com.ulticode.modules.admin.service.handler.ProblemTagHandler;
import com.ulticode.modules.admin.service.handler.TagDomainHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTagServiceImpl implements AdminTagService {
 private final ProblemTagHandler problemTagHandler;
 private final ForumTagHandler forumTagHandler;
    private TagDomainHandler handlerFor(String normalizedType) {
        return TagTypes.FORUM.equals(normalizedType) ? forumTagHandler : problemTagHandler;
    }

    private static String normalizeType(String type) {
        if (type == null) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Invalid tag type: required. Allowed: PROBLEM, FORUM");
        }
        if (TagTypes.WHITELIST.stream().noneMatch(t -> t.equalsIgnoreCase(type))) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Invalid tag type: '" + type + "'. Allowed: PROBLEM, FORUM");
        }
        return type.toUpperCase();
    }

    @Override
    public TagListResponse getTags(TagQueryDTO query) {
        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 20;
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "name";
        String sortOrder = "desc".equalsIgnoreCase(query.getSortOrder()) ? "desc" : "asc";
        String type = normalizeType(query.getType());
        return handlerFor(type).list(query.getSearch(), pageNum, pageSize, sortBy, sortOrder);
    }

    @Override
    public TagVO getTag(String id, String type) {
        return handlerFor(normalizeType(type)).getById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_TAG, entityType = AuditVocabulary.ENTITY_TAG, captureOldState = false)
    public TagVO createTag(CreateTagDTO dto) {
        String normalized = normalizeType(dto.getType());
        String slug = StringUtils.hasText(dto.getSlug()) ? dto.getSlug() : generateSlug(dto.getName());
        TagVO result = handlerFor(normalized).create(dto, slug);
        AuditContext.setNewValues(Map.of("name", result.getName(), "type", normalized));
        return result;
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_TAG, entityType = AuditVocabulary.ENTITY_TAG, entityIdFrom = "id")
    public TagVO updateTag(String id, UpdateTagDTO dto) {
        String normalized = normalizeType(dto.getType());
        TagDomainHandler handler = handlerFor(normalized);
        TagVO existing = handler.getById(id);
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", existing.getName());
        oldValues.put("slug", existing.getSlug());
        oldValues.put("description", existing.getDescription());
        oldValues.put("color", existing.getColor());
        TagVO result = handler.update(id, dto);
        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(Map.of("name", result.getName(), "type", normalized));
        return result;
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_TAG, entityType = AuditVocabulary.ENTITY_TAG, entityIdFrom = "id")
    public void deleteTag(String id, String type) {
        String normalized = normalizeType(type);
        TagDomainHandler handler = handlerFor(normalized);
        TagVO existing = handler.getById(id);
        AuditContext.setOldValues(Map.of("name", existing.getName(), "type", normalized));
        AuditContext.setNewValues(null);
        handler.delete(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_TAG, entityType = AuditVocabulary.ENTITY_TAG)
    public void mergeTag(MergeTagDTO dto) {
        if (dto.getSourceId().equals(dto.getTargetTagId())) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Cannot merge tag into itself");
        }
        String normalized = normalizeType(dto.getType());
        TagDomainHandler handler = handlerFor(normalized);
        TagVO source = handler.getById(dto.getSourceId());
        AuditContext.setOldValues(Map.of("name", source.getName(), "mergedInto", dto.getTargetTagId()));
        AuditContext.setNewValues(null);
        handler.merge(dto);
    }

    private String generateSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
