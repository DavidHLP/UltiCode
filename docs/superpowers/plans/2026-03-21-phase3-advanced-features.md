# NestJS 到 Spring Boot 迁移 - Phase 3: 高级功能

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现高级功能模块，包括 Contest（竞赛）、Forum（论坛）、WebSocket 实时通信，使前端可以进行完整的功能测试。

**Architecture:** 采用标准的 Controller → Service → Mapper 三层架构，使用 MyBatis-Plus 进行数据访问。WebSocket 使用 Spring WebSocket + STOMP 协议。

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus 3.5.5, Spring WebSocket, STOMP, Redis (Pub/Sub)

---

## 前置条件

- Phase 1 基础设施层已完成 ✅
- Phase 2 核心业务模块已完成 ✅
- 数据库已存在 (与 NestJS 共享)
- Redis 已配置

---

## 模块概览

| 模块 | 功能 | API 端点数量 | 复杂度 |
|------|------|-------------|--------|
| Contest | 竞赛管理、排名、参赛 | 17 | 高 |
| Forum | 社区论坛、帖子、评论 | 20 | 高 |
| WebSocket | 实时通知、竞赛更新 | N/A | 中 |

---

## Task 1: Contest 模块 - Entity 和 Mapper

**Files:**
- Create: `src/main/java/com/ulticode/modules/contest/entity/Contest.java`
- Create: `src/main/java/com/ulticode/modules/contest/entity/ContestParticipant.java`
- Create: `src/main/java/com/ulticode/modules/contest/entity/GlobalRanking.java`
- Create: `src/main/java/com/ulticode/modules/contest/mapper/ContestMapper.java`
- Create: `src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java`
- Create: `src/main/java/com/ulticode/modules/contest/mapper/GlobalRankingMapper.java`

### Step 1.1: 创建 Contest Entity

参照 NestJS Prisma schema 中的 Contest 模型:

```java
package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("contests")
public class Contest {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String description;
    private String slug;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private String status; // DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED
    private String visibility; // PUBLIC, PRIVATE, INVITE_ONLY
    private Integer duration; // 竞赛时长（秒）
    private Boolean isVirtualAllowed;
    private Boolean isRated;
    private String ratingCategory; // DIV1, DIV2, DIV3, UNRATED
    private String bannerImage;
    private String organizerId;
    private Integer maxParticipants;
    private String scoringType; // ICPC, IOI, CUSTOM
    private Boolean isPublished;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;
}
```

### Step 1.2: 创建 ContestParticipant Entity

```java
package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("contest_participants")
public class ContestParticipant {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;
    private String userId;
    private String status; // REGISTERED, PARTICIPATING, COMPLETED, DISQUALIFIED
    private LocalDateTime registeredAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer finalRank;
    private BigDecimal totalScore;
    private Integer problemsSolved;
    private BigDecimal penaltyTime;
    private String virtualSessionId;
    private LocalDateTime virtualStartTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Step 1.3: 创建 GlobalRanking Entity

```java
package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("global_ranking")
public class GlobalRanking {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String username;
    private String avatar;
    private String country;
    private BigDecimal rating;
    private BigDecimal maxRating;
    private String ratingTitle;
    private String maxRatingTitle;
    private Integer contestsAttended;
    private Integer globalRank;
    private String badge;
}
```

### Step 1.4: 创建 Mapper 接口

```java
// ContestMapper.java
package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.Contest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContestMapper extends BaseMapper<Contest> {
}

// ContestParticipantMapper.java
package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ContestParticipantMapper extends BaseMapper<ContestParticipant> {
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} ORDER BY total_score DESC, penalty_time ASC")
    List<ContestParticipant> findByContestIdOrderByScore(String contestId);

    @Select("SELECT * FROM contest_participants WHERE user_id = #{userId} AND status = #{status}")
    List<ContestParticipant> findByUserIdAndStatus(String userId, String status);
}

