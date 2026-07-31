package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.tag.*;

import java.util.List;

public interface AdminTagService {

    TagListResponse getTags(TagQueryDTO query);

    TagVO getTag(String id, String type);

    TagVO createTag(CreateTagDTO dto);

    TagVO updateTag(String id, UpdateTagDTO dto);

    void deleteTag(String id, String type);

    void mergeTag(MergeTagDTO dto);
}
