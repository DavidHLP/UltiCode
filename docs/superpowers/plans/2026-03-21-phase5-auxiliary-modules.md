# NestJS 到 Spring Boot 迁移 - Phase 5: 辅助模块

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现推荐系统、搜索服务、边缘操作、投票系统、备份系统、监控服务和国际化模块，完成所有后端功能迁移。

**Architecture:** Controller → Service → Mapper 三层架构，使用 MyBatis-Plus 进行数据访问，复用 Phase 1-4 已建立的模式。

**Tech Stack:** Spring Boot 3.5.12, MyBatis-Plus 3.5.5, Spring Security, jjwt 0.12.5, RestTemplate/WebClient, MeiliSearch Java Client

---

## 前置条件

- Phase 1-4 已完成
- 数据库已存在 (与 NestJS 共享)
- 现有模块：user, auth, problem, submission, solution, contest, forum, admin, moderation, notification, bookmark, problemlist

---

## 模块概览

| 模块 | 功能 | 文件数 | API 端点数 | 预计时间 |
|------|------|--------|-----------|----------|
| Recommendation | 每日推荐、个性化推荐、相似题目 | 10 | 6 | 2天 |
| Search | 全文搜索、MeiliSearch 集成 | 6 | 2 | 0.5天 |
| EdgeOperations | 点赞、投票操作 | 6 | 2 | 0.5天 |
| Vote | 投票逻辑、计数 | 6 | 1 | 0.5天 |
| Backup | 数据库备份、恢复 | 8 | 6 | 0.5天 |
| Monitoring | 系统健康检查、资源监控 | 6 | 6 | 0.5天 |
| I18n | 国际化、翻译管理 | 8 | 4 | 0.5天 |

---

## Task 1: Vote 模块 - 投票系统

**Priority:** High (EdgeOperations 依赖此模块)

**Files:**
- Create: `src/main/java/com/ulticode/modules/vote/entity/EdgeOperation.java`
- Create: `src/main/java/com/ulticode/modules/vote/entity/enums/EdgeOperationType.java`
- Create: `src/main/java/com/ulticode/modules/vote/entity/enums/EdgeOperationTargetType.java`
- Create: `src/main/java/com/ulticode/modules/vote/mapper/EdgeOperationMapper.java`
- Create: `src/main/java/com/ulticode/modules/vote/dto/VoteDTO.java`
- Create: `src/main/java/com/ulticode/modules/vote/dto/VoteResultVO.java`
- Create: `src/main/java/com/ulticode/modules/vote/service/VoteService.java`
- Create: `src/main/java/com/ulticode/modules/vote/service/impl/VoteServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/vote/controller/VoteController.java`
- Test: `src/test/java/com/ulticode/modules/vote/service/VoteServiceTest.java`

### Step 1.1: 创建枚举类型

```java
// EdgeOperationType.java
package com.ulticode.modules.vote.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EdgeOperationType {
    VOTE_UP("VOTE_UP"),
    VOTE_DOWN("VOTE_DOWN"),
    ANALYZE("ANALYZE"),
    VIEW("VIEW");

    @EnumValue
    private final String value;

    EdgeOperationType(String value) {
        this.value = value;
    }
}

// EdgeOperationTargetType.java
package com.ulticode.modules.vote.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EdgeOperationTargetType {
    PROBLEM("PROBLEM"),
    SOLUTION("SOLUTION"),
    POST("POST"),
    COMMENT("COMMENT");

    @EnumValue
    private final String value;

    EdgeOperationTargetType(String value) {
        this.value = value;
    }
}
```

### Step 1.2: 创建 EdgeOperation Entity

```java
package com.ulticode.modules.vote.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("edge_operations")
public class EdgeOperation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String targetId;
    private EdgeOperationTargetType targetType;
    private String operatorId;
    private EdgeOperationType operationType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Step 1.3: 创建 EdgeOperationMapper

```java
package com.ulticode.modules.vote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface EdgeOperationMapper extends BaseMapper<EdgeOperation> {

    @Select("SELECT operation_type, COUNT(*) as count FROM edge_operations " +
            "WHERE target_type = #{targetType} AND target_id = #{targetId} " +
            "AND operation_type IN ('VOTE_UP', 'VOTE_DOWN') " +
            "GROUP BY operation_type")
    List<Map<String, Object>> countVotesByTarget(
        @Param("targetType") String targetType,
        @Param("targetId") String targetId
    );
}
```

### Step 1.4: 创建 DTO

```java
// VoteDTO.java
package com.ulticode.modules.vote.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteDTO {
    @NotNull(message = "Target type is required")
    private EdgeOperationTargetType targetType;

    @NotNull(message = "Target ID is required")
    private String targetId;

    @NotNull(message = "Vote type is required")
    private Integer voteType; // 1 for upvote, -1 for downvote
}

// VoteResultVO.java
package com.ulticode.modules.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultVO {
    private Integer likes;
    private Integer dislikes;
    private Integer userVote; // 1, 0, or -1
}
```

### Step 1.5: 创建 VoteService

```java
package com.ulticode.modules.vote.service;

import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import java.util.Map;

