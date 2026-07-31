package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

import java.util.List;

/**
 * 用户管理服务：负责管理后台的用户写操作 &mdash; CRUD、封禁、批量操作。
 *
 * <p>从原 {@code AdminUserService} 拆分而来（架构评审 Candidate 1）。
 * 权限授予 / 撤销逻辑移至 {@link UserPermissionService}，使两个模块各自深化、独立演进。
 *
 * <p>该接口仅承担「用户档案与状态」语义；
 * 不持有任何权限授予 / 撤销相关方法，避免与 {@link UserPermissionService} 产生交叉依赖。
 *
 * <p><b>ADR-0011 Stage 2 更新</b>：所有读路径（列表 / 单条详情 + stats + permissions
 * 快照）已迁移至 {@link com.ulticode.modules.admin.projection.AdminUserProjection}。
 * 本接口现在只暴露写操作；写方法的返回 VO 通过委托
 * {@link com.ulticode.modules.admin.projection.AdminUserProjection#getUserById(String)}
 * 组合而成，避免在两处复制 entity&rarr;VO 规则。
 */
public interface UserManagementService {

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