// GlobalRankingMapper.java
package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.GlobalRanking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface GlobalRankingMapper extends BaseMapper<GlobalRanking> {
    @Select("SELECT * FROM global_ranking ORDER BY global_rank ASC LIMIT #{limit}")
    List<GlobalRanking> findTopByOrderByGlobalRank(int limit);
}
```

- [ ] **Step 1.5: 运行编译验证**
- [ ] **Step 1.6: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/contest/
git commit -m "feat(contest): 添加 Contest 实体和 Mapper"
```

---

## Task 2: Contest 模块 - DTO

**Files:**
- Create: `src/main/java/com/ulticode/modules/contest/dto/ContestVO.java`
- Create: `src/main/java/com/ulticode/modules/contest/dto/ContestQueryDTO.java`
- Create: `src/main/java/com/ulticode/modules/contest/dto/CreateContestDTO.java`
- Create: `src/main/java/com/ulticode/modules/contest/dto/UpdateContestDTO.java`
- Create: `src/main/java/com/ulticode/modules/contest/dto/ContestRankingVO.java`
- Create: `src/main/java/com/ulticode/modules/contest/dto/ParticipationStatusDTO.java`

### Step 2.1: 创建 ContestVO

```java
package com.ulticode.modules.contest.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ContestVO {
    private String id;
    private String title;
    private String description;
    private String slug;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private String status; // UPCOMING, RUNNING, FINISHED
    private String timingStatus; // derived: upcoming, running, finished
    private Long timeRemaining; // seconds until end
    private Boolean isVirtualAllowed;
    private Boolean isRated;
    private String ratingCategory;
    private String bannerImage;
    private String organizerId;
    private Integer maxParticipants;
    private String scoringType;
    private Integer participantCount;
    private Boolean isRegistered; // for current user
    private String participationStatus; // for current user
}
```

### Step 2.2: 创建 ContestQueryDTO

```java
package com.ulticode.modules.contest.dto;

import lombok.Data;

@Data
public class ContestQueryDTO {
    private String status; // upcoming, running, finished, all
    private String visibility; // public, private, all
    private String search;
    private Integer page = 1;
    private Integer pageSize = 20;
}
```

### Step 2.3: 创建 CreateContestDTO

```java
package com.ulticode.modules.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateContestDTO {
    @NotBlank
    private String title;

    private String description;
    private String slug;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    private LocalDateTime registrationDeadline;
    private String visibility = "PUBLIC";
    private Boolean isVirtualAllowed = true;
    private Boolean isRated = false;
    private String ratingCategory = "UNRATED";
    private String bannerImage;
    private Integer maxParticipants;
    private String scoringType = "ICPC";
}
```

### Step 2.4: 创建 ParticipationStatusDTO

```java
package com.ulticode.modules.contest.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParticipationStatusDTO {
    private Boolean isRegistered;
    private String status; // REGISTERED, PARTICIPATING, COMPLETED, null
    private Boolean canStart;
    private Boolean canUnregister;
}
```

- [ ] **Step 2.5: 运行编译验证**
- [ ] **Step 2.6: Commit**

---

## Task 3: Contest 模块 - Service 和 Controller

**Files:**
- Create: `src/main/java/com/ulticode/modules/contest/service/ContestService.java`
- Create: `src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/contest/service/RankingService.java`
- Create: `src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/contest/controller/ContestController.java`

### Step 3.1: 创建 ContestService 接口

```java
package com.ulticode.modules.contest.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import java.util.List;

public interface ContestService {
    Page<ContestVO> findAll(ContestQueryDTO query, String userId);
    ContestVO findById(String id, String userId);
    List<ContestVO> findUpcoming();
    List<ContestVO> findRunning();
    Page<ContestVO> findPast(int page, int pageSize);
    ContestStatsVO getStats();
    void registerForContest(String contestId, String userId);
    void unregisterFromContest(String contestId, String userId);
    ParticipationStatusDTO getParticipationStatus(String contestId, String userId);
    List<ContestVO> getUserContests(String userId, String type);
    Contest createContest(CreateContestDTO dto, String userId);
    Contest updateContest(String id, UpdateContestDTO dto);
    void deleteContest(String id);
}
```

