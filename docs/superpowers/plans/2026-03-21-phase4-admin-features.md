# NestJS 到 Spring Boot 迁移 - Phase 4: 管理功能

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现管理后台、内容审核、通知、收藏和题单模块，完成前后端核心功能对接。

**Architecture:** Controller → Service → Mapper 三层架构，使用 MyBatis-Plus 进行数据访问，复用 Phase 1-3 已建立的模式。

**Tech Stack:** Spring Boot 3.5.12, MyBatis-Plus 3.5.5, Spring Security, jjwt 0.12.5

---

## 前置条件

- Phase 1-3 已完成
- 数据库已存在 (与 NestJS 共享)
- 现有模块：user, auth, problem, submission, solution, contest, forum

---

## 模块概览

| 模块 | 功能 | 文件数 | API 端点数 |
|------|------|--------|-----------|
| Admin | 审计日志、用户管理、导出 | 14 | 12+ |
| Moderation | 内容审核、举报处理、申诉 | 22 | 25+ |
| Notification | 通知管理、偏好设置 | 12 | 8 |
| Bookmark | 收藏夹管理 | 14 | 14 |
| ProblemList | 题单管理、分类管理 | 16 | 20+ |

---

## Task 1: Admin 模块 - 审计日志

**Files:**
- Create: `src/main/java/com/ulticode/modules/admin/entity/AuditLog.java`
- Create: `src/main/java/com/ulticode/modules/admin/mapper/AuditLogMapper.java`
- Create: `src/main/java/com/ulticode/modules/admin/dto/AuditLogVO.java`
- Create: `src/main/java/com/ulticode/modules/admin/dto/AuditLogQueryDTO.java`
- Create: `src/main/java/com/ulticode/modules/admin/dto/AuditStatsVO.java`
- Create: `src/main/java/com/ulticode/modules/admin/service/AuditService.java`
- Create: `src/main/java/com/ulticode/modules/admin/service/impl/AuditServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/admin/controller/AuditController.java`
- Test: `src/test/java/com/ulticode/modules/admin/service/AuditServiceTest.java`

### Step 1.1: 创建 AuditLog Entity

```java
package com.ulticode.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("audit_logs")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String performerId;
    private String userId;
    private String action;
    private String entityType;
    private String entityId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> oldValues;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> newValues;

    private String ipAddress;
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Step 1.2: 创建 AuditLogMapper

```java
package com.ulticode.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.admin.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT entity_type, COUNT(*) as count FROM audit_logs " +
            "WHERE (${whereClause}) GROUP BY entity_type ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> countByEntityType(@Param("whereClause") String whereClause);

    @Select("SELECT performer_id, COUNT(*) as count FROM audit_logs " +
            "WHERE (${whereClause}) GROUP BY performer_id ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> countByPerformer(@Param("whereClause") String whereClause);
}
```

### Step 1.3: 创建 DTO

```java
// AuditLogVO.java
package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditLogVO {
    private String id;
    private String performerId;
    private String performerName;
    private String performerUsername;
    private String userId;
    private String userName;
    private String userUsername;
    private String action;
    private String entityType;
    private String entityId;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}

// AuditLogQueryDTO.java
package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogQueryDTO {
    private String performerId;
    private String userId;
    private String entityType;
    private String entityId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer page = 1;
    private Integer limit = 50;
}

// AuditStatsVO.java
package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AuditStatsVO {
    private Long totalActions;
    private List<Map<String, Object>> actionsByEntity;
    private List<Map<String, Object>> actionsByPerformer;
    private List<Map<String, Object>> topPerformers;
}
```

### Step 1.4: 创建 AuditService

```java
package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.common.response.PageResult;
import java.util.Map;

public interface AuditService {
    AuditLog log(String performerId, String userId, String action,
                 String entityType, String entityId,
                 Map<String, Object> oldValues, Map<String, Object> newValues,
                 String ipAddress, String userAgent);