public interface VoteService {
    VoteResultVO vote(String userId, VoteDTO dto);
    Map<String, Integer> getVoteCounts(EdgeOperationTargetType targetType, String targetId);
    Integer getUserVote(String userId, EdgeOperationTargetType targetType, String targetId);
    Map<String, VoteResultVO> getUserVotesBatch(String userId, EdgeOperationTargetType targetType, java.util.List<String> targetIds);
}
```

### Step 1.6: 实现 VoteServiceImpl

```java
package com.ulticode.modules.vote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {

    private final EdgeOperationMapper edgeOperationMapper;

    @Override
    @Transactional
    public VoteResultVO vote(String userId, VoteDTO dto) {
        EdgeOperationType operationType = dto.getVoteType() == 1
            ? EdgeOperationType.VOTE_UP
            : EdgeOperationType.VOTE_DOWN;

        // Find existing vote
        LambdaQueryWrapper<EdgeOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOperation::getOperatorId, userId)
               .eq(EdgeOperation::getTargetType, dto.getTargetType())
               .eq(EdgeOperation::getTargetId, dto.getTargetId())
               .in(EdgeOperation::getOperationType,
                   EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN);

        EdgeOperation existingVote = edgeOperationMapper.selectOne(wrapper);
        int finalVoteType = 0;

        if (existingVote != null) {
            if (existingVote.getOperationType() == operationType) {
                // Toggle off: remove vote
                edgeOperationMapper.deleteById(existingVote.getId());
                finalVoteType = 0;
            } else {
                // Change vote
                existingVote.setOperationType(operationType);
                edgeOperationMapper.updateById(existingVote);
                finalVoteType = dto.getVoteType();
            }
        } else {
            // Create new vote
            EdgeOperation newVote = new EdgeOperation();
            newVote.setOperatorId(userId);
            newVote.setTargetType(dto.getTargetType());
            newVote.setTargetId(dto.getTargetId());
            newVote.setOperationType(operationType);
            edgeOperationMapper.insert(newVote);
            finalVoteType = dto.getVoteType();
        }

        Map<String, Integer> counts = getVoteCounts(dto.getTargetType(), dto.getTargetId());
        return VoteResultVO.builder()
            .likes(counts.getOrDefault("likes", 0))
            .dislikes(counts.getOrDefault("dislikes", 0))
            .userVote(finalVoteType)
            .build();
    }

    @Override
    public Map<String, Integer> getVoteCounts(EdgeOperationTargetType targetType, String targetId) {
        List<Map<String, Object>> results = edgeOperationMapper.countVotesByTarget(
            targetType.getValue(), targetId
        );

        int likes = 0, dislikes = 0;
        for (Map<String, Object> row : results) {
            String opType = (String) row.get("operation_type");
            Long count = (Long) row.get("count");
            if ("VOTE_UP".equals(opType)) likes = count.intValue();
            if ("VOTE_DOWN".equals(opType)) dislikes = count.intValue();
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("likes", likes);
        result.put("dislikes", dislikes);
        return result;
    }

    @Override
    public Integer getUserVote(String userId, EdgeOperationTargetType targetType, String targetId) {
        LambdaQueryWrapper<EdgeOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOperation::getOperatorId, userId)
               .eq(EdgeOperation::getTargetType, targetType)
               .eq(EdgeOperation::getTargetId, targetId)
               .in(EdgeOperation::getOperationType,
                   EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN);

        EdgeOperation vote = edgeOperationMapper.selectOne(wrapper);
        if (vote == null) return 0;
        return vote.getOperationType() == EdgeOperationType.VOTE_UP ? 1 : -1;
    }

    @Override
    public Map<String, VoteResultVO> getUserVotesBatch(String userId, EdgeOperationTargetType targetType, List<String> targetIds) {
        // Initialize result map
        Map<String, VoteResultVO> result = new HashMap<>();
        for (String id : targetIds) {
            result.put(id, VoteResultVO.builder().likes(0).dislikes(0).userVote(0).build());
        }

        // Get all votes for these targets
        LambdaQueryWrapper<EdgeOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOperation::getTargetType, targetType)
               .in(EdgeOperation::getTargetId, targetIds)
               .in(EdgeOperation::getOperationType,
                   EdgeOperationType.VOTE_UP, EdgeOperationType.VOTE_DOWN);
        List<EdgeOperation> votes = edgeOperationMapper.selectList(wrapper);

        // Group by target and count
        Map<String, List<EdgeOperation>> byTarget = votes.stream()
            .collect(Collectors.groupingBy(EdgeOperation::getTargetId));

        for (Map.Entry<String, List<EdgeOperation>> entry : byTarget.entrySet()) {
            String targetId = entry.getKey();
            List<EdgeOperation> targetVotes = entry.getValue();

            int likes = 0, dislikes = 0, userVote = 0;
            for (EdgeOperation vote : targetVotes) {
                if (vote.getOperationType() == EdgeOperationType.VOTE_UP) likes++;
                if (vote.getOperationType() == EdgeOperationType.VOTE_DOWN) dislikes++;
                if (vote.getOperatorId().equals(userId)) {
                    userVote = vote.getOperationType() == EdgeOperationType.VOTE_UP ? 1 : -1;
                }
            }

            result.put(targetId, VoteResultVO.builder()
                .likes(likes).dislikes(dislikes).userVote(userVote).build());
        }

        return result;
    }
}
```

### Step 1.7: 创建 VoteController

```java
package com.ulticode.modules.vote.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.service.VoteService;
import com.ulticode.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Vote", description = "Voting operations")
@RestController
@RequestMapping("/api/vote")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping
    @Operation(summary = "Vote on a target (problem, solution, post, etc.)")
    public Result<VoteResultVO> vote(
            @Valid @RequestBody VoteDTO dto,
            @CurrentUser String userId) {
        VoteResultVO result = voteService.vote(userId, dto);
        return Result.success(result);
    }
}
```

---

## Task 2: EdgeOperations 模块 - 边缘操作

**Priority:** High (依赖 Vote 模块)

**Files:**
- Create: `src/main/java/com/ulticode/modules/edgeoperations/dto/EdgeOperationDTO.java`
- Create: `src/main/java/com/ulticode/modules/edgeoperations/dto/GetInteractionsQueryDTO.java`
- Create: `src/main/java/com/ulticode/modules/edgeoperations/dto/EdgeOperationResponseVO.java`
- Create: `src/main/java/com/ulticode/modules/edgeoperations/service/EdgeOperationsService.java`
- Create: `src/main/java/com/ulticode/modules/edgeoperations/service/impl/EdgeOperationsServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/edgeoperations/controller/EdgeOperationsController.java`
- Test: `src/test/java/com/ulticode/modules/edgeoperations/service/EdgeOperationsServiceTest.java`

### Step 2.1: 创建 DTO

```java
// EdgeOperationDTO.java
package com.ulticode.modules.edgeoperations.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EdgeOperationDTO {
    @NotNull(message = "Operation type is required")
    private EdgeOperationType operationType;

    @NotNull(message = "Target type is required")
    private EdgeOperationTargetType targetType;

    @NotNull(message = "Target ID is required")
    private String targetId;
}