### Step 3.2: 创建 ContestServiceImpl (核心实现)

```java
package com.ulticode.modules.contest.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;

    @Override
    public Page<ContestVO> findAll(ContestQueryDTO query, String userId) {
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();

        // Status filtering based on timing
        if ("upcoming".equals(query.getStatus())) {
            wrapper.gt(Contest::getStartTime, LocalDateTime.now());
        } else if ("running".equals(query.getStatus())) {
            wrapper.le(Contest::getStartTime, LocalDateTime.now())
                   .gt(Contest::getEndTime, LocalDateTime.now());
        } else if ("finished".equals(query.getStatus())) {
            wrapper.le(Contest::getEndTime, LocalDateTime.now());
        }

        // Search
        if (query.getSearch() != null && !query.getSearch().isEmpty()) {
            wrapper.like(Contest::getTitle, query.getSearch());
        }

        wrapper.eq(Contest::getIsPublished, true)
               .orderByAsc(Contest::getStartTime);

        Page<Contest> contestPage = contestMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper
        );

        Page<ContestVO> voPage = new Page<>(query.getPage(), query.getPageSize(), contestPage.getTotal());
        List<ContestVO> voList = contestPage.getRecords().stream()
            .map(c -> toVO(c, userId))
            .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public ContestVO findById(String id, String userId) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return toVO(contest, userId);
    }

    @Override
    public List<ContestVO> findUpcoming() {
        List<Contest> contests = contestMapper.selectList(
            new LambdaQueryWrapper<Contest>()
                .gt(Contest::getStartTime, LocalDateTime.now())
                .eq(Contest::getIsPublished, true)
                .orderByAsc(Contest::getStartTime)
                .last("LIMIT 10")
        );
        return contests.stream().map(c -> toVO(c, null)).collect(Collectors.toList());
    }

    @Override
    public List<ContestVO> findRunning() {
        List<Contest> contests = contestMapper.selectList(
            new LambdaQueryWrapper<Contest>()
                .le(Contest::getStartTime, LocalDateTime.now())
                .gt(Contest::getEndTime, LocalDateTime.now())
                .eq(Contest::getIsPublished, true)
                .orderByAsc(Contest::getEndTime)
        );
        return contests.stream().map(c -> toVO(c, null)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void registerForContest(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        // Check if already registered
        Long count = participantMapper.selectCount(
            new LambdaQueryWrapper<ContestParticipant>()
                .eq(ContestParticipant::getContestId, contestId)
                .eq(ContestParticipant::getUserId, userId)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONTEST_ALREADY_REGISTERED);
        }

        // Check registration deadline
        if (contest.getRegistrationDeadline() != null &&
            LocalDateTime.now().isAfter(contest.getRegistrationDeadline())) {
            throw new BusinessException(ErrorCode.CONTEST_REGISTRATION_CLOSED);
        }

        // Check max participants
        if (contest.getMaxParticipants() != null) {
            Long currentCount = participantMapper.selectCount(
                new LambdaQueryWrapper<ContestParticipant>()
                    .eq(ContestParticipant::getContestId, contestId)
            );
            if (currentCount >= contest.getMaxParticipants()) {
                throw new BusinessException(ErrorCode.CONTEST_FULL);
            }
        }

        // Create participation
        ContestParticipant participant = new ContestParticipant();
        participant.setId(IdUtil.fastSimpleUUID());
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus("REGISTERED");
        participant.setRegisteredAt(LocalDateTime.now());

        participantMapper.insert(participant);
    }

    @Override
    @Transactional
    public void unregisterFromContest(String contestId, String userId) {
        int deleted = participantMapper.delete(
            new LambdaQueryWrapper<ContestParticipant>()
                .eq(ContestParticipant::getContestId, contestId)
                .eq(ContestParticipant::getUserId, userId)
                .eq(ContestParticipant::getStatus, "REGISTERED")
        );
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED);
        }
    }

    @Override
    public ParticipationStatusDTO getParticipationStatus(String contestId, String userId) {
        ContestParticipant participant = participantMapper.selectOne(
            new LambdaQueryWrapper<ContestParticipant>()
                .eq(ContestParticipant::getContestId, contestId)
                .eq(ContestParticipant::getUserId, userId)
        );

        if (participant == null) {
            return ParticipationStatusDTO.builder()
                .isRegistered(false)
                .canStart(false)
                .canUnregister(false)
                .build();
        }

        Contest contest = contestMapper.selectById(contestId);
        boolean canStart = contest != null &&
            LocalDateTime.now().isAfter(contest.getStartTime()) &&
            LocalDateTime.now().isBefore(contest.getEndTime()) &&
            "REGISTERED".equals(participant.getStatus());

        boolean canUnregister = "REGISTERED".equals(participant.getStatus()) &&
            contest != null &&
            LocalDateTime.now().isBefore(contest.getStartTime());

        return ParticipationStatusDTO.builder()
            .isRegistered(true)
            .status(participant.getStatus())
            .canStart(canStart)
            .canUnregister(canUnregister)
            .build();
    }

    private ContestVO toVO(Contest contest, String userId) {
        ContestVO vo = new ContestVO();
        vo.setId(contest.getId());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setSlug(contest.getSlug());
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setRegistrationDeadline(contest.getRegistrationDeadline());
        vo.setStatus(contest.getStatus());
        vo.setIsVirtualAllowed(contest.getIsVirtualAllowed());
        vo.setIsRated(contest.getIsRated());
        vo.setRatingCategory(contest.getRatingCategory());
        vo.setBannerImage(contest.getBannerImage());
        vo.setOrganizerId(contest.getOrganizerId());
        vo.setMaxParticipants(contest.getMaxParticipants());
        vo.setScoringType(contest.getScoringType());

        // Derive timing status
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            vo.setTimingStatus("upcoming");
        } else if (now.isAfter(contest.getEndTime())) {
            vo.setTimingStatus("finished");
        } else {
            vo.setTimingStatus("running");
            vo.setTimeRemaining(java.time.Duration.between(now, contest.getEndTime()).getSeconds());
        }

        // Participant count
        Long count = participantMapper.selectCount(
            new LambdaQueryWrapper<ContestParticipant>()
                .eq(ContestParticipant::getContestId, contest.getId())
        );
        vo.setParticipantCount(count.intValue());

        // User participation status
        if (userId != null) {
            ContestParticipant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<ContestParticipant>()
                    .eq(ContestParticipant::getContestId, contest.getId())
                    .eq(ContestParticipant::getUserId, userId)
            );
            vo.setIsRegistered(participant != null);
            vo.setParticipationStatus(participant != null ? participant.getStatus() : null);
        }

        return vo;
    }

    // ... additional methods for create, update, delete
}
```

