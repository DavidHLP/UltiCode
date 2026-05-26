# Plan: Forum Frontend-Backend Alignment

## Summary
对齐论坛模块前后端的数据类型、查询逻辑和字段传递，解决 JSON 字段（tags/media/stats）在自定义 SQL 中绕过 TypeHandler 导致返回字符串、sortBy 参数后端未实现、community/flair 字段 VO 缺失、前端本地过滤/排序应迁移到服务端等问题。

## User Story
As a 论坛用户,
I want 论坛帖子列表和详情页的数据完整且类型正确（tags 为数组、stats 为对象、media 正确解析、排序生效）,
So that 论坛功能正常运作，不会因类型不匹配导致前端解析错误或显示异常。

## Problem → Solution
**Current state**: ForumPostMapper 自定义 SQL 绕过 MyBatis-Plus TypeHandler，导致 tags/media/stats 等 JSON 字段返回原始字符串而非解析后的 Java 对象；sortBy 参数被后端忽略；community/flair 字段在 VO 中缺失；前端在本地做过滤/排序/分页，效率低且与后端不一致。
**Desired state**: 自定义 SQL 正确应用 TypeHandler；后端实现 sortBy 排序；VO 包含 community/flair 字段；前端将过滤/排序/分页委托给后端 API。

## Metadata
- **Complexity**: Large
- **Source PRD**: docs/forum-frontend-backend-alignment-analysis.md
- **PRD Phase**: Phase 1 (Critical Fixes)
- **Estimated Files**: 15-20

---

## UX Design

### Before
```
┌─────────────────────────────────────────────┐
│  Forum Feed                                 │
│  ┌───────────────────────────────────────┐  │
│  │ Post Card                             │  │
│  │  tags: "[\"tag1\",\"tag2\"]" (字符串!)  │  │
│  │  stats: "{"views":0}" (字符串!)        │  │
│  │  communityName: (缺失)                │  │
│  │  communitySlug: (缺失)                │  │
│  │  commentCount: (缺失)                 │  │
│  │  isAuthor: (缺失)                     │  │
│  │  排序: 不生效（后端忽略 sortBy）         │  │
│  └───────────────────────────────────────┘  │
│  [前端本地排序 - 性能差]                     │
│  [前端本地分页 - 数据不一致]                  │
└─────────────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────────────┐
│  Forum Feed                                 │
│  ┌───────────────────────────────────────┐  │
│  │ Post Card                             │  │
│  │  tags: ["tag1","tag2"] (数组)          │  │
│  │  stats: {views:0, ...} (对象)         │  │
│  │  communityName: "General" (字段填充)   │  │
│  │  communitySlug: "general" (字段填充)   │  │
│  │  commentCount: 5 (字段填充)            │  │
│  │  isAuthor: true (字段填充)             │  │
│  │  排序: 按最新/热门/评论数（后端实现）    │  │
│  └───────────────────────────────────────┘  │
│  [后端排序+分页 - 数据一致且高效]             │
└─────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| 帖子列表排序 | 前端本地排序，sortBy 参数发送但被忽略 | 后端 SQL ORDER BY 实现，sortBy 参数生效 | 3 种排序: latest/hot/most_commented |
| 帖子列表分页 | 前端本地 slice 分页 | 后端 LIMIT/OFFSET 分页 | 配合排序迁移 |
| tags 字段显示 | JSON 字符串可能被原样展示 | 正确解析为数组展示 | TypeHandler 修复 |
| stats 字段 | 字符串需前端 JSON.parse | 后端返回解析后的对象 | TypeHandler 修复 |
| community/flair 字段 | VO 中声明但 Service 未赋值，前端收不到 | Service 层批量查询 ForumCommunity 填充 communityName/communitySlug；flairType/flairLabel 直接从 Entity 复制 | VO 字段 @JsonInclude(NON_NULL) 导致 null 字段消失 |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/.../forum/mapper/ForumPostMapper.java` | 自定义 SQL 是 TypeHandler 绕过的根源 |
| P0 (critical) | `backend-spring/.../forum/entity/ForumPost.java` | JSON 字段定义和 TypeHandler 注解 |
| P0 (critical) | `backend-spring/.../forum/dto/ForumPostVO.java` | VO 结构及缺失字段 |
| P0 (critical) | `console/src/views/forum/ForumFeedView.vue` | 前端排序/过滤/分页逻辑 |
| P1 (important) | `backend-spring/.../forum/service/impl/ForumPostServiceImpl.java` | Service 层数据转换逻辑 |
| P1 (important) | `backend-spring/.../forum/service/impl/ForumServiceImpl.java` | sortBy 处理 |
| P1 (important) | `console/src/api/forum.ts` | API 调用和类型定义 |
| P2 (reference) | `backend-spring/.../forum/controller/ForumController.java` | API 端点和参数 |
| P2 (reference) | `console/src/views/forum/ForumThreadView.vue` | 详情页数据消费 |
| P2 (reference) | `console/src/views/forum/components/ForumPostCard.vue` | 组件字段依赖 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus TypeHandler in XML | MyBatis-Plus 官方文档 | 自定义 SQL 需显式指定 TypeHandler |
| JacksonTypeHandler | MyBatis-Plus 官方文档 | 在 @Select 注解中需用 `typeHandler=` 指定 |