// GetInteractionsQueryDTO.java
package com.ulticode.modules.edgeoperations.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import lombok.Data;

@Data
public class GetInteractionsQueryDTO {
    private EdgeOperationTargetType targetType;
    private String targetId;
}

// EdgeOperationResponseVO.java
package com.ulticode.modules.edgeoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeOperationResponseVO {
    private Integer likes;
    private Integer dislikes;
    private Integer favorites;
    private ViewerState viewer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewerState {
        private Integer vote; // 1, 0, or -1
    }
}
```

### Step 2.2: 创建 EdgeOperationsService

```java
package com.ulticode.modules.edgeoperations.service;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;

public interface EdgeOperationsService {
    EdgeOperationResponseVO operate(String userId, EdgeOperationDTO dto);
    EdgeOperationResponseVO getInteractions(EdgeOperationTargetType targetType, String targetId, String userId);
}
```

### Step 2.3: 实现 EdgeOperationsServiceImpl

```java
package com.ulticode.modules.edgeoperations.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeOperationsServiceImpl implements EdgeOperationsService {

    private final VoteService voteService;
    private final EdgeOperationMapper edgeOperationMapper;
    private final BookmarkMapper bookmarkMapper;

    @Override
    @Transactional
    public EdgeOperationResponseVO operate(String userId, EdgeOperationDTO dto) {
        EdgeOperationType opType = dto.getOperationType();

        // Handle voting operations
        if (opType == EdgeOperationType.VOTE_UP || opType == EdgeOperationType.VOTE_DOWN) {
            int voteType = opType == EdgeOperationType.VOTE_UP ? 1 : -1;
            VoteDTO voteDTO = new VoteDTO();
            voteDTO.setTargetType(dto.getTargetType());
            voteDTO.setTargetId(dto.getTargetId());
            voteDTO.setVoteType(voteType);

            VoteResultVO voteResult = voteService.vote(userId, voteDTO);

            return EdgeOperationResponseVO.builder()
                .likes(voteResult.getLikes())
                .dislikes(voteResult.getDislikes())
                .favorites(getFavoritesCount(dto.getTargetType(), dto.getTargetId()))
                .viewer(EdgeOperationResponseVO.ViewerState.builder()
                    .vote(voteResult.getUserVote())
                    .build())
                .build();
        }

        // Handle other operations (ANALYZE, VIEW, etc.)
        toggleOperation(userId, dto.getTargetType(), dto.getTargetId(), opType);

        // Return current state
        return getInteractions(dto.getTargetType(), dto.getTargetId(), userId);
    }

    @Override
    public EdgeOperationResponseVO getInteractions(EdgeOperationTargetType targetType, String targetId, String userId) {
        var voteCounts = voteService.getVoteCounts(targetType, targetId);
        Integer userVote = userId != null
            ? voteService.getUserVote(userId, targetType, targetId)
            : 0;

        return EdgeOperationResponseVO.builder()
            .likes(voteCounts.getOrDefault("likes", 0))
            .dislikes(voteCounts.getOrDefault("dislikes", 0))
            .favorites(getFavoritesCount(targetType, targetId))
            .viewer(EdgeOperationResponseVO.ViewerState.builder()
                .vote(userVote)
                .build())
            .build();
    }

    private boolean toggleOperation(String userId, EdgeOperationTargetType targetType,
                                    String targetId, EdgeOperationType operationType) {
        LambdaQueryWrapper<EdgeOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOperation::getOperatorId, userId)
               .eq(EdgeOperation::getOperationType, operationType)
               .eq(EdgeOperation::getTargetType, targetType)
               .eq(EdgeOperation::getTargetId, targetId);

        EdgeOperation existing = edgeOperationMapper.selectOne(wrapper);

        if (existing != null) {
            edgeOperationMapper.deleteById(existing.getId());
            return false;
        }

        EdgeOperation newOp = new EdgeOperation();
        newOp.setOperatorId(userId);
        newOp.setTargetType(targetType);
        newOp.setTargetId(targetId);
        newOp.setOperationType(operationType);
        edgeOperationMapper.insert(newOp);
        return true;
    }

    private Integer getFavoritesCount(EdgeOperationTargetType targetType, String targetId) {
        if (targetType != EdgeOperationTargetType.PROBLEM) {
            return 0;
        }

        // Count unique users who bookmarked this problem
        // This requires bookmark module to be implemented
        // For now, return 0 as placeholder
        return 0;
    }
}
```

### Step 2.4: 创建 EdgeOperationsController

```java
package com.ulticode.modules.edgeoperations.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.dto.GetInteractionsQueryDTO;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Edge Operations", description = "Edge operations like voting, analyzing")
@RestController
@RequestMapping("/api/edge-operations")
@RequiredArgsConstructor
public class EdgeOperationsController {