### Step 3.3: 创建 ContestController

```java
package com.ulticode.modules.contest.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.service.ContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Contest", description = "竞赛管理接口")
@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    // ========== 竞赛查询 ==========

    @Operation(summary = "获取竞赛列表")
    @GetMapping("/list")
    public Result<PageResult<ContestVO>> findAll(ContestQueryDTO query) {
        String userId = SecurityUtil.getCurrentUserIdOrNull();
        Page<ContestVO> page = contestService.findAll(query, userId);
        PageResult<ContestVO> result = PageResult.of(
            page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取即将开始的竞赛")
    @GetMapping("/upcoming")
    public Result<List<ContestVO>> findUpcoming() {
        return Result.success(contestService.findUpcoming());
    }

    @Operation(summary = "获取进行中的竞赛")
    @GetMapping("/running")
    public Result<List<ContestVO>> findRunning() {
        return Result.success(contestService.findRunning());
    }

    @Operation(summary = "获取已结束的竞赛")
    @GetMapping("/past")
    public Result<PageResult<ContestVO>> findPast(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int limit
    ) {
        Page<ContestVO> voPage = contestService.findPast(page, limit);
        return Result.success(PageResult.of(
            voPage.getRecords(), voPage.getTotal(), (int) voPage.getCurrent(), (int) voPage.getSize()
        ));
    }

    @Operation(summary = "获取竞赛详情")
    @GetMapping("/{id}")
    public Result<ContestVO> findById(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(contestService.findById(id, userId));
    }

    // ========== 参赛管理 (需要认证) ==========

    @Operation(summary = "报名竞赛", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/register")
    public Result<Void> register(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        contestService.registerForContest(id, userId);
        return Result.success();
    }

    @Operation(summary = "取消报名", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}/register")
    public Result<Void> unregister(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        contestService.unregisterFromContest(id, userId);
        return Result.success();
    }

    @Operation(summary = "获取参赛状态", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}/participation")
    public Result<ParticipationStatusDTO> getParticipation(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(contestService.getParticipationStatus(id, userId));
    }

    // ========== 用户竞赛 ==========

    @Operation(summary = "获取我的竞赛", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/user/my-contests")
    public Result<List<ContestVO>> getMyContests(
        @RequestParam(defaultValue = "participated") String type
    ) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(contestService.getUserContests(userId, type));
    }

    // ========== 全局排名 ==========

    @Operation(summary = "获取全局排名")
    @GetMapping("/global-ranking")
    public Result<List<GlobalRankingVO>> getGlobalRanking() {
        return Result.success(contestService.getGlobalRanking());
    }
}
```

