package com.ulticode.modules.admin.service.handler;

import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import com.ulticode.modules.admin.dto.tag.MergeTagDTO;
import com.ulticode.modules.admin.dto.tag.TagListResponse;
import com.ulticode.modules.admin.dto.tag.TagTypes;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.dto.tag.UpdateTagDTO;

/**
 * Strategy for tag CRUD against one domain (problem or forum).
 * Extracted from AdminTagServiceImpl to eliminate repeated if/else branching.
 */
public interface TagDomainHandler {

    String type();

    TagListResponse list(String search, int pageNum, int pageSize, String sortBy, String sortOrder);

    TagVO getById(String id);

    TagVO create(CreateTagDTO dto, String slug);

    TagVO update(String id, UpdateTagDTO dto);

    void delete(String id);

    void merge(MergeTagDTO dto);
}
