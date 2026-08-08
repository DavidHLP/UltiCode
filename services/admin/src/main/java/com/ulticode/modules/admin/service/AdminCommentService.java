package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;

public interface AdminCommentService {

    PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO query);

    AdminCommentVO getComment(String id, String type);

    AdminCommentVO flagComment(String id, String type, String reason);

    AdminCommentVO unflagComment(String id, String type);

    void deleteComment(String id, String type);

    BulkActionResult bulkCommentAction(BulkCommentActionRequest request);
}