    PageResult<AuditLogVO> getAuditLogs(AuditLogQueryDTO query);
    AuditStatsVO getAuditStats(AuditLogQueryDTO query);
}
```

### Step 1.5: 实现 AuditServiceImpl

```java
package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    @Override
    public AuditLog log(String performerId, String userId, String action,
                         String entityType, String entityId,
                         Map<String, Object> oldValues, Map<String, Object> newValues,
                         String ipAddress, String userAgent) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPerformerId(performerId);
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId != null ? entityId : "N/A");
        auditLog.setOldValues(oldValues);
        auditLog.setNewValues(newValues);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);

        auditLogMapper.insert(auditLog);
        log.debug("Audit log created: {} by {}", action, performerId);
        return auditLog;
    }

    @Override
    public PageResult<AuditLogVO> getAuditLogs(AuditLogQueryDTO query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getPerformerId() != null) {
            wrapper.eq(AuditLog::getPerformerId, query.getPerformerId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AuditLog::getUserId, query.getUserId());
        }
        if (query.getEntityType() != null) {
            wrapper.eq(AuditLog::getEntityType, query.getEntityType());
        }
        if (query.getEntityId() != null) {
            wrapper.eq(AuditLog::getEntityId, query.getEntityId());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(AuditLog::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(AuditLog::getCreatedAt, query.getEndDate());
        }

        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> page = new Page<>(query.getPage(), query.getLimit());
        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);

        // Collect user IDs to batch fetch
        Set<String> userIds = new HashSet<>();
        result.getRecords().forEach(log -> {
            if (log.getPerformerId() != null) userIds.add(log.getPerformerId());
            if (log.getUserId() != null) userIds.add(log.getUserId());
        });

        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        List<AuditLogVO> voList = result.getRecords().stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public AuditStatsVO getAuditStats(AuditLogQueryDTO query) {
        AuditStatsVO stats = new AuditStatsVO();

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getStartDate() != null) {
            wrapper.ge(AuditLog::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(AuditLog::getCreatedAt, query.getEndDate());
        }
        if (query.getPerformerId() != null) {
            wrapper.eq(AuditLog::getPerformerId, query.getPerformerId());
        }

        stats.setTotalActions(auditLogMapper.selectCount(wrapper));

        // Group by entity type
        wrapper.groupBy(AuditLog::getEntityType);
        List<Map<String, Object>> entityStats = auditLogMapper.selectMaps(
            wrapper.select("entity_type as entityType", "COUNT(*) as count")
        );
        stats.setActionsByEntity(entityStats);

        return stats;
    }

    private AuditLogVO toVO(AuditLog auditLog, Map<String, User> userMap) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(auditLog.getId());
        vo.setPerformerId(auditLog.getPerformerId());
        vo.setUserId(auditLog.getUserId());
        vo.setAction(auditLog.getAction());
        vo.setEntityType(auditLog.getEntityType());
        vo.setEntityId(auditLog.getEntityId());
        vo.setOldValues(auditLog.getOldValues());
        vo.setNewValues(auditLog.getNewValues());
        vo.setIpAddress(auditLog.getIpAddress());
        vo.setUserAgent(auditLog.getUserAgent());
        vo.setCreatedAt(auditLog.getCreatedAt());

        User performer = userMap.get(auditLog.getPerformerId());
        if (performer != null) {
            vo.setPerformerName(performer.getName());
            vo.setPerformerUsername(performer.getUsername());
        }

        User user = userMap.get(auditLog.getUserId());
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserUsername(user.getUsername());
        }

        return vo;
    }
}
```

### Step 1.6: 创建 AuditController

```java
package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Audit", description = "审计日志管理接口")
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "获取审计日志列表")
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AuditLogVO>> getAuditLogs(AuditLogQueryDTO query) {
        return Result.success(auditService.getAuditLogs(query));
    }

    @Operation(summary = "获取审计统计")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AuditStatsVO> getAuditStats(AuditLogQueryDTO query) {
        return Result.success(auditService.getAuditStats(query));
    }
}
```

- [ ] **Step 1.7: 编写单元测试**
- [ ] **Step 1.8: 运行测试验证**
- [ ] **Step 1.9: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/admin/
git add backend-spring/src/test/java/com/ulticode/modules/admin/
git commit -m "feat(admin): 添加审计日志模块"
```

---

## Task 2: Moderation 模块 - 内容审核

**Files:**
- Create: `src/main/java/com/ulticode/modules/moderation/entity/ModerationQueue.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/ModerationAction.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/Report.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/Appeal.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/enums/ModerationStatus.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/enums/ModerationActionType.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/enums/ReportCategory.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/enums/ReportStatus.java`
- Create: `src/main/java/com/ulticode/modules/moderation/entity/enums/AppealStatus.java`
- Create: `src/main/java/com/ulticode/modules/moderation/mapper/ModerationQueueMapper.java`
- Create: `src/main/java/com/ulticode/modules/moderation/mapper/ModerationActionMapper.java`
- Create: `src/main/java/com/ulticode/modules/moderation/mapper/ReportMapper.java`
- Create: `src/main/java/com/ulticode/modules/moderation/dto/*.java` (6 DTOs)
- Create: `src/main/java/com/ulticode/modules/moderation/service/ModerationService.java`
- Create: `src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/moderation/controller/ModerationController.java`
- Test: `src/test/java/com/ulticode/modules/moderation/service/ModerationServiceTest.java`

### Step 2.1: 创建枚举类

```java
// ModerationStatus.java - 与 Prisma schema 一致
package com.ulticode.modules.moderation.entity.enums;

public enum ModerationStatus {
    PENDING, UNDER_REVIEW, RESOLVED, DISMISSED, APPEAL_PENDING
}

// ModerationActionType.java - 与 Prisma schema 一致
package com.ulticode.modules.moderation.entity.enums;

public enum ModerationActionType {
    DELETED, HIDDEN, RESTORED, WARNED, TEMP_BANNED, PERM_BANNED,
    DISMISSED, RESOLVED, APPEAL_PENDING, APPEAL_APPROVED, APPEAL_REJECTED
}

// ReportCategory.java - 与 Prisma schema 一致
package com.ulticode.modules.moderation.entity.enums;

public enum ReportCategory {
    SPAM, HARASSMENT, HATE_SPEECH, VIOLENCE, SEXUAL_CONTENT,
    MISINFORMATION, WRONG_ANSWER, COPYRIGHT, OTHER
}

// ReportStatus.java - 与 Prisma schema 一致
package com.ulticode.modules.moderation.entity.enums;

public enum ReportStatus {
    PENDING, REVIEWED, RESOLVED, DISMISSED
}

// AppealStatus.java - 与 Prisma schema 一致
package com.ulticode.modules.moderation.entity.enums;

public enum AppealStatus {
    PENDING, UNDER_REVIEW, APPROVED, REJECTED
}
```