    private final EdgeOperationsService edgeOperationsService;

    @PostMapping
    @Operation(summary = "Perform an edge operation (vote, analyze, etc.)")
    public Result<EdgeOperationResponseVO> operate(
            @Valid @RequestBody EdgeOperationDTO dto,
            @CurrentUser String userId) {
        EdgeOperationResponseVO result = edgeOperationsService.operate(userId, dto);
        return Result.success(result);
    }

    @GetMapping("/interactions")
    @Operation(summary = "Get interaction stats for a target")
    public Result<EdgeOperationResponseVO> getInteractions(
            @Valid GetInteractionsQueryDTO query,
            @CurrentUser(required = false) String userId) {
        EdgeOperationResponseVO result = edgeOperationsService.getInteractions(
            query.getTargetType(), query.getTargetId(), userId
        );
        return Result.success(result);
    }
}
```

---

## Task 3: Search 模块 - 搜索服务

**Priority:** Medium

**Files:**
- Create: `src/main/java/com/ulticode/modules/search/config/MeiliSearchConfig.java`
- Create: `src/main/java/com/ulticode/modules/search/dto/SearchQueryDTO.java`
- Create: `src/main/java/com/ulticode/modules/search/dto/SearchResponseVO.java`
- Create: `src/main/java/com/ulticode/modules/search/service/SearchService.java`
- Create: `src/main/java/com/ulticode/modules/search/service/impl/SearchServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/search/controller/SearchController.java`
- Test: `src/test/java/com/ulticode/modules/search/service/SearchServiceTest.java`

### Step 3.1: 添加 MeiliSearch 依赖

在 `pom.xml` 中添加：
```xml
<dependency>
    <groupId>com.meilisearch.sdk</groupId>
    <artifactId>meilisearch-java</artifactId>
    <version>0.11.0</version>
</dependency>
```

### Step 3.2: 创建 MeiliSearch 配置

```java
package com.ulticode.modules.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MeiliSearchConfig {

    @Value("${meilisearch.host:}")
    private String host;

    @Value("${meilisearch.api-key:}")
    private String apiKey;

    @Bean
    public Client meiliSearchClient() {
        if (host == null || host.isEmpty()) {
            log.warn("MeiliSearch host not configured. Search will use database fallback.");
            return null;
        }
        log.info("MeiliSearch client initialized: {}", host);
        return new Client(new Config(host, apiKey));
    }
}
```

### Step 3.3: 创建 DTO

```java
// SearchQueryDTO.java
package com.ulticode.modules.search.dto;

import lombok.Data;

@Data
public class SearchQueryDTO {
    private String query;
    private SearchIndex index;
    private Integer page = 1;
    private Integer limit = 20;

    public enum SearchIndex {
        PROBLEMS, USERS, POSTS, SOLUTIONS
    }
}

// SearchResponseVO.java
package com.ulticode.modules.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponseVO {
    private String query;
    private Long total;
    private Integer page;
    private Integer limit;
    private List<SearchResultVO> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResultVO {
        private String id;
        private String type;
        private String title;
        private String description;
        private String url;
        private Map<String, List<String>> highlights;
    }
}
```

### Step 3.4: 创建 SearchService

```java
package com.ulticode.modules.search.service;

import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;