- [ ] **Step 3.4: 添加 ErrorCode 常量**
- [ ] **Step 3.5: 运行编译验证**
- [ ] **Step 3.6: Commit**

---

## Task 4: Forum 模块 - Entity 和 Mapper

**Files:**
- Create: `src/main/java/com/ulticode/modules/forum/entity/ForumPost.java`
- Create: `src/main/java/com/ulticode/modules/forum/entity/ForumComment.java`
- Create: `src/main/java/com/ulticode/modules/forum/entity/ForumCommunity.java`
- Create: `src/main/java/com/ulticode/modules/forum/mapper/ForumPostMapper.java`
- Create: `src/main/java/com/ulticode/modules/forum/mapper/ForumCommentMapper.java`
- Create: `src/main/java/com/ulticode/modules/forum/mapper/ForumCommunityMapper.java`

### Step 4.1: 创建 ForumPost Entity

```java
package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "forum_posts", autoResultMap = true)
public class ForumPost {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String body;
    private String excerpt;
    private String userId;
    private String communityId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String flairType;
    private String flairLabel;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> media;

    private Boolean isPinned;
    private Boolean isLocked;
    private Boolean isDeleted;
    private Integer views;
    private Integer shares;
    private Integer saves;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Step 4.2: 创建 ForumComment Entity

```java
package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("forum_comments")
public class ForumComment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String postId;
    private String userId;
    private String parentId;
    private String body;
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Step 4.3: 创建 ForumCommunity Entity