### Step 2.2: 创建 Entity

```java
// ModerationQueue.java
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;
import com.ulticode.modules.moderation.entity.enums.ReportCategory;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("moderation_queue")
public class ModerationQueue {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String entityType;
    private String entityId;
    private String authorId;
    private Integer priority;

    @TableField("status")
    private String status; // Use String for DB compatibility

    private Integer reportCount;

    @TableField("primary_category")
    private String primaryCategory;

    private String assignedToId;
    private LocalDateTime assignedAt;

    private String reviewedById;
    private LocalDateTime reviewedAt;

    private String resolution;
    private String resolutionNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;
}

// Report.java
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reports")
public class Report {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String reporterId;
    private String entityType;
    private String entityId;

    @TableField("category")
    private String category;

    private String reason;
    private String evidence;

    @TableField("status")
    private String status;

    private String queueId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// Appeal.java
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("appeals")
public class Appeal {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String queueId;
    private String appellantId;
    private String reason;
    private String evidence;

    @TableField("status")
    private String status;

    private String reviewedById;
    private LocalDateTime reviewedAt;
    private String response;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// ModerationAction.java
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("moderation_actions")
public class ModerationAction {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String queueId;

    @TableField("action")
    private String action;

    private String performedById;
    private String note;
    private Integer durationDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

// UserWarning.java - 用户警告记录
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_warnings")
public class UserWarning {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String queueId;
    private String reason;
    private String issuedById;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}

// UserBan.java - 用户封禁记录
package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_bans")
public class UserBan {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String queueId;
    private String reason;
    private String issuedById;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
    private Boolean isPermanent;
}
```

### Step 2.3: 创建 Mapper

```java
// ModerationQueueMapper.java
package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.ModerationQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ModerationQueueMapper extends BaseMapper<ModerationQueue> {

    @Select("SELECT * FROM moderation_queue WHERE status = #{status} ORDER BY priority DESC, created_at ASC")
    List<ModerationQueue> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM moderation_queue WHERE entity_type = #{entityType} AND entity_id = #{entityId}")
    ModerationQueue findByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Update("UPDATE moderation_queue SET assigned_to_id = #{assignedTo}, assigned_at = NOW(), status = 'IN_REVIEW' WHERE id = #{id}")
    int assignToModerator(@Param("id") String id, @Param("assignedTo") String assignedTo);

    @Update("UPDATE moderation_queue SET assigned_to_id = NULL, assigned_at = NULL WHERE id = #{id}")
    int unassign(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM moderation_queue WHERE status = 'PENDING'")
    long countPending();

    @Select("SELECT COUNT(*) FROM moderation_queue WHERE status = 'IN_REVIEW'")
    long countInReview();
}

// UserWarningMapper.java
package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.UserWarning;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserWarningMapper extends BaseMapper<UserWarning> {

    @Select("SELECT * FROM user_warnings WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserWarning> findByUserId(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM user_warnings WHERE user_id = #{userId} AND (expires_at IS NULL OR expires_at > NOW())")
    long countActiveWarnings(@Param("userId") String userId);
}

// UserBanMapper.java
package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface UserBanMapper extends BaseMapper<UserBan> {

    @Select("SELECT * FROM user_bans WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserBan> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_bans WHERE user_id = #{userId} AND (is_permanent = 1 OR expires_at > NOW()) ORDER BY created_at DESC LIMIT 1")
    Optional<UserBan> findActiveBan(@Param("userId") String userId);
}
```

### Step 2.4: 创建 DTO

```java
// QueryModerationQueueDTO.java
package com.ulticode.modules.moderation.dto;

import lombok.Data;

@Data
public class QueryModerationQueueDTO {
    private String status;
    private String entityType;
    private String assignedTo;
    private Integer page = 1;
    private Integer limit = 20;
}

// ModerationQueueVO.java
package com.ulticode.modules.moderation.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ModerationQueueVO {
    private String id;
    private String entityType;
    private String entityId;
    private String authorId;
    private String authorName;
    private Integer priority;
    private String status;
    private Integer reportCount;
    private String primaryCategory;
    private String assignedToId;
    private String assignedToName;
    private LocalDateTime assignedAt;
    private String resolution;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}

// PerformModerationActionDTO.java
package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PerformModerationActionDTO {
    @NotBlank
    private String action;
    private String note;
    private Integer durationDays;
}

// CreateReportDTO.java
package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReportDTO {
    @NotBlank
    private String entityType;
    @NotBlank
    private String entityId;
    @NotBlank
    private String category;
    private String reason;
    private String evidence;
}

// ModerationStatsVO.java
package com.ulticode.modules.moderation.dto;

import lombok.Data;

@Data
public class ModerationStatsVO {
    private long pendingCount;
    private long inReviewCount;
    private long resolvedToday;
    private long avgResolutionTime;
}
```

### Step 2.5: 创建 ModerationService