No external research needed — feature uses established internal patterns (之前的 audit 对齐工作已验证此方法)。

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: audit module alignment (commit 7432cc75f)
- VO 字段使用 camelCase
- API 响应使用统一信封 `{ code, message, data }`
- 前端类型定义与 VO 字段一一对应

### ERROR_HANDLING
// SOURCE: backend-spring/.../forum/controller/ForumController.java
```java
return Result.success(pageResult);
return Result.error(ErrorCode.PARAMS_ERROR);
```
后端使用 `Result.success()` / `Result.error()` 统一响应格式。

### LOGGING_PATTERN
// SOURCE: backend-spring/.../forum/service/impl/ForumPostServiceImpl.java
```java
log.error("Failed to find all posts", e);
```
Service 层使用 `log.error()` 记录异常，向上抛出。

### REPOSITORY_PATTERN
// SOURCE: backend-spring/.../forum/mapper/ForumPostMapper.java
自定义 SQL 使用 `@Select` 注解，而非 XML mapper:
```java
@Select("<script>SELECT * FROM forum_post WHERE ... </script>")
IPage<ForumPost> findAllPosts(...);
```
**关键发现**: 这种方式不会自动应用实体类上的 `@TableField(typeHandler = ...)` 注解。

### SERVICE_PATTERN
// SOURCE: backend-spring/.../forum/service/impl/ForumPostServiceImpl.java
Entity → VO 转换在 Service 层手动完成:
```java
ForumPostVO vo = new ForumPostVO();
BeanUtils.copyProperties(post, vo);
```
**问题**: `BeanUtils.copyProperties` 不处理类型不匹配（如 JSON 字段在 Entity 已解析但 VO 字段类型不同时）。

### TYPE_HANDLER_FIX_PATTERN
// SOURCE: audit module alignment
自定义 SQL 中 JSON 字段需要显式指定 TypeHandler:
```sql
-- 错误: 绕过 TypeHandler
SELECT * FROM forum_post

-- 正确: 显式指定 TypeHandler
SELECT id, title, content,
  tags AS tags,
  JSON_UNQUOTE(tags) as tags_str,  -- 如果需要
  ...
```
**实际解决方案**: 在 @Select 中使用 `${ew.customSqlSegment}` 配合 MyBatis-Plus QueryWrapper 而非手写 SQL，或在查询后手动设置 JSON 字段。