```java
package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("forum_communities")
public class ForumCommunity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String slug;
    private String description;
    private String icon;
    private String color;
    private String banner;
    private String visibility; // PUBLIC, PRIVATE
    private String ownerId;
    private Integer memberCount;
    private Boolean isFeatured;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4.4: 创建 Mapper 接口**
- [ ] **Step 4.5: 运行编译验证**
- [ ] **Step 4.6: Commit**

---

## Task 5: Forum 模块 - DTO、Service 和 Controller

**Files:**
- Create: `src/main/java/com/ulticode/modules/forum/dto/ForumPostVO.java`
- Create: `src/main/java/com/ulticode/modules/forum/dto/CreatePostDTO.java`
- Create: `src/main/java/com/ulticode/modules/forum/dto/UpdatePostDTO.java`
- Create: `src/main/java/com/ulticode/modules/forum/dto/ForumCommentVO.java`
- Create: `src/main/java/com/ulticode/modules/forum/dto/CreateCommentDTO.java`
- Create: `src/main/java/com/ulticode/modules/forum/dto/ForumCommunityVO.java`
- Create: `src/main/java/com/ulticode/modules/forum/service/ForumService.java`
- Create: `src/main/java/com/ulticode/modules/forum/service/impl/ForumServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/forum/controller/ForumController.java`

### Step 5.1: 创建 DTO 类

```java
// ForumPostVO.java
package com.ulticode.modules.forum.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ForumPostVO {
    private String id;
    private String title;
    private String body;
    private String excerpt;
    private String userId;
    private String username;
    private String userAvatar;
    private String communityId;
    private String communityName;
    private List<String> tags;
    private String flairType;
    private String flairLabel;
    private Boolean isPinned;
    private Boolean isLocked;
    private Integer views;
    private Integer shares;
    private Integer saves;
    private Integer commentsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Step 5.2: 创建 ForumController

```java
package com.ulticode.modules.forum.controller;

import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.service.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Forum", description = "论坛接口")
@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    // ========== 帖子操作 ==========

    @Operation(summary = "获取所有帖子")
    @GetMapping("/posts")
    public Result<List<ForumPostVO>> findAllPosts() {
        String userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(forumService.findAllPosts(userId));
    }

    @Operation(summary = "获取帖子详情")
    @GetMapping("/posts/{id}")
    public Result<ForumPostVO> findOnePost(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(forumService.findOnePost(id, userId));
    }

    @Operation(summary = "创建帖子", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/posts")
    public Result<ForumPostVO> createPost(@Valid @RequestBody CreatePostDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        String username = SecurityUtil.getCurrentUsername();
        String avatar = SecurityUtil.getCurrentUserAvatar();
        return Result.success(forumService.createPost(dto, userId, username, avatar));
    }

    @Operation(summary = "更新帖子", security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/posts/{id}")
    public Result<ForumPostVO> updatePost(
        @PathVariable String id,
        @Valid @RequestBody UpdatePostDTO dto
    ) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(forumService.updatePost(id, userId, dto));
    }

    @Operation(summary = "删除帖子", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        forumService.deletePost(id, userId);
        return Result.success();
    }

    // ========== 评论操作 ==========

    @Operation(summary = "创建评论", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/posts/{postId}/comments")
    public Result<ForumCommentVO> createComment(
        @PathVariable String postId,
        @Valid @RequestBody CreateCommentDTO dto
    ) {
        String userId = SecurityUtil.getCurrentUserId();
        String username = SecurityUtil.getCurrentUsername();
        String avatar = SecurityUtil.getCurrentUserAvatar();
        return Result.success(forumService.createComment(postId, dto, userId, username, avatar));
    }

    @Operation(summary = "更新评论", security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/comments/{id}")
    public Result<ForumCommentVO> updateComment(
        @PathVariable String id,
        @Valid @RequestBody UpdateCommentDTO dto
    ) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(forumService.updateComment(id, userId, dto));
    }

    @Operation(summary = "删除评论", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        forumService.deleteComment(id, userId);
        return Result.success();
    }

    // ========== 社区操作 ==========

    @Operation(summary = "获取所有社区")
    @GetMapping("/communities")
    public Result<List<ForumCommunityVO>> findAllCommunities(
        @RequestParam(required = false) Boolean featured
    ) {
        return Result.success(forumService.findAllCommunities(featured));
    }

    @Operation(summary = "获取社区详情")
    @GetMapping("/communities/{slugOrId}")
    public Result<ForumCommunityDetailVO> findOneCommunity(@PathVariable String slugOrId) {
        return Result.success(forumService.findOneCommunity(slugOrId));
    }

    @Operation(summary = "加入社区", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/communities/{id}/join")
    public Result<Void> joinCommunity(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        forumService.joinCommunity(userId, id);
        return Result.success();
    }

    @Operation(summary = "离开社区", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/communities/{id}/leave")
    public Result<Void> leaveCommunity(@PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        forumService.leaveCommunity(userId, id);
        return Result.success();
    }
}
```

- [ ] **Step 5.3: 实现 ForumServiceImpl**
- [ ] **Step 5.4: 运行编译验证**
- [ ] **Step 5.5: Commit**

---

## Task 6: WebSocket 实时通信

**Files:**
- Create: `src/main/java/com/ulticode/config/WebSocketConfig.java`
- Create: `src/main/java/com/ulticode/websocket/NotificationController.java`
- Create: `src/main/java/com/ulticode/websocket/dto/NotificationMessage.java`

### Step 6.1: 添加 WebSocket 依赖

在 `pom.xml` 中添加:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### Step 6.2: 创建 WebSocketConfig

```java
package com.ulticode.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker
        config.enableSimpleBroker("/topic", "/queue");
        // Set prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        // Set prefix for user-specific messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
            .withSockJS();
    }
}
```

### Step 6.3: 创建 NotificationController

```java
package com.ulticode.websocket;

import com.ulticode.websocket.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final SimpMessagingTemplate messagingTemplate;

    @SubscribeMapping("/connected")
    public Map<String, Object> onConnect(Principal principal) {
        log.info("User {} connected to notifications", principal.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Successfully connected to notification service");
        response.put("userId", principal.getName());
        return response;
    }

    @MessageMapping("/subscribe/community")
    public void subscribeCommunity(@Payload String communityId, Principal principal) {
        log.info("User {} subscribed to community {}", principal.getName(), communityId);
        // Subscription is handled by the client joining the topic directly
    }

    @MessageMapping("/subscribe/contest")
    public void subscribeContest(@Payload String contestId, Principal principal) {
        log.info("User {} subscribed to contest {}", principal.getName(), contestId);
    }

    // Send notification to specific user
    public void sendToUser(String userId, String event, Object data) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notifications",
            NotificationMessage.builder()
                .event(event)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build()
        );
    }

    // Broadcast to all subscribers
    public void broadcast(String destination, Object data) {
        messagingTemplate.convertAndSend(destination, data);
    }

    // Broadcast to contest room
    public void broadcastToContest(String contestId, String event, Object data) {
        messagingTemplate.convertAndSend(
            "/topic/contest/" + contestId,
            NotificationMessage.builder()
                .event(event)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build()
        );
    }

    // Broadcast to community room
    public void broadcastToCommunity(String communityId, String event, Object data) {
        messagingTemplate.convertAndSend(
            "/topic/community/" + communityId,
            NotificationMessage.builder()
                .event(event)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build()
        );
    }
}
```

- [ ] **Step 6.4: 创建 NotificationMessage DTO**
- [ ] **Step 6.5: 配置 WebSocket 认证**
- [ ] **Step 6.6: 运行编译验证**
- [ ] **Step 6.7: Commit**

---

## Task 7: 集成测试和验收

**Files:**
- Create: `src/test/java/com/ulticode/modules/contest/service/ContestServiceTest.java`
- Create: `src/test/java/com/ulticode/modules/forum/service/ForumServiceTest.java`

### Step 7.1: 编写 Contest 集成测试
### Step 7.2: 编写 Forum 集成测试
### Step 7.3: 验证 WebSocket 连接
### Step 7.4: 运行所有测试
### Step 7.5: 最终 Commit

---

## 验收检查清单

- [ ] Contest 模块: 竞赛 CRUD、报名、参赛状态正常
- [ ] Contest 排名: 全局排名、竞赛排名正常
- [ ] Forum 模块: 帖子 CRUD、评论正常
- [ ] Forum 社区: 社区管理、成员管理正常
- [ ] WebSocket: 客户端可连接、接收通知正常
- [ ] 所有 API 返回格式与 NestJS 兼容
- [ ] 所有错误码与 NestJS 一致

---

## 注意事项

1. **数据库兼容性**: 使用现有的 MySQL 数据库，Entity 字段名使用下划线命名（与 Prisma schema 一致）
2. **错误码**: 必须使用与 NestJS 相同的错误码（在 `ErrorCode` 中添加新的错误码）
3. **响应格式**: 所有 API 返回 `Result<T>` 或 `PageResult<T>`
4. **认证**: 使用现有的 JWT 认证机制，通过 `SecurityUtil` 获取当前用户
5. **WebSocket**: 使用 STOMP 协议（与前端兼容），支持 SockJS 降级