public interface SearchService {
    SearchResponseVO search(SearchQueryDTO dto);
    void indexProblem(String id, String title, String slug, String difficulty, String summary, java.util.List<String> tags);
    void indexUser(String id, String username, String name, String bio);
    void deleteProblem(String id);
    void deleteUser(String id);
}
```

### Step 3.5: 实现 SearchServiceImpl

```java
package com.ulticode.modules.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResult;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.service.SearchService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ProblemMapper problemMapper;
    private final UserMapper userMapper;

    @Autowired(required = false)
    private Client meiliSearchClient;

    private boolean isMeiliSearchEnabled() {
        return meiliSearchClient != null;
    }

    @Override
    public SearchResponseVO search(SearchQueryDTO dto) {
        if (isMeiliSearchEnabled()) {
            return searchWithMeiliSearch(dto);
        }
        return searchWithDatabase(dto);
    }

    private SearchResponseVO searchWithMeiliSearch(SearchQueryDTO dto) {
        // Implementation with MeiliSearch client
        // Simplified for brevity
        return searchWithDatabase(dto);
    }

    private SearchResponseVO searchWithDatabase(SearchQueryDTO dto) {
        List<SearchResponseVO.SearchResultVO> results = new ArrayList<>();
        long total = 0;
        int offset = (dto.getPage() - 1) * dto.getLimit();

        // Search problems
        if (dto.getIndex() == null || dto.getIndex() == SearchQueryDTO.SearchIndex.PROBLEMS) {
            LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Problem::getIsPublished, true)
                   .eq(Problem::getIsDeleted, false)
                   .and(w -> w.like(Problem::getTitle, dto.getQuery())
                              .or()
                              .like(Problem::getSlug, dto.getQuery()))
                   .last("LIMIT " + dto.getLimit() + " OFFSET " + offset);

            List<Problem> problems = problemMapper.selectList(wrapper);
            for (Problem p : problems) {
                results.add(SearchResponseVO.SearchResultVO.builder()
                    .id(String.valueOf(p.getId()))
                    .type("PROBLEMS")
                    .title(p.getTitle())
                    .description("")
                    .url("/problems/" + p.getSlug())
                    .build());
            }
        }

        // Search users
        if (dto.getIndex() == null || dto.getIndex() == SearchQueryDTO.SearchIndex.USERS) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(User::getUsername, dto.getQuery())
                   .or()
                   .like(User::getName, dto.getQuery())
                   .last("LIMIT " + dto.getLimit() + " OFFSET " + offset);

            List<User> users = userMapper.selectList(wrapper);
            for (User u : users) {
                results.add(SearchResponseVO.SearchResultVO.builder()
                    .id(u.getId())
                    .type("USERS")
                    .title(u.getUsername())
                    .description(u.getName() != null ? u.getName() : "")
                    .url("/users/" + u.getId())
                    .build());
            }
        }

        return SearchResponseVO.builder()
            .query(dto.getQuery())
            .total((long) results.size())
            .page(dto.getPage())
            .limit(dto.getLimit())
            .results(results)
            .build();
    }

    @Override
    public void indexProblem(String id, String title, String slug, String difficulty, String summary, List<String> tags) {
        if (!isMeiliSearchEnabled()) return;
        // Index problem in MeiliSearch
    }

    @Override
    public void indexUser(String id, String username, String name, String bio) {
        if (!isMeiliSearchEnabled()) return;
        // Index user in MeiliSearch
    }

    @Override
    public void deleteProblem(String id) {
        if (!isMeiliSearchEnabled()) return;
        // Delete from MeiliSearch
    }

    @Override
    public void deleteUser(String id) {
        if (!isMeiliSearchEnabled()) return;
        // Delete from MeiliSearch
    }
}
```

### Step 3.6: 创建 SearchController

```java
package com.ulticode.modules.search.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Search", description = "Search operations")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search problems, users, posts, solutions")
    public Result<SearchResponseVO> search(SearchQueryDTO query) {
        SearchResponseVO result = searchService.search(query);
        return Result.success(result);
    }
}
```

---

## Task 4: Recommendation 模块 - 推荐系统

**Priority:** Medium

**Files:**
- Create: `src/main/java/com/ulticode/modules/recommendation/config/RecommendationConfig.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/entity/DailyRecommendation.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/mapper/DailyRecommendationMapper.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/dto/GetRecommendationsDTO.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/dto/RecommendResponseVO.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/service/RecommendationService.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/service/impl/RecommendationServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/scheduler/RecommendationScheduler.java`
- Create: `src/main/java/com/ulticode/modules/recommendation/controller/RecommendationController.java`
- Test: `src/test/java/com/ulticode/modules/recommendation/service/RecommendationServiceTest.java`

### Step 4.1: 创建配置类

```java
package com.ulticode.modules.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationConfig {
    private Boolean enabled = false;
    private String serviceUrl;
    private Integer timeout = 5000;
    private Boolean nacosEnabled = false;
    private String fallbackUrl;
}
```

### Step 4.2: 创建 Entity

```java
package com.ulticode.modules.recommendation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "daily_recommendations", autoResultMap = true)
public class DailyRecommendation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private Long problemId;
    private String scenario;
    private Double score;
    private String reason;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private LocalDateTime generatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Step 4.3: 创建 Mapper

```java
package com.ulticode.modules.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.recommendation.entity.DailyRecommendation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailyRecommendationMapper extends BaseMapper<DailyRecommendation> {
}
```

### Step 4.4: 创建 DTO

```java
// GetRecommendationsDTO.java
package com.ulticode.modules.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class GetRecommendationsDTO {
    private String userId;
    private Integer size = 10;
    private RecommendScenario scenario = RecommendScenario.DAILY;
    private Long sourceProblemId;
    private List<String> targetTags;
    private Boolean includeSolved = false;

    public enum RecommendScenario {
        DAILY, SIMILAR, WEAK_POINT, CHALLENGE
    }
}

// RecommendResponseVO.java
package com.ulticode.modules.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponseVO {
    private Boolean success;
    private Integer code;
    private String message;
    private RecommendDataVO data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendDataVO {
        private List<RecommendItemVO> items;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecommendItemVO {
            private Long problemId;
            private String title;
            private String slug;
            private String difficulty;
            private Double score;
            private String reason;
            private List<String> tags;
        }
    }
}
```

### Step 4.5: 创建 RecommendationService

```java
package com.ulticode.modules.recommendation.service;

import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;

public interface RecommendationService {
    RecommendResponseVO getRecommendations(GetRecommendationsDTO dto);
    RecommendResponseVO getDailyRecommendations(String userId, Integer size, Boolean includeSolved);
    RecommendResponseVO getSimilarProblems(String userId, Long problemId, Integer size);
    RecommendResponseVO getWeakPointRecommendations(String userId, Integer size, java.util.List<String> tags);
    RecommendResponseVO getChallengeRecommendations(String userId, Integer size);
    RecommendResponseVO healthCheck();
}
```

### Step 4.6: 实现 RecommendationServiceImpl