**推荐方案**: 将 `findAllPosts` 改为使用 MyBatis-Plus 的 `selectPage` + QueryWrapper，这样 TypeHandler 自动生效。只有在需要 JOIN 或子查询时才用自定义 SQL。

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../forum/mapper/ForumPostMapper.java` | UPDATE | 重写查询方法，使用 QueryWrapper 替代手写 SQL |
| `backend-spring/.../forum/service/impl/ForumPostServiceImpl.java` | UPDATE | 添加 sortBy 排序逻辑，修复 Entity→VO 转换 |
| `backend-spring/.../forum/service/impl/ForumServiceImpl.java` | UPDATE | 传递 sortBy 到 mapper 层 |
| `backend-spring/.../forum/dto/ForumPostVO.java` | UPDATE | 添加 community/flair 字段 |
| `backend-spring/.../forum/dto/ForumPostThreadVO.java` | VERIFY | 确认是否也有 communityName/commentCount 缺失问题 |
| `backend-spring/.../forum/controller/ForumController.java` | UPDATE | 更新 API 参数定义 |
| `console/src/api/forum.ts` | UPDATE | 更新 API 类型定义和参数 |
| `console/src/views/forum/ForumFeedView.vue` | UPDATE | 移除本地排序/分页，使用后端排序+分页 |
| `console/src/views/forum/ForumThreadView.vue` | UPDATE | 适配新的 VO 字段 |
| `console/src/views/forum/components/ForumPostCard.vue` | UPDATE | 使用 community/flair 字段 |
| `console/src/types/forum.ts` | UPDATE | 更新类型定义匹配新 VO |
| `console/src/views/forum/composables/useForumPosts.ts` | UPDATE (如存在) | 适配分页逻辑 |

## NOT Building

- 论坛帖子编辑功能的改动（仅读取对齐）
- 新的论坛 API 端点（仅修复现有端点）
- 前端论坛路由结构改动
- 论坛评论/回复的对齐（仅帖子相关）
- 推荐系统相关改动
- Flyway 数据库迁移（schema 无变化）

---

## Step-by-Step Tasks

### Task 1: 重写 ForumPostMapper 查询方法 — 修复 TypeHandler 绕过
- **ACTION**: 将 `findAllPosts` 方法从手写 `@Select` SQL 改为 MyBatis-Plus 的 `selectPage` + QueryWrapper
- **IMPLEMENT**:
  1. 删除 `findAllPosts` 的 `@Select` 注解和手写 SQL
  2. 改为使用 `IPage<ForumPost> selectPage(IPage<ForumPost> page, @Param("ew") Wrapper<ForumPost> wrapper)`
  3. 这是 MyBatis-Plus 内置方法，自动应用 TypeHandler
  4. 如果有其他自定义查询也需要类似处理（如 `findByCommunity`），同样改用 QueryWrapper
- **MIRROR**: MyBatis-Plus 的 `BaseMapper.selectPage()` 标准用法
- **IMPORTS**: `com.baomidou.mybatisplus.core.conditions.query.QueryWrapper`, `com.baomidou.mybatisplus.core.metadata.IPage`
- **GOTCHA**: 确保查询条件（deleted 状态、community 过滤等）都迁移到 QueryWrapper 中
- **VALIDATE**: 启动后端，调用 `/api/forum/posts`，检查返回的 tags 字段是否为 JSON 数组而非字符串

### Task 2: 后端实现 sortBy 排序逻辑
- **ACTION**: 在 ForumPostServiceImpl 中实现 sortBy 参数的排序逻辑
- **IMPLEMENT**:
  ```java
  // 在构建 QueryWrapper 时添加排序
  private void applySortBy(QueryWrapper<ForumPost> wrapper, String sortBy) {
      if (sortBy == null || sortBy.isEmpty() || "latest".equals(sortBy)) {
          wrapper.orderByDesc("create_time");
      } else if ("hot".equals(sortBy)) {
          wrapper.orderByDesc("view_count");  // 或基于 stats 中的字段
      } else if ("most_commented".equals(sortBy)) {
          wrapper.orderByDesc("comment_count");
      } else {
          wrapper.orderByDesc("create_time"); // 默认按时间
      }
  }
  ```
- **MIRROR**: 参照 audit 模块的排序实现模式
- **IMPORTS**: 无新 import
- **GOTCHA**: "hot" 排序需要确认是用 view_count 还是 stats 中的某个计算字段；建议先用 view_count，后续可优化为加权算法
- **VALIDATE**: 调用 `/api/forum/posts?sortBy=hot`，验证返回顺序是否按浏览量降序

### Task 3: ForumPostVO — 确认并补全 community/flair 字段
- **ACTION**: VO 中已声明 `communityName`/`communitySlug`/`flairType`/`flairLabel` 但 Service 层从未赋值，需修复
- **IMPLEMENT**:
  1. **flair 字段已存在于 VO**: `ForumPostVO.java` 已有 `flairType` (line 64) 和 `flairLabel` (line 69)，且 Entity `ForumPost` 也直接有 `flairType`/`flairLabel` 列 — 无需额外映射
  2. **community 字段已存在于 VO**: `ForumPostVO.java` 已有 `communityName` (line 29) 和 `communitySlug` (line 34)，但 Service 层从未赋值 — 需在 `convertToPostVO` 中填充
  3. **commentCount 已存在于 VO**: `ForumPostVO.java` 已有 `commentCount` (line 124)，但 Service 层从未赋值 — 需从 `stats.comments` 提取或单独计算
  4. **isAuthor 已存在于 VO**: `ForumPostVO.java` 已有 `isAuthor` (line 149)，但 Service 层从未赋值 — 需比较 `userId` 与当前用户
- **MIRROR**: audit 模块中 `PerformerStat` record 的 "resolve at backend, flatten in VO" 模式
- **IMPORTS**: 无新 import
- **GOTCHA**: `@JsonInclude(NON_NULL)` 导致未赋值字段直接从 JSON 中消失，前端收不到这些字段。修复后字段将被正确序列化。
- **VALIDATE**: API 返回的 VO 中 `communityName`/`communitySlug`/`commentCount`/`isAuthor` 不再缺失

### Task 4: 修复 ForumPostServiceImpl convertToPostVO — 填充缺失字段
- **ACTION**: 在 `convertToPostVO` 方法中补充 4 个从未赋值的 VO 字段
- **IMPLEMENT**:
  1. **communityName/communitySlug**: 当前 `convertToPostVO` 只设置 `communityId` (line 155)，但 VO 中的 `communityName`/`communitySlug` 始终为 null。解决方案：在 `convertToPostVO` 中增加 `ForumCommunity community` 参数（或从已有 `communityMapper` 获取），当 community 不为 null 时设置：
  ```java
  if (community != null) {
      vo.setCommunityName(community.getName());
      vo.setCommunitySlug(community.getSlug());
  }
  ```
  需要修改 `batchLoadAuthors` 以同时批量加载 `ForumCommunity`，避免 N+1 查询：
  ```java
  Set<String> communityIds = posts.stream().map(ForumPost::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());
  Map<String, ForumCommunity> communityMap = communityIds.stream()
      .map(communityMapper::selectById)
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(ForumCommunity::getId, Function.identity()));
  ```
  2. **commentCount**: 从 `stats` 中提取。当 `stats instanceof Map` 时：
  ```java
  if (post.getStats() instanceof Map) {
      Map<String, Object> statsMap = (Map<String, Object>) post.getStats();
      vo.setCommentCount(statsMap.get("comments") instanceof Number ? ((Number) statsMap.get("comments")).longValue() : 0L);
  }
  ```
  3. **isAuthor**: 比较帖子作者与当前用户：
  ```java
  vo.setIsAuthor(userId != null && post.getUserId() != null && post.getUserId().equals(userId));
  ```
  4. **stats/media 类型安全**: 当前 `convertToPostVO` 中 stats 注入 likes/dislikes (lines 163-167) 和 tags 的 unchecked cast (line 158) 需要加固：
  ```java
  // stats: 确保永远返回 Map，即使 DB 中 stats 为 null
  Map<String, Object> statsMap = new LinkedHashMap<>();
  if (post.getStats() instanceof Map) {
      statsMap = new LinkedHashMap<>((Map<String, Object>) post.getStats());
  }
  statsMap.put("likes", vr.getLikes());
  statsMap.put("dislikes", vr.getDislikes());
  vo.setStats(statsMap);
  ```
- **MIRROR**: audit 模块中 "resolve at backend, flatten in VO" 的 PerformerStat 模式 — 在 Service 层主动查询关联数据并填入 VO
- **IMPORTS**: `java.util.function.Function`
- **GOTCHA**:
  - `convertToPostVO` 是所有帖子接口的共享方法，新增 community 参数会影响所有调用点。建议改为 `convertToPostVO(post, userId, author, community)` 或在 Service 层先批量查询 community 再传入
  - `getPostThread` (line 124-135) 中的 `convertToPostVO` 调用也需更新，但该方法的 `ForumServiceImpl.getPostThread` (line 43-49) 已经修复了 comments 未设置的问题（在 ForumServiceImpl 中做了 `thread.setComments(...)`）。需确认 ForumPostServiceImpl 中的 `getPostThread` 是否还存在同样的 bug（line 127 获取了 comments 但 line 132-134 未设置）
- **VALIDATE**: 调用 API 验证 VO 中所有字段类型和值正确，特别是 `communityName`/`communitySlug`/`commentCount`/`isAuthor`

### Task 5: 更新 ForumController API 参数
- **ACTION**: 确保 sortBy 参数正确传递到 Service 层
- **IMPLEMENT**:
  1. 检查 Controller 方法是否已接收 sortBy 参数
  2. 如果已有但未使用，确认传递到 Service
  3. 如果缺失，添加 `@RequestParam(defaultValue = "latest") String sortBy`
  4. 同时检查 page/limit 参数是否正确传递
- **MIRROR**: 其他 Controller 的分页排序参数模式
- **IMPORTS**: 无新 import
- **GOTCHA**: 确保 sortBy 的合法值在文档中说明
- **VALIDATE**: Swagger/OpenAPI 文档中能看到 sortBy 参数

### Task 6: 更新前端 API 类型定义
- **ACTION**: 更新 `console/src/api/forum.ts` 和 `console/src/types/forum.ts` 匹配新的后端 VO
- **IMPLEMENT**:
  1. 更新 ForumPost 接口，添加 `communityId`, `communityName`, `flairId`, `flairName` 字段
  2. 确保 `tags` 类型为 `string[]`（不是 `string`）
  3. 确保 `stats` 类型为 `object` 或具体接口（不是 `string`）
  4. 确保 `media` 类型为正确类型（不是 `string`）
  5. API 调用函数中添加 sortBy/page/limit 参数
- **MIRROR**: 参照 audit 模块的前端类型定义方式
- **IMPORTS**: 无新 import
- **GOTCHA**: 向后兼容 — 如果 tags 在某些旧数据中仍为字符串，前端可能需要类型守卫
- **VALIDATE**: TypeScript 编译无错误

### Task 7: 重构 ForumFeedView — 迁移排序/分页到后端
- **ACTION**: 移除前端本地排序和分页逻辑，改用后端 API 的 sortBy/page/limit 参数
- **IMPLEMENT**:
  1. 移除 `computedPosts` 中的前端排序逻辑（如 `sort((a, b) => ...)`）
  2. 移除前端分页的 `slice()` 调用
  3. 将 sortBy 变量绑定到 API 调用参数
  4. 添加分页组件，使用后端返回的总数计算页数
  5. 页码变化时重新调用 API
- **MIRROR**: audit 报表视图的分页模式
- **IMPORTS**: 无新 import
- **GOTCHA**: 确保路由参数（如 `/forum/:sortBy`）正确映射到 API 参数；切换排序时重置页码到 1
- **VALIDATE**: 切换排序时，数据顺序实际变化（而非前端排序）；翻页时加载新数据

### Task 8: 更新 ForumPostCard — 使用 community/flair 字段
- **ACTION**: 更新组件使用新的 VO 字段
- **IMPLEMENT**:
  1. 模板中使用 `post.communityName` 显示社区名称
  2. 模板中使用 `post.flairName` 显示帖子标签
  3. 确保 tags 正确遍历（`v-for="tag in post.tags"`，此时 tags 是数组）
  4. 确保 stats 正确访问（`post.stats.views`，此时 stats 是对象）
- **MIRROR**: 其他列表组件的字段使用模式
- **IMPORTS**: 无新 import
- **GOTCHA**: 如果 tags 可能仍为字符串（向后兼容），添加类型守卫：
  ```ts
  const normalizedTags = computed(() => {
    if (typeof props.post.tags === 'string') return JSON.parse(props.post.tags)
    return props.post.tags
  })
  ```
  **但优先修复后端**，确保前端不需要这种守卫。
- **VALIDATE**: 帖子卡片正确显示社区、标签、标签页、统计信息

### Task 9: 更新 ForumThreadView 适配新 VO
- **ACTION**: 确保帖子详情页正确消费新的 VO 字段
- **IMPLEMENT**:
  1. 检查 `ForumPostThreadVO` 中的字段是否也需要 community/flair
  2. 更新模板中的字段引用
  3. 确保 thread 中的 stats/tags/media 字段类型正确
- **MIRROR**: ForumPostCard 的更新模式
- **IMPORTS**: 无新 import
- **GOTCHA**: Thread VO 可能有额外的嵌套结构（如评论列表），确保不影响
- **VALIDATE**: 帖子详情页正确显示所有字段

### Task 10: 端到端验证
- **ACTION**: 启动前后端，完整测试论坛功能
- **IMPLEMENT**:
  1. 启动后端 `pm2 restart ulticode-9001`
  2. 启动前端 `pm2 restart ulticode-9002`
  3. 测试帖子列表页：排序切换、分页、字段显示
  4. 测试帖子详情页：字段显示、投票更新
  5. 检查浏览器控制台无类型错误
- **MIRROR**: 标准验证流程
- **IMPORTS**: N/A
- **GOTCHA**: 确保数据库中有测试数据（不同社区、不同标签的帖子）
- **VALIDATE**: 所有论坛页面正常工作，无控制台错误

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| ForumPostMapper TypeHandler | 插入带 JSON 字段的帖子，再查询 | tags 返回 List<String>，非字符串 | Yes — JSON 字段解析 |
| sortBy=latest | 按创建时间降序 | 最新帖子在前 | No |
| sortBy=hot | 按浏览量降序 | 浏览量最高的在前 | No |
| sortBy=most_commented | 按评论数降序 | 评论最多的在前 | No |
| sortBy=invalid | 无效排序值 | 默认按时间降序 | Yes — 参数校验 |
| ForumPostVO community/flair | 带社区和标签的帖子 | VO 包含 communityName/flairName | No |
| 分页 page=1,limit=10 | 第一页数据 | 10 条记录 + 总数 | No |
| 分页 page=999 | 超出范围的页码 | 空列表 | Yes — 边界 |

### Edge Cases Checklist
- [ ] tags 为 null 的帖子
- [ ] tags 为空数组的帖子 `[]`
- [ ] stats 为 null 的帖子
- [ ] media 为 null 的帖子
- [ ] communityId 为 null 的帖子
- [ ] flairId 为 null 的帖子
- [ ] 超大页码请求
- [ ] 无效 sortBy 值
- [ ] 并发排序切换请求

---

## Validation Commands

### Static Analysis
```bash
cd console && pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd console && pnpm lint
```
EXPECT: No lint errors

### Backend Compile
```bash
cd backend-spring && ./mvnw compile
```
EXPECT: Build success

### Backend Tests
```bash
cd backend-spring && ./mvnw test -Dtest="ForumPost*Test"
```
EXPECT: All tests pass

### Frontend Tests
```bash
cd console && pnpm test
```
EXPECT: All tests pass

### Browser Validation
```bash
pm2 restart ulticode-9001 ulticode-9002
```
EXPECT:
1. `/forum` 页面正常加载
2. 切换排序（最新/热门/最多评论）数据顺序变化
3. 帖子卡片显示社区名称和标签
4. tags 显示为数组元素
5. 浏览器控制台无类型相关错误

### Manual Validation
- [ ] 帖子列表 tags 显示为独立标签（非 JSON 字符串）
- [ ] 帖子列表 stats 中的浏览量/点赞数正确显示
- [ ] 切换排序时数据顺序实际变化
- [ ] 分页功能正常
- [ ] 帖子详情页字段完整
- [ ] community/flair 字段可见

---

## Acceptance Criteria
- [ ] ForumPostMapper 使用 QueryWrapper，TypeHandler 自动生效
- [ ] 后端 sortBy 参数实现 3 种排序
- [ ] ForumPostVO 包含 community/flair 字段
- [ ] 前端移除本地排序/分页，改用后端 API
- [ ] 前端类型定义与后端 VO 一致
- [ ] 所有 validation commands 通过
- [ ] 无 TypeScript 类型错误
- [ ] 无 ESLint 错误

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style (Result.success/error)
- [ ] Logging follows codebase conventions (log.error)
- [ ] VO 转换正确处理 JSON 字段
- [ ] No hardcoded values
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| convertToPostVO 签名变更影响所有调用点 | Medium | Medium | 新增 community 参数为可选，null 时不设置 communityName/communitySlug |
| QueryWrapper 无法完全替代自定义 SQL | Low | High | 保留自定义 SQL 方案作为备选，在其中显式指定 TypeHandler |
| 前端依赖 tags 为字符串的旧代码 | Medium | Low | 搜索所有 `JSON.parse(post.tags)` 并移除 |
| 排序字段映射错误（如 hot 的定义） | Low | Medium | 确认产品需求，先用 view_count |
| ForumPostServiceImpl.getPostThread 仍有 comments 未设置的 bug | High | High | 需确认 ForumServiceImpl.getPostThread (line 43-49) 是否已修复此问题 |

## Notes
- **flair 是 forum_posts 的内联列**（`flair_type` enum + `flair_label` varchar），没有单独的 `forum_flairs` 表。VO 中 `flairType`/`flairLabel` 直接从 Entity 复制即可，Service 层已有此逻辑 (line 157)。
- **community 需要 ID→名称映射**: `forum_posts.community_id` 是外键指向 `forum_communities`。VO 中 `communityName`/`communitySlug` 需要在 Service 层批量查询 `ForumCommunity` 后填充。`ForumCommunityMapper.selectById()` 可用于此目的。
- **4 个 VO 字段从未赋值**: `communityName`、`communitySlug`、`commentCount`、`isAuthor` — 全部因 `@JsonInclude(NON_NULL)` 而从 JSON 响应中消失。
- **ForumServiceImpl.getPostThread (line 43-49)** 已修复了 ForumPostServiceImpl.getPostThread 中 comments 未设置的 bug — ForumServiceImpl 在调用 `forumPostService.getPostThread()` 后又重新获取了 comments 并设置到 thread 上。
- **"hot" 排序的精确定义**需确认：纯浏览量？浏览量+点赞加权？建议先用 `views` 列，后续迭代优化。
- 此计划仅覆盖分析文档中的 Phase 1（Critical Fixes）。Phase 2（前端优化）和 Phase 3（高级功能）在后续计划中处理。
