# 题解详情页数据缺失分析计划

> **验证日期**: 2026-04-30
> **验证结论**: 前端 Tags 解析已生效，统计数据非全零，问题定位需修正

## 问题总览

| # | 问题现象 | 实际状态 | 根因 | 优先级 |
|---|---------|---------|------|--------|
| 1 | 标签区域显示异常 | **已修复** ✅ | 前端已有 `parseTags()` 处理 JSON 字符串 | — |
| 1b| `tags` 类型契约断裂 | **待修复** | API 返回 `String`，前端声明 `string[]` | P1 |
| 2 | `userVote` 始终为 0 | **待修复** | `toVO()` 未查询当前用户投票状态 | P2 |
| 3 | `flair`/`badges`/`topic` 缺失 | **低优先级** | 后端无此字段，但前端 `v-if` 优雅降级 | P3 |
| 4 | 种子数据内容质量 | **已丰富** ✅ | V13 迁移已补充完整代码和解析 | — |

---

## 详细分析

### 问题 1：Tags 解析（状态：运行时正常，类型不匹配）

**数据流验证：**
```
DB: tags = '["map","javascript"]' (JSON string)
  → Backend SolutionVO.tags: String — API 返回 JSON 字符串
  → Frontend parseTags(item.tags) → ["map","javascript"] ✅
  → Component v-for="tag in props.item.tags" — 正常渲染数组
```

**验证结果：**
- `console/src/api/solution.ts` 第 41-52 行已有 `parseTags()`，安全处理 JSON 字符串/数组
- `transformApiSolution()` 第 89 行已调用 `parseTags(item.tags)`
- `SolutionDetail.vue` 第 343-352 行正常渲染，**无逐字符问题**

**遗留问题：**
```typescript
// 前端声明（第 30 行）
tags: string[];  // 期望数组

// API 实际返回
"[\"map\",\"javascript\"]"  // String

// 运行时通过 parseTags 转换后才是数组
```

- TypeScript 类型与实际 API 响应不匹配
- 后端 `toVO()` 已解析 `tagsList`（第 367 行），但前端类型未声明此字段，导致重复解析

**修复方案：**
- **后端**：让 `tags` 字段直接返回 `List<String>`（ Jackson 序列化为 JSON 数组）
- **前端**：移除 `parseTags()` 调用，直接使用 `item.tags` 作为数组

---

### 问题 2：统计数据（状态：非全零，userVote 缺失）

**验证结果：**

| 字段 | 后端来源 | 前端显示 | 状态 |
|------|----------|----------|------|
| `stats.views` | `solution.views` | `item.views ?? 0` | ✅ 正常 |
| `stats.likes` | `edge_operations` COUNT | `item.likes ?? 0` | ✅ 实时计算 |
| `stats.dislikes` | `edge_operations` COUNT | `item.dislikes ?? 0` | ✅ 实时计算 |
| `stats.comments` | `solution_comments` COUNT | `item.comments ?? 0` | ✅ 实时计算 |
| `score` | `likes - dislikes` | `item.score ?? 0` | ✅ 实时计算 |
| `userVote` | **未查询** | `resolveUserVote(undefined) → 0` | ⚠️ **始终显示未投票** |

**根因修正：**
原分析认为"统计数据全为 0"是**错误的**。实际 `toVO()`（第 358-391 行）已从数据库实时计算并填充：
- `likes`/`dislikes`：从 `edge_operations` 表 COUNT（第 379-383 行）
- `comments`：从 `solution_comments` 表 COUNT（第 383 行）
- `score`：`likes - dislikes`（第 388 行）

**真正缺失的是 `userVote`**：后端未接收当前用户 ID，未查询该用户对当前 solution 的投票状态。

**修复方案：**
1. API 接口增加可选的 `userId` 查询参数（已有，如 `/api/solutions/{id}?userId=xxx`）
2. `toVO()` 接收 `currentUserId` 参数，查询 `edge_operations` 获取该用户的投票记录
3. 填充 `userVote: 1 | -1 | 0`

---

### 问题 3：UI 装饰字段（状态：缺失但无害）

**缺失字段：**
- `flair` — 用户徽章/标签（如 "作者"、"专家"）
- `badges` — 题解标签列表
- `topic` / `topicName` / `topicTranslated` — 关联论坛话题
- `highlight` — 高亮显示标记
- `languageFilter` — 语言过滤标识
- `isPinned` / `isLocked` — 置顶/锁定状态

**影响评估：**
- 前端均使用 `v-if` 条件渲染，缺失时自动隐藏，**无报错**
- 属于体验增强字段，非核心功能
- 如 `topicLabel` computed 在缺失时回退到默认文本（`SolutionDetail.vue` 第 46-52 行）

---

### 问题 4：种子数据（状态：已丰富）