```java
package com.ulticode.modules.moderation.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.*;
import java.util.List;

public interface ModerationService {
    PageResult<ModerationQueueVO> getQueueItems(QueryModerationQueueDTO query);
    ModerationQueueVO getQueueItem(String id);
    ModerationStatsVO getStats();

    ModerationQueueVO claimItem(String id, String moderatorId);
    ModerationQueueVO assignItem(String id, String moderatorId, String assignedTo);
    ModerationQueueVO unassignItem(String id, String moderatorId);
    ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId);

    void createReport(CreateReportDTO dto, String reporterId);
    List<ReportVO> getReportsForEntity(String entityType, String entityId);
}
```

### Step 2.6: 创建 ModerationController

```java
package com.ulticode.modules.moderation.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Moderation", description = "内容审核接口")
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class ModerationController {

    private final ModerationService moderationService;

    @Operation(summary = "获取审核队列")
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ModerationQueueVO>> getQueue(QueryModerationQueueDTO query) {
        return Result.success(moderationService.getQueueItems(query));
    }

    @Operation(summary = "获取审核统计")
    @GetMapping("/queue/stats")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationStatsVO> getStats() {
        return Result.success(moderationService.getStats());
    }

    @Operation(summary = "获取审核项详情")
    @GetMapping("/queue/{id}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> getQueueItem(@PathVariable String id) {
        return Result.success(moderationService.getQueueItem(id));
    }

    @Operation(summary = "认领审核项")
    @PostMapping("/queue/{id}/claim")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> claim(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.claimItem(id, moderatorId));
    }

    @Operation(summary = "分配审核项")
    @PostMapping("/queue/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> assign(@PathVariable String id, @RequestBody AssignDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.assignItem(id, moderatorId, dto.getAssignedTo()));
    }

    @Operation(summary = "取消分配")
    @PatchMapping("/queue/{id}/unassign")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> unassign(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.unassignItem(id, moderatorId));
    }

    @Operation(summary = "执行审核操作")
    @PostMapping("/queue/{id}/action")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> performAction(
            @PathVariable String id,
            @Valid @RequestBody PerformModerationActionDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.performAction(id, dto, moderatorId));
    }

    @Operation(summary = "创建举报")
    @PostMapping("/reports")
    public Result<Void> createReport(@Valid @RequestBody CreateReportDTO dto) {
        String reporterId = SecurityUtil.getCurrentUserId();
        moderationService.createReport(dto, reporterId);
        return Result.success();
    }

    @Operation(summary = "按实体查找审核项")
    @GetMapping("/queue/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> findByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return Result.success(moderationService.findByEntity(entityType, entityId));
    }

    @Operation(summary = "批量审核操作")
    @PostMapping("/queue/batch-action")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BatchActionResultVO> batchAction(@Valid @RequestBody BatchModerationActionDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.batchAction(dto, moderatorId));
    }
}
```

