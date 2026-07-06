package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

import java.util.List;

/**
 * 用户管理服务：负责管理后台的用户 CRUD、封禁、批量操作。
 *
 * <p>从原 {@code AdminUserService} 拆分而来（架构评审 Candidate 1）。
 * 权限授予 / 撤销逻辑移至 {@link UserPermissionService}，使两个模块各自深化、独立演进。
 *
 * <p>该接口仅承担「用户档案与状态」语义；
 * 不持有任何权限授予 / 撤销相关方法，避免与 {@link UserPermissionService} 产生交叉依赖。
 */
public interface UserManagementService {

    /**
     * 分页查询用户列表，支持搜索 / 角色过滤 / 状态过滤 / 排序。
     */
    PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query);

    /**
     * 按 ID 获取用户详情，含 stats 与 permissions 快照。
     * 该方法同时被 {@link UserPermissionService} 在授权变更后调用以返回最新 VO。
     */
    AdminUserVO getUserById(String id);

    /**
     * 创建用户。
     */
    AdminUserVO createUser(AdminCreateUserDTO dto);

    /**
     * 更新用户档案字段。
     */
    AdminUserVO updateUser(String id, AdminUpdateUserDTO dto);

    /**
     * 删除用户（物理删除）。
     */
    void deleteUser(String id);

    /**
     * 封禁用户；{@code until} 为 ISO-local-date-time 字符串，可空表示永久封禁。
     */
    AdminUserVO banUser(String id, String reason, String until);

    /**
     * 解除封禁。
     */
    AdminUserVO unbanUser(String id);

    /**
     * 重置用户密码。
     */
    void resetPassword(String id, String newPassword);

    /**
     * 批量封禁；每条独立 try/catch，失败项以 {@link BanResult#error} 返回。
     */
    List<BanResult> bulkBan(List<String> ids, String reason);

    /**
     * 批量解禁。
     */
    List<BanResult> bulkUnban(List<String> ids);

    /**
     * 批量删除。
     */
    List<DeleteResult> bulkDelete(List<String> ids);

    /**
     * 批量封禁 / 解禁操作的单条结果。
     */
    record BanResult(String id, boolean success, String error) {
    }

    /**
     * 批量删除操作的单条结果。
     */
    record DeleteResult(String id, boolean success, String error) {
    }
}