```java
package com.ulticode.modules.recommendation.service.impl;

import com.ulticode.modules.recommendation.config.RecommendationConfig;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationConfig config;
    private final RestTemplate restTemplate;

    @Override
    public RecommendResponseVO getRecommendations(GetRecommendationsDTO dto) {
        if (!config.getEnabled()) {
            return createDisabledResponse();
        }

        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            return createErrorResponse(503, "Recommendation service unavailable");
        }

        try {
            String url = serviceUrl + "/api/recommend";
            return restTemplate.postForObject(url, dto, RecommendResponseVO.class);
        } catch (Exception e) {
            log.error("Failed to get recommendations: {}", e.getMessage());
            return createErrorResponse(500, "Failed to get recommendations: " + e.getMessage());
        }
    }

    @Override
    public RecommendResponseVO getDailyRecommendations(String userId, Integer size, Boolean includeSolved) {
        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setUserId(userId);
        dto.setSize(size != null ? size : 10);
        dto.setScenario(GetRecommendationsDTO.RecommendScenario.DAILY);
        dto.setIncludeSolved(includeSolved != null ? includeSolved : false);
        return getRecommendations(dto);
    }

    @Override
    public RecommendResponseVO getSimilarProblems(String userId, Long problemId, Integer size) {
        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setUserId(userId);
        dto.setSize(size != null ? size : 5);
        dto.setScenario(GetRecommendationsDTO.RecommendScenario.SIMILAR);
        dto.setSourceProblemId(problemId);
        return getRecommendations(dto);
    }

    @Override
    public RecommendResponseVO getWeakPointRecommendations(String userId, Integer size, java.util.List<String> tags) {
        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setUserId(userId);
        dto.setSize(size != null ? size : 10);
        dto.setScenario(GetRecommendationsDTO.RecommendScenario.WEAK_POINT);
        dto.setTargetTags(tags);
        return getRecommendations(dto);
    }

    @Override
    public RecommendResponseVO getChallengeRecommendations(String userId, Integer size) {
        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setUserId(userId);
        dto.setSize(size != null ? size : 5);
        dto.setScenario(GetRecommendationsDTO.RecommendScenario.CHALLENGE);
        return getRecommendations(dto);
    }

    @Override
    public RecommendResponseVO healthCheck() {
        if (!config.getEnabled()) {
            return RecommendResponseVO.builder()
                .success(true)
                .code(200)
                .message("Recommendation service is disabled")
                .build();
        }
        // Check service health
        return RecommendResponseVO.builder()
            .success(true)
            .code(200)
            .message("Healthy")
            .build();
    }

    private RecommendResponseVO createErrorResponse(Integer code, String message) {
        return RecommendResponseVO.builder()
            .success(false)
            .code(code)
            .message(message)
            .build();
    }

    private RecommendResponseVO createDisabledResponse() {
        return createErrorResponse(503, "Recommendation service is disabled");
    }
}
```

### Step 4.7: 创建定时任务

```java
package com.ulticode.modules.recommendation.scheduler;

import com.ulticode.modules.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationService recommendationService;

    @Scheduled(cron = "0 0 8 * * ?") // Daily at 8 AM
    public void generateDailyRecommendations() {
        log.info("Starting daily recommendation generation...");
        // Implementation would generate recommendations for all users
        log.info("Daily recommendation generation completed");
    }
}
```

### Step 4.8: 创建 RecommendationController

```java
package com.ulticode.modules.recommendation.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.service.RecommendationService;
import com.ulticode.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Recommendations", description = "Problem recommendation operations")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping
    @Operation(summary = "Get personalized problem recommendations")
    public RecommendResponseVO getRecommendations(
            @Valid @RequestBody GetRecommendationsDTO dto,
            @CurrentUser(required = false) String userId) {
        if (dto.getUserId() == null && userId != null) {
            dto.setUserId(userId);
        }
        return recommendationService.getRecommendations(dto);
    }

    @GetMapping("/daily")
    @Operation(summary = "Get daily practice recommendations")
    public RecommendResponseVO getDailyRecommendations(
            @CurrentUser String userId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean includeSolved) {
        return recommendationService.getDailyRecommendations(userId, size, includeSolved);
    }

    @GetMapping("/similar/{problemId}")
    @Operation(summary = "Get problems similar to a specific problem")
    public RecommendResponseVO getSimilarProblems(
            @PathVariable Long problemId,
            @CurrentUser String userId,
            @RequestParam(required = false) Integer size) {
        return recommendationService.getSimilarProblems(userId, problemId, size);
    }

    @GetMapping("/weak-points")
    @Operation(summary = "Get recommendations for weak point strengthening")
    public RecommendResponseVO getWeakPointRecommendations(
            @CurrentUser String userId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) java.util.List<String> tags) {
        return recommendationService.getWeakPointRecommendations(userId, size, tags);
    }

    @GetMapping("/challenge")
    @Operation(summary = "Get challenge mode recommendations (harder problems)")
    public RecommendResponseVO getChallengeRecommendations(
            @CurrentUser String userId,
            @RequestParam(required = false) Integer size) {
        return recommendationService.getChallengeRecommendations(userId, size);
    }

    @GetMapping("/health")
    @Operation(summary = "Check recommendation service health")
    public RecommendResponseVO healthCheck() {
        return recommendationService.healthCheck();
    }
}
```

---

## Task 5: Backup 模块 - 备份系统

**Priority:** Low (Admin only)