**验证结果：**
- V13 迁移（`V13__solution_enrich_content.sql`）已将 sol-001 ~ sol-008 的 content 替换为完整 Markdown
- 包含：思路分析 + 代码实现 + 复杂度分析
- V23 迁移新增 ~92 条 solution 种子数据

**结论：无需修复。**

---

## 修正后实施计划

### Phase 1：后端 Tags 返回数组（后端，低风险）

**目标**：消除类型不匹配，去掉前端冗余解析

**文件**：`backend-spring/src/main/java/com/ulticode/modules/solution/dto/SolutionVO.java`

**修改**：
```java
// 移除
private String tags;

// 改为（使用 @JsonProperty 保持字段名兼容）
@JsonProperty("tags")
private List<String> tagsList;
```

**文件**：`backend-spring/src/main/java/com/ulticode/modules/solution/service/impl/SolutionServiceImpl.java`

**修改**：`toVO()` 中直接设置 `tagsList`，无需双重解析

**文件**：`console/src/api/solution.ts`

**修改**：移除 `parseTags()` 调用，`item.tags` 直接作为数组使用

**验证**：API 响应中 `tags` 字段应为 `["map","javascript"]` 而非 `"[\"map\",\"javascript\"]"`

---

### Phase 2：添加 userVote 查询（后端，中风险）

**目标**：让用户看到自己是否已投票

**文件**：`backend-spring/src/main/java/com/ulticode/modules/solution/controller/SolutionController.java`

**修改**：
1. GET `/api/solutions/{id}` 接收 `userId` 查询参数
2. 传递给 `getSolutionById(id, userId)`

**文件**：`backend-spring/src/main/java/com/ulticode/modules/solution/service/impl/SolutionServiceImpl.java`

**修改**：
```java
public SolutionVO toVO(Solution solution, String currentUserId) {
    // ... 现有逻辑 ...
    
    // 添加：查询当前用户投票状态
    if (currentUserId != null) {
        EdgeOperation userVote = edgeOperationMapper.selectOne(
            new LambdaQueryWrapper<EdgeOperation>()
                .eq(EdgeOperation::getOperatorId, currentUserId)
                .eq(EdgeOperation::getTargetId, solution.getId())
                .eq(EdgeOperation::getTargetType, EdgeOperationTargetType.SOLUTION)
        );
        if (userVote != null) {
            vo.setUserVote(userVote.getOperationType() == EdgeOperationType.VOTE_UP ? 1 : -1);
        }
    }
    
    return vo;
}
```

**文件**：`backend-spring/src/main/java/com/ulticode/modules/solution/dto/SolutionVO.java`

**添加字段**：
```java
private Integer userVote;  // 1 = upvote, -1 = downvote, null/0 = no vote
```

**前端验证**：投票后刷新页面，`PostActions` 组件应显示已投票状态

---

### Phase 3：补齐 UI 装饰字段（后端+前端，低风险）

**目标**：提升题解展示体验

**字段优先级**：
1. `topic` / `topicName` — 关联题目话题（从 `problem` 表获取）
2. `isPinned` — 置顶标记（solution 表已存在 `is_pinned` 列待添加）
3. `flair` / `badges` — 作者徽章（从用户成就系统获取）

**文件**：
- `backend-spring/.../dto/SolutionVO.java` — 添加字段
- `backend-spring/.../service/impl/SolutionServiceImpl.java` — 在 `toVO()` 中填充
- `console/src/types/solution.ts` — 更新类型（如需要）

---

## 验证清单

### Phase 1 验证
- [ ] API `/api/solutions/{id}` 返回的 `tags` 是数组 `["map","javascript"]`
- [ ] 前端不再调用 `JSON.parse()` 解析 tags
- [ ] 标签正常渲染，无逐字符问题

### Phase 2 验证
- [ ] 未登录用户：userVote 为 0，显示未投票
- [ ] 登录用户未投票：userVote 为 0
- [ ] 登录用户已投票：userVote 为 1 或 -1，UI 显示已投票状态
- [ ] 投票后刷新页面，状态保持

### Phase 3 验证
- [ ] topic 显示正确（如 "Array"、"Hash Table"）
- [ ] flair/badges 在有数据时显示
- [ ] 无数据时前端优雅降级（不显示/显示默认文本）

---

## 附录：原始错误分析

**原问题 1 误判原因**：
- 看到 API 返回 `tags` 为 String 类型，误以为前端未解析
- 实际前端已有 `parseTags()` 处理，运行时正常
- 真正的问题是**类型契约断裂**而非功能缺陷

**原问题 2 误判原因**：
- 截图中可能展示的是特定场景（如全新安装、数据未初始化）
- 实际 `toVO()` 已实现实时统计计算
- 真正缺失的是 `userVote` 而非所有统计字段

**原问题 3 状态更新**：
- V13 迁移已丰富种子数据，此问题已解决