- [ ] **Step 2.7: 实现 ModerationServiceImpl**
- [ ] **Step 2.8: 编写单元测试**
- [ ] **Step 2.9: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/moderation/
git commit -m "feat(moderation): 添加内容审核模块"
```

---

## Task 3: Notification 模块

**Files:**
- Create: `src/main/java/com/ulticode/modules/notification/entity/Notification.java`
- Create: `src/main/java/com/ulticode/modules/notification/entity/NotificationPreference.java`
- Create: `src/main/java/com/ulticode/modules/notification/entity/enums/NotificationType.java`
- Create: `src/main/java/com/ulticode/modules/notification/entity/enums/NotificationCategory.java`
- Create: `src/main/java/com/ulticode/modules/notification/mapper/NotificationMapper.java`
- Create: `src/main/java/com/ulticode/modules/notification/mapper/NotificationPreferenceMapper.java`
- Create: `src/main/java/com/ulticode/modules/notification/dto/*.java` (5 DTOs)
- Create: `src/main/java/com/ulticode/modules/notification/service/NotificationService.java`
- Create: `src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/notification/controller/NotificationController.java`
- Test: `src/test/java/com/ulticode/modules/notification/service/NotificationServiceTest.java`

### Step 3.1: 创建 Entity

```java
// Notification.java
package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "notifications", autoResultMap = true)
public class Notification {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    @TableField("type")
    private String type;

    @TableField("category")
    private String category;

    private String title;
    private String body;
    private String link;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private Boolean isRead;
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// NotificationPreference.java
package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_preferences")
public class NotificationPreference {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Step 3.2: 创建枚举

```java
// NotificationType.java - 与 Prisma schema 一致
package com.ulticode.modules.notification.entity.enums;

public enum NotificationType {
    COMMENT, REPLY, MENTION, UPVOTE, FOLLOW, SYSTEM, SUBMISSION, CONTEST
}

// NotificationCategory.java - 与 Prisma schema 一致
package com.ulticode.modules.notification.entity.enums;

public enum NotificationCategory {
    COMMUNICATION, MARKETING, SECURITY, SYSTEM
}
```

### Step 3.3: 创建 DTO

```java
// NotificationVO.java
package com.ulticode.modules.notification.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class NotificationVO {
    private String id;
    private String type;
    private String category;
    private String title;
    private String body;
    private String link;
    private Map<String, Object> metadata;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}

// NotificationQueryDTO.java
package com.ulticode.modules.notification.dto;

import lombok.Data;

@Data
public class NotificationQueryDTO {
    private String type;
    private String category;
    private Boolean isRead;
    private Integer page = 1;
    private Integer limit = 20;
}

// UpdateNotificationDTO.java
package com.ulticode.modules.notification.dto;

import lombok.Data;

@Data
public class UpdateNotificationDTO {
    private Boolean isRead;
}

// NotificationPreferenceVO.java
package com.ulticode.modules.notification.dto;

import lombok.Data;

@Data
public class NotificationPreferenceVO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;
}

// UpdateNotificationPreferenceDTO.java
package com.ulticode.modules.notification.dto;

import lombok.Data;

@Data
public class UpdateNotificationPreferenceDTO {
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;
}
```

### Step 3.4: 创建 NotificationController

```java
package com.ulticode.modules.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.notification.dto.*;
import com.ulticode.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "通知管理接口")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取通知列表")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(NotificationQueryDTO query) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.list(userId, query));
    }

    @Operation(summary = "获取未读通知数量")
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> getUnreadCount() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "获取通知偏好")
    @GetMapping("/preferences")
    public Result<NotificationPreferenceVO> getPreferences() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.getPreferences(userId));
    }

    @Operation(summary = "更新通知偏好")
    @PatchMapping("/preferences")
    public Result<NotificationPreferenceVO> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.updatePreferences(userId, dto));
    }

    @Operation(summary = "标记所有通知为已读")
    @PostMapping("/mark-all-read")
    public Result<Void> markAllRead() {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.markAllRead(userId);
        return Result.success();
    }

    @Operation(summary = "删除所有通知")
    @DeleteMapping("/clear")
    public Result<Void> clearAll() {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.clearAll(userId);
        return Result.success();
    }

    @Operation(summary = "更新单个通知")
    @PatchMapping("/{id}")
    public Result<NotificationVO> updateNotification(
            @PathVariable String id,
            @Valid @RequestBody UpdateNotificationDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(notificationService.updateNotification(userId, id, dto));
    }

    @Operation(summary = "删除单个通知")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        notificationService.deleteNotification(userId, id);
        return Result.success();
    }
}
```

- [ ] **Step 3.5: 实现 NotificationServiceImpl**
- [ ] **Step 3.6: 编写单元测试**
- [ ] **Step 3.7: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/notification/
git commit -m "feat(notification): 添加通知模块"
```

---

## Task 4: Bookmark 模块

**Files:**
- Create: `src/main/java/com/ulticode/modules/bookmark/entity/BookmarkFolder.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/entity/Bookmark.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/entity/enums/BookmarkType.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/mapper/BookmarkFolderMapper.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/mapper/BookmarkMapper.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/dto/*.java` (6 DTOs)
- Create: `src/main/java/com/ulticode/modules/bookmark/service/BookmarkService.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/service/impl/BookmarkServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/bookmark/controller/BookmarkController.java`
- Test: `src/test/java/com/ulticode/modules/bookmark/service/BookmarkServiceTest.java`

### Step 4.1: 创建 Entity

```java
// BookmarkFolder.java
package com.ulticode.modules.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collections")
public class BookmarkFolder {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// Bookmark.java
package com.ulticode.modules.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collection_items")
public class Bookmark {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("collection_id")
    private String folderId;

    private String targetId;

    @TableField("target_type")
    private String targetType;

    private Integer sortOrder;
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Step 4.2: 创建 BookmarkController

```java
package com.ulticode.modules.bookmark.controller;

import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.bookmark.dto.*;
import com.ulticode.modules.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bookmark", description = "收藏夹接口")
@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "快速收藏/取消收藏")
    @PostMapping("/quick")
    public Result<QuickFavoriteVO> quickFavorite(@Valid @RequestBody QuickFavoriteDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        boolean isSaved = bookmarkService.quickFavorite(userId, dto.getTargetType(), dto.getTargetId());
        return Result.success(new QuickFavoriteVO(isSaved));
    }

    @Operation(summary = "获取用户收藏夹列表")
    @GetMapping("/folders")
    public Result<List<BookmarkFolderVO>> getUserFolders() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getUserFolders(userId));
    }

    @Operation(summary = "获取收藏夹详情")
    @GetMapping("/folders/{id}")
    public Result<BookmarkFolderDetailVO> getFolder(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getFolderWithBookmarks(userId, id));
    }

    @Operation(summary = "创建收藏夹")
    @PostMapping("/folders")
    public Result<BookmarkFolderVO> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.createFolder(userId, dto));
    }

    @Operation(summary = "更新收藏夹")
    @PatchMapping("/folders/{id}")
    public Result<BookmarkFolderVO> updateFolder(
            @PathVariable String id,
            @Valid @RequestBody UpdateFolderDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.updateFolder(userId, id, dto));
    }

    @Operation(summary = "删除收藏夹")
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.deleteFolder(userId, id);
        return Result.success();
    }

    @Operation(summary = "添加收藏")
    @PostMapping("/folders/{folderId}/items")
    public Result<BookmarkVO> addBookmark(
            @PathVariable String folderId,
            @Valid @RequestBody AddBookmarkDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.addBookmark(userId, folderId, dto));
    }

    @Operation(summary = "删除收藏")
    @DeleteMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<Void> removeBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.removeBookmark(userId, folderId, bookmarkId);
        return Result.success();
    }

    @Operation(summary = "按目标删除收藏")
    @DeleteMapping("/folders/{folderId}/items/target/{targetType}/{targetId}")
    public Result<Void> removeBookmarkByTarget(
            @PathVariable String folderId,
            @PathVariable String targetType,
            @PathVariable String targetId) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.removeBookmarkByTarget(userId, folderId, targetType, targetId);
        return Result.success();
    }

    @Operation(summary = "更新收藏")
    @PatchMapping("/folders/{folderId}/items/{bookmarkId}")
    public Result<BookmarkVO> updateBookmark(
            @PathVariable String folderId,
            @PathVariable String bookmarkId,
            @Valid @RequestBody UpdateBookmarkDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.updateBookmark(userId, folderId, bookmarkId, dto));
    }

    @Operation(summary = "获取项目的收藏夹列表")
    @GetMapping("/item/{targetType}/{targetId}")
    public Result<List<BookmarkFolderVO>> getBookmarkFolders(
            @PathVariable String targetType,
            @PathVariable String targetId) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(bookmarkService.getBookmarkFolders(userId, targetType, targetId));
    }

    @Operation(summary = "重排序收藏夹")
    @PostMapping("/folders/reorder")
    public Result<Void> reorderFolders(@Valid @RequestBody ReorderFoldersDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        bookmarkService.reorderFolders(userId, dto.getFolderIds());
        return Result.success();
    }
}
```

- [ ] **Step 4.3: 实现 BookmarkServiceImpl**
- [ ] **Step 4.4: 编写单元测试**
- [ ] **Step 4.5: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/bookmark/
git commit -m "feat(bookmark): 添加收藏夹模块"
```

---

## Task 5: ProblemList 模块

**Files:**
- Create: `src/main/java/com/ulticode/modules/problemlist/entity/ProblemList.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/entity/ProblemListProblemRelation.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/entity/ProblemListCategory.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/mapper/ProblemListMapper.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/mapper/ProblemListProblemMapper.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/mapper/ProblemListCategoryMapper.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/dto/*.java` (8 DTOs)
- Create: `src/main/java/com/ulticode/modules/problemlist/service/ProblemListService.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/problemlist/controller/ProblemListController.java`
- Test: `src/test/java/com/ulticode/modules/problemlist/service/ProblemListServiceTest.java`

### Step 5.1: 创建 Entity

```java
// ProblemList.java
package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("problem_lists")
public class ProblemList {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String description;
    private String authorId;
    private Boolean isPublic;
    private Boolean isFeatured;
    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// ProblemListProblemRelation.java
package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("problem_list_problem_relations")
public class ProblemListProblemRelation {

    @TableField("list_id")
    private String listId;

    private Long problemId;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;
}

// ProblemListCategory.java - 用户自定义分类
// NOTE: This table does NOT exist in the current Prisma schema.
// Implementation requires creating this table via database migration:
// CREATE TABLE problem_list_categories (
//   id VARCHAR(36) PRIMARY KEY,
//   user_id VARCHAR(36) NOT NULL,
//   name VARCHAR(100) NOT NULL,
//   description TEXT,
//   icon VARCHAR(50),
//   color VARCHAR(20),
//   sort_order INT DEFAULT 0,
//   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
//   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
// );
@Data
@TableName("problem_list_categories")
public class ProblemListCategory {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

// ProblemListBookmark.java - 保存的题单
// NOTE: This table does NOT exist in the current Prisma schema.
// Implementation requires creating this table via database migration:
// CREATE TABLE problem_list_bookmarks (
//   id VARCHAR(36) PRIMARY KEY,
//   user_id VARCHAR(36) NOT NULL,
//   list_id VARCHAR(36) NOT NULL,
//   category_id VARCHAR(36),
//   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
// );
@Data
@TableName("problem_list_bookmarks")
public class ProblemListBookmark {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String listId;
    private String categoryId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Step 5.2: 创建 ProblemListController

```java
package com.ulticode.modules.problemlist.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.problemlist.dto.*;
import com.ulticode.modules.problemlist.service.ProblemListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ProblemList", description = "题单接口")
@RestController
@RequestMapping("/api/problem-lists")
@RequiredArgsConstructor
public class ProblemListController {

    private final ProblemListService problemListService;

    @Operation(summary = "获取题单概览")
    @GetMapping("/overview")
    public Result<UserProblemListsVO> getOverview(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        if (userId != null) {
            return Result.success(problemListService.getUserProblemLists(userId));
        }
        return Result.success(problemListService.findAll(locale));
    }

    @Operation(summary = "获取题单详情")
    @GetMapping("/{id}/overview")
    public Result<ProblemListDetailVO> getListOverview(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return Result.success(problemListService.getListOverview(id, userId, locale));
    }

    @Operation(summary = "创建题单")
    @PostMapping
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> createList(
            @RequestParam String userId,
            @Valid @RequestBody CreateProblemListDTO dto) {
        return Result.success(problemListService.createList(userId, dto));
    }

    @Operation(summary = "更新题单")
    @PatchMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> updateList(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody UpdateProblemListDTO dto) {
        return Result.success(problemListService.updateList(id, userId, dto));
    }

    @Operation(summary = "删除题单")
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteList(
            @PathVariable String id,
            @RequestParam String userId) {
        problemListService.deleteList(id, userId);
        return Result.success();
    }

    @Operation(summary = "Fork题单")
    @PostMapping("/{id}/fork")
    @SecurityRequirement(name = "Bearer")
    public Result<ForkResultVO> forkList(
            @PathVariable String id,
            @RequestParam String userId) {
        String newListId = problemListService.forkList(id, userId);
        return Result.success(new ForkResultVO(newListId));
    }

    @Operation(summary = "添加题目到题单")
    @PostMapping("/{id}/problems")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> addProblem(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody AddProblemToListDTO dto) {
        problemListService.addProblem(id, userId, dto.getProblemId());
        return Result.success();
    }

    @Operation(summary = "从题单移除题目")
    @DeleteMapping("/{id}/problems/{problemId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> removeProblem(
            @PathVariable String id,
            @PathVariable Long problemId,
            @RequestParam String userId) {
        problemListService.removeProblem(id, userId, problemId);
        return Result.success();
    }

    @Operation(summary = "保存题单")
    @PostMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> saveList(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestBody(required = false) SaveListDTO dto) {
        problemListService.saveList(userId, id, dto != null ? dto.getCategoryId() : null);
        return Result.success();
    }

    @Operation(summary = "取消保存题单")
    @DeleteMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> unsaveList(
            @PathVariable String id,
            @RequestParam String userId) {
        problemListService.unsaveList(userId, id);
        return Result.success();
    }

    @Operation(summary = "获取用户对题目的题单状态")
    @GetMapping("/problems/{problemId}/user-lists")
    @SecurityRequirement(name = "Bearer")
    public Result<UserListsForProblemVO> getUserListsForProblem(
            @PathVariable Long problemId,
            @RequestParam String userId) {
        return Result.success(problemListService.getUserListsForProblem(userId, problemId));
    }

    @Operation(summary = "批量添加题目到题单")
    @PostMapping("/problems/{problemId}/batch-add")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchAddProblemToLists(
            @PathVariable Long problemId,
            @RequestParam String userId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        problemListService.batchAddProblemToLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "批量从题单移除题目")
    @PostMapping("/problems/{problemId}/batch-remove")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchRemoveProblemFromLists(
            @PathVariable Long problemId,
            @RequestParam String userId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        problemListService.batchRemoveProblemFromLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "移动题单到分类")
    @PatchMapping("/{id}/category")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> moveListToCategory(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody MoveListToCategoryDTO dto) {
        problemListService.moveListToCategory(userId, id, dto.getCategoryId());
        return Result.success();
    }

    // ==================== Category Management ====================

    @Operation(summary = "创建分类")
    @PostMapping("/categories")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> createCategory(
            @RequestParam String userId,
            @Valid @RequestBody CreateCategoryDTO dto) {
        return Result.success(problemListService.createCategory(userId, dto));
    }

    @Operation(summary = "更新分类")
    @PatchMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> updateCategory(
            @PathVariable String categoryId,
            @RequestParam String userId,
            @Valid @RequestBody UpdateCategoryDTO dto) {
        return Result.success(problemListService.updateCategory(categoryId, userId, dto));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteCategory(
            @PathVariable String categoryId,
            @RequestParam String userId) {
        problemListService.deleteCategory(categoryId, userId);
        return Result.success();
    }
}
```

- [ ] **Step 5.3: 实现 ProblemListServiceImpl**
- [ ] **Step 5.4: 编写单元测试**
- [ ] **Step 5.5: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/problemlist/
git commit -m "feat(problem-list): 添加题单模块"
```

---

## Task 6: 集成测试与验收

**Files:**
- Create: `src/test/java/com/ulticode/modules/admin/controller/AdminControllerTest.java`
- Create: `src/test/java/com/ulticode/modules/moderation/controller/ModerationControllerTest.java`
- Create: `src/test/java/com/ulticode/modules/notification/controller/NotificationControllerTest.java`
- Create: `src/test/java/com/ulticode/modules/bookmark/controller/BookmarkControllerTest.java`
- Create: `src/test/java/com/ulticode/modules/problemlist/controller/ProblemListControllerTest.java`

### Step 6.1: 运行所有测试

```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring
./mvnw test
```

### Step 6.2: 验证编译

```bash
./mvnw compile
```

### Step 6.3: Final Commit

```bash
git add backend-spring/
git commit -m "feat(phase4): 完成 Admin、Moderation、Notification、Bookmark、ProblemList 模块"
```

---

## 验收检查清单

- [ ] Admin 模块: 审计日志 CRUD 正常
- [ ] Moderation 模块: 内容审核流程正常
- [ ] Notification 模块: 通知管理正常
- [ ] Bookmark 模块: 收藏夹功能正常
- [ ] ProblemList 模块: 题单管理正常
- [ ] 所有 API 返回格式与 NestJS 兼容
- [ ] 所有错误码与 NestJS 一致
- [ ] 权限验证正常 (Role-based access)
- [ ] 单元测试覆盖率达到 80%+

---

## 文件总览

```
backend-spring/src/main/java/com/ulticode/modules/
├── admin/
│   ├── controller/
│   │   └── AuditController.java
│   ├── dto/
│   │   ├── AuditLogVO.java
│   │   ├── AuditLogQueryDTO.java
│   │   └── AuditStatsVO.java
│   ├── entity/
│   │   └── AuditLog.java
│   ├── mapper/
│   │   └── AuditLogMapper.java
│   └── service/
│       ├── AuditService.java
│       └── impl/AuditServiceImpl.java
├── moderation/
│   ├── controller/
│   │   ├── ModerationController.java
│   │   ├── ReportController.java
│   │   └── AppealController.java
│   ├── dto/
│   │   ├── QueryModerationQueueDTO.java
│   │   ├── ModerationQueueVO.java
│   │   ├── PerformModerationActionDTO.java
│   │   ├── BatchModerationActionDTO.java
│   │   ├── BatchActionResultVO.java
│   │   ├── AssignDTO.java
│   │   ├── CreateReportDTO.java
│   │   ├── QueryReportsDTO.java
│   │   ├── ReportVO.java
│   │   ├── CreateAppealDTO.java
│   │   ├── QueryAppealsDTO.java
│   │   ├── ReviewAppealDTO.java
│   │   ├── AppealVO.java
│   │   └── ModerationStatsVO.java
│   ├── entity/
│   │   ├── ModerationQueue.java
│   │   ├── ModerationAction.java
│   │   ├── Report.java
│   │   ├── Appeal.java
│   │   ├── UserWarning.java
│   │   ├── UserBan.java
│   │   └── enums/*.java (5 files)
│   ├── mapper/
│   │   ├── ModerationQueueMapper.java
│   │   ├── ModerationActionMapper.java
│   │   ├── ReportMapper.java
│   │   ├── AppealMapper.java
│   │   ├── UserWarningMapper.java
│   │   └── UserBanMapper.java
│   └── service/
│       ├── ModerationService.java
│       ├── ReportService.java
│       ├── AppealService.java
│       └── impl/ModerationServiceImpl.java
├── notification/
│   ├── controller/
│   │   └── NotificationController.java
│   ├── dto/
│   │   ├── NotificationVO.java
│   │   ├── NotificationQueryDTO.java
│   │   ├── UpdateNotificationDTO.java
│   │   ├── NotificationPreferenceVO.java
│   │   ├── UpdateNotificationPreferenceDTO.java
│   │   └── UnreadCountVO.java
│   ├── entity/
│   │   ├── Notification.java
│   │   ├── NotificationPreference.java
│   │   └── enums/*.java (2 files)
│   ├── mapper/
│   │   ├── NotificationMapper.java
│   │   └── NotificationPreferenceMapper.java
│   └── service/
│       ├── NotificationService.java
│       └── impl/NotificationServiceImpl.java
├── bookmark/
│   ├── controller/
│   │   └── BookmarkController.java
│   ├── dto/
│   │   ├── BookmarkFolderVO.java
│   │   ├── BookmarkFolderDetailVO.java
│   │   ├── CreateFolderDTO.java
│   │   ├── UpdateFolderDTO.java
│   │   ├── AddBookmarkDTO.java
│   │   ├── QuickFavoriteDTO.java
│   │   └── QuickFavoriteVO.java
│   ├── entity/
│   │   ├── BookmarkFolder.java
│   │   ├── Bookmark.java
│   │   └── enums/BookmarkType.java
│   ├── mapper/
│   │   ├── BookmarkFolderMapper.java
│   │   └── BookmarkMapper.java
│   └── service/
│       ├── BookmarkService.java
│       └── impl/BookmarkServiceImpl.java
└── problemlist/
    ├── controller/
    │   └── ProblemListController.java
    ├── dto/
    │   ├── ProblemListSummaryVO.java
    │   ├── ProblemListDetailVO.java
    │   ├── UserProblemListsVO.java
    │   ├── UserListsForProblemVO.java
    │   ├── ForkResultVO.java
    │   ├── CreateProblemListDTO.java
    │   ├── UpdateProblemListDTO.java
    │   ├── AddProblemToListDTO.java
    │   ├── SaveListDTO.java
    │   ├── BatchAddToListsDTO.java
    │   ├── MoveListToCategoryDTO.java
    │   ├── CreateCategoryDTO.java
    │   ├── UpdateCategoryDTO.java
    │   └── CategorySummaryVO.java
    ├── entity/
    │   ├── ProblemList.java
    │   ├── ProblemListProblemRelation.java
    │   ├── ProblemListCategory.java
    │   └── ProblemListBookmark.java
    ├── mapper/
    │   ├── ProblemListMapper.java
    │   ├── ProblemListProblemMapper.java
    │   ├── ProblemListCategoryMapper.java
    │   └── ProblemListBookmarkMapper.java
    └── service/
        ├── ProblemListService.java
        ├── ProblemListCategoryService.java
        └── impl/ProblemListServiceImpl.java
```

---

## 依赖关系

```
Phase 4 依赖:
├── Phase 1 基础设施
│   ├── common/exception/ErrorCode.java
│   ├── common/response/Result.java
│   ├── common/util/SecurityUtil.java
│   └── security/* (JWT 认证)
├── Phase 2 核心模块
│   └── user/* (用户信息)
└── Phase 3 高级功能
    ├── contest/* (竞赛排名)
    ├── forum/* (论坛帖子)
    └── websocket/* (实时通知)
```