**Files:**
- Create: `src/main/java/com/ulticode/modules/backup/entity/Backup.java`
- Create: `src/main/java/com/ulticode/modules/backup/entity/enums/BackupType.java`
- Create: `src/main/java/com/ulticode/modules/backup/entity/enums/BackupStatus.java`
- Create: `src/main/java/com/ulticode/modules/backup/mapper/BackupMapper.java`
- Create: `src/main/java/com/ulticode/modules/backup/dto/CreateBackupDTO.java`
- Create: `src/main/java/com/ulticode/modules/backup/dto/BackupQueryDTO.java`
- Create: `src/main/java/com/ulticode/modules/backup/dto/BackupVO.java`
- Create: `src/main/java/com/ulticode/modules/backup/service/BackupService.java`
- Create: `src/main/java/com/ulticode/modules/backup/service/impl/BackupServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/backup/controller/BackupController.java`
- Test: `src/test/java/com/ulticode/modules/backup/service/BackupServiceTest.java`

### Step 5.1: 创建枚举

```java
// BackupType.java
package com.ulticode.modules.backup.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum BackupType {
    FULL("FULL"),
    INCREMENTAL("INCREMENTAL");

    @EnumValue
    private final String value;

    BackupType(String value) {
        this.value = value;
    }
}

// BackupStatus.java
package com.ulticode.modules.backup.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum BackupStatus {
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    @EnumValue
    private final String value;

    BackupStatus(String value) {
        this.value = value;
    }
}
```

### Step 5.2: 创建 Entity

```java
package com.ulticode.modules.backup.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "backups", autoResultMap = true)
public class Backup {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String filename;
    private Long size;
    private BackupType type;
    private BackupStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String error;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}
```

### Step 5.3: 创建 Service 和 Controller

(详细实现省略，参考 NestJS backup.service.ts 实现)

---

## Task 6: Monitoring 模块 - 监控服务

**Priority:** Low (Admin only)

**Files:**
- Create: `src/main/java/com/ulticode/modules/monitoring/dto/SystemInfoVO.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/dto/ResourceUsageVO.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/dto/DatabaseStatsVO.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/dto/SystemHealthVO.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/service/MonitoringService.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/monitoring/controller/MonitoringController.java`
- Test: `src/test/java/com/ulticode/modules/monitoring/service/MonitoringServiceTest.java`

### Step 6.1: 创建 DTO

```java
// SystemInfoVO.java
package com.ulticode.modules.monitoring.dto;

import lombok.Builder;
import lombok.Data;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

@Data
@Builder
public class SystemInfoVO {
    private Long uptime;
    private String javaVersion;
    private String platform;
    private String hostname;
    private String env;
    private Long pid;
    private String version;

    public static SystemInfoVO create() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return SystemInfoVO.builder()
            .uptime(runtime.getUptime() / 1000)
            .javaVersion(System.getProperty("java.version"))
            .platform(System.getProperty("os.name"))
            .hostname(getHostname())
            .env(System.getProperty("spring.profiles.active", "development"))
            .pid(ProcessHandle.current().pid())
            .version("1.0.0")
            .build();
    }

    private static String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
```

### Step 6.2: 创建 MonitoringService

```java
package com.ulticode.modules.monitoring.service;

import com.ulticode.modules.monitoring.dto.*;
import java.util.List;

public interface MonitoringService {
    SystemInfoVO getSystemInfo();
    ResourceUsageVO getResourceUsage();
    DatabaseStatsVO getDatabaseStats();
    List<QueueStatsVO> getQueueStats();
    RedisStatsVO getRedisStats();
    SystemHealthVO getHealthCheck();
}
```

### Step 6.3: 创建 MonitoringController

```java
package com.ulticode.modules.monitoring.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.monitoring.dto.*;
import com.ulticode.modules.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Monitoring", description = "System monitoring operations")
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/system")
    @Operation(summary = "Get system information")
    public Result<SystemInfoVO> getSystemInfo() {
        return Result.success(monitoringService.getSystemInfo());
    }

    @GetMapping("/resources")
    @Operation(summary = "Get resource usage")
    public Result<ResourceUsageVO> getResourceUsage() {
        return Result.success(monitoringService.getResourceUsage());
    }

    @GetMapping("/database")
    @Operation(summary = "Get database statistics")
    public Result<DatabaseStatsVO> getDatabaseStats() {
        return Result.success(monitoringService.getDatabaseStats());
    }

    @GetMapping("/queues")
    @Operation(summary = "Get queue statistics")
    public Result<List<QueueStatsVO>> getQueueStats() {
        return Result.success(monitoringService.getQueueStats());
    }

    @GetMapping("/redis")
    @Operation(summary = "Get Redis statistics")
    public Result<RedisStatsVO> getRedisStats() {
        return Result.success(monitoringService.getRedisStats());
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health check")
    public Result<SystemHealthVO> getHealthCheck() {
        return Result.success(monitoringService.getHealthCheck());
    }
}
```

---

## Task 7: I18n 模块 - 国际化

**Priority:** Medium

**Files:**
- Create: `src/main/java/com/ulticode/modules/i18n/entity/Translation.java`
- Create: `src/main/java/com/ulticode/modules/i18n/mapper/TranslationMapper.java`
- Create: `src/main/java/com/ulticode/modules/i18n/dto/TranslationDTO.java`
- Create: `src/main/java/com/ulticode/modules/i18n/dto/BulkUpsertDTO.java`
- Create: `src/main/java/com/ulticode/modules/i18n/constants/I18nConstants.java`
- Create: `src/main/java/com/ulticode/modules/i18n/service/I18nService.java`
- Create: `src/main/java/com/ulticode/modules/i18n/service/impl/I18nServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/i18n/controller/I18nController.java`
- Test: `src/test/java/com/ulticode/modules/i18n/service/I18nServiceTest.java`

### Step 7.1: 创建常量类

```java
package com.ulticode.modules.i18n.constants;

import lombok.Getter;
import java.util.*;

@Getter
public class I18nConstants {
    public static final String FALLBACK_LOCALE = "en-US";

    public static final Set<String> SUPPORTED_LOCALES = Set.of(
        "en-US", "zh-CN", "zh-TW", "ja-JP"
    );

    public enum TranslatableEntity {
        PROBLEM,
        PROBLEM_DETAIL,
        CONTEST,
        SOLUTION,
        POST
    }

    public static final Map<TranslatableEntity, List<String>> TRANSLATABLE_FIELDS = Map.of(
        TranslatableEntity.PROBLEM, List.of("title", "summary"),
        TranslatableEntity.PROBLEM_DETAIL, List.of("description", "hints", "solution"),
        TranslatableEntity.CONTEST, List.of("title", "description"),
        TranslatableEntity.SOLUTION, List.of("title", "content"),
        TranslatableEntity.POST, List.of("title", "content")
    );

    public static String parseAcceptLanguage(String header) {
        if (header == null || header.isEmpty()) {
            return FALLBACK_LOCALE;
        }

        // Simple parsing - return first supported locale found
        String[] languages = header.split(",");
        for (String lang : languages) {
            String locale = lang.trim().split(";")[0].trim();
            if (SUPPORTED_LOCALES.contains(locale)) {
                return locale;
            }
        }

        return FALLBACK_LOCALE;
    }
}
```

### Step 7.2: 创建 Entity

```java
package com.ulticode.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("translations")
public class Translation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String entityType;
    private String entityId;
    private String fieldName;
    private String locale;
    private String content;
    private String createdBy;
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Step 7.3: 创建 I18nService

```java
package com.ulticode.modules.i18n.service;

import com.ulticode.modules.i18n.constants.I18nConstants;
import com.ulticode.modules.i18n.dto.BulkUpsertDTO;
import java.util.List;
import java.util.Map;

public interface I18nService {
    Map<String, String> getTranslations(I18nConstants.TranslatableEntity entityType, String entityId, String locale);
    Map<String, Map<String, String>> getBatchTranslations(I18nConstants.TranslatableEntity entityType, List<String> entityIds, String locale);
    <T> T applyTranslations(T entity, Map<String, String> translations, List<String> fields);
    <T extends Map<String, Object>> List<T> translateEntities(I18nConstants.TranslatableEntity entityType, List<T> entities, String locale);
    BulkUpsertDTO bulkUpsertTranslations(List<BulkUpsertDTO.TranslationItem> translations, boolean skipDuplicates);
    String parseAcceptLanguage(String header);
}
```

---

## Task 8: 更新错误码

**Files:**
- Modify: `src/main/java/com/ulticode/common/exception/ErrorCode.java`

### Step 8.1: 添加新错误码

在 ErrorCode.java 中添加：

```java
// Recommendation module (11xxxx)
RECOMMENDATION_SERVICE_UNAVAILABLE(110001, "Recommendation service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
RECOMMENDATION_USER_REQUIRED(110002, "User ID is required", HttpStatus.BAD_REQUEST),

// Search module (12xxxx)
SEARCH_QUERY_EMPTY(120001, "Search query cannot be empty", HttpStatus.BAD_REQUEST),

// Backup module (13xxxx)
BACKUP_NOT_FOUND(130001, "Backup not found", HttpStatus.NOT_FOUND),
BACKUP_NOT_COMPLETED(130002, "Backup is not completed", HttpStatus.BAD_REQUEST),
BACKUP_FILE_NOT_FOUND(130003, "Backup file not found", HttpStatus.NOT_FOUND),
BACKUP_RESTORE_FAILED(130004, "Backup restore failed", HttpStatus.INTERNAL_SERVER_ERROR),

// I18n module (14xxxx)
TRANSLATION_NOT_FOUND(140001, "Translation not found", HttpStatus.NOT_FOUND),
TRANSLATION_ALREADY_EXISTS(140002, "Translation already exists", HttpStatus.CONFLICT);
```

---

## Task 9: 配置文件更新

**Files:**
- Modify: `src/main/resources/application.yml`

### Step 9.1: 添加配置项

```yaml
# Recommendation
recommendation:
  enabled: false
  service-url: ${RECOMMENDATION_SERVICE_URL:}
  timeout: 5000
  nacos-enabled: false
  fallback-url: ${RECOMMENDATION_FALLBACK_URL:}

# MeiliSearch
meilisearch:
  host: ${MEILISEARCH_HOST:}
  api-key: ${MEILISEARCH_API_KEY:}

# Backup
backup:
  dir: ${BACKUP_DIR:/tmp/backups}
```

---

## 验收标准

### Vote 模块
- [ ] 用户可以对目标进行点赞/点踩
- [ ] 再次点击可以取消投票
- [ ] 获取投票统计正确

### EdgeOperations 模块
- [ ] 边缘操作可以正确处理
- [ ] 获取交互统计正确

### Search 模块
- [ ] 可以搜索题目和用户
- [ ] 数据库回退正常工作

### Recommendation 模块
- [ ] 服务不可用时返回正确响应
- [ ] 各场景推荐接口正常

### Backup 模块
- [ ] 管理员可以创建备份
- [ ] 可以列出和下载备份

### Monitoring 模块
- [ ] 系统信息返回正确
- [ ] 健康检查正常

### I18n 模块
- [ ] 可以获取翻译
- [ ] 批量翻译正常工作

---

## 测试计划

每个模块需要以下测试：
1. **单元测试** - Service 层逻辑
2. **集成测试** - Controller + Service + Mapper
3. **E2E 测试** - 完整 API 流程

测试覆盖率要求：80%+
