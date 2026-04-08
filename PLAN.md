# 题解详情页数据缺失分析计划

## 问题总览

截图中题解详情页 (SolutionDetail) 存在三类数据问题：

| # | 问题现象 | 问题层级 | 根因 |
|---|---------|---------|------|
| 1 | 标签区域渲染为逐字符圆形按钮 `[ " m a p " , " j a v a s c r i p t " ]` | **前端数据映射** | 后端返回 `tags` 为 JSON 字符串 `'["map","javascript"]'`，前端未解析 |
| 2 | 统计数据全为 0（投票/浏览/评论） | **后端 VO 缺字段** | `SolutionVO` 缺少 `likes/dislikes/comments/score/flair/badges/stats` 等字段 |
| 3 | content 仅为简短描述，无实际代码 | **数据库种子数据** | 种子数据的 `content` 字段只存 Markdown 描述，不含代码块 |

---

## 详细分析

### 问题 1：Tags 逐字符渲染（前端问题）

**数据流追踪：**
```
DB: tags = '["map","javascript"]' (JSON column, stored as string)
  → Backend SolutionVO.tags: String (line 64) — 原样返回 JSON 字符串
  → Frontend API mapping: tags: item.tags (line 189) — 未 JSON.parse
  → Component v-for="tag in props.item.tags" (line 334) — 遍历字符串每个字符
```

**根因：** 后端 `SolutionVO.tags` 是 `String` 类型，返回 JSON 字符串。前端类型声明为 `string[]`，但映射时直接透传，未调用 `JSON.parse()`。Vue 的 `v-for` 对字符串会逐字符遍历。

**修复点：** `console/src/api/solution.ts` 三处映射（`fetchSolution`、`fetchSolutionFeed`、`fetchUserSolutions`）中对 `tags` 做 `JSON.parse`。

### 问题 2：统计数据全为 0（后端 VO 不完整）

**数据流追踪：**
```
Frontend expects: { stats: { views, comments, likes, dislikes }, score, flair, badges, topic, userVote, votes, likes, dislikes }
Backend SolutionVO has: { id, problemId, userId, title, content, summary, language, tags(String), views(Integer), isPublished, publishedAt, createdAt, updatedAt }
```

**缺失字段：**
- `stats` (嵌套对象) — 后端返回 flat `views`，前端期望 `stats.views`
- `score` — 完全缺失
- `flair` / `badges` — 完全缺失
- `topic` / `topicName` / `topicTranslated` — 完全缺失
- `votes` / `likes` / `dislikes` / `userVote` — 完全缺失
- `languageFilter` — 完全缺失
- `highlight` — 完全缺失

**根因：** `SolutionVO` 设计过于简单，与前端 `SolutionFeedItem` 类型严重不对齐。`toVO()` 使用 `BeanUtils.copyProperties()` 只复制同名字段。

**修复点：**
- 方案 A（快速）：在 `SolutionVO` 中补齐所有缺失字段，在 `toVO()` 中填充默认值或查询填充
- 方案 B（推荐）：创建专用的 `SolutionFeedVO` 匹配前端类型，feed 接口和 detail 接口分别返回不同 VO

### 问题 3：种子数据缺少代码内容（数据库问题）

**现状：**
```
sol-004 content: '# JS 哈希表\n\n使用原生 Map 对象。'
```

这是一个 Markdown 标题 + 一句话描述，不含任何代码实现。所有 8 条种子数据都是如此。

**根因：** 种子数据设计为"解题思路概述"而非"完整题解"。对于 LeetCode 风格平台，题解应包含：
1. 思路分析（已有，但太简短）
2. 实现代码（完全缺失）
3. 复杂度分析（完全缺失）

**修复点：** 丰富种子数据的 `content` 字段，加入代码块和详细解析。

---

## 实施计划

### Phase 1：修复 Tags 解析（前端，低风险）
- `console/src/api/solution.ts`: 3 处 `tags` 映射增加 `JSON.parse()`
- 增加安全解析（try/catch 兜底空数组）

### Phase 2：补齐后端 VO 字段（后端，中风险）
- 扩展 `SolutionVO` 或新建 `SolutionFeedVO`，补齐 `stats/score/flair/badges/topic/userVote` 等字段
- `toVO()` 方法填充真实数据（从 vote 表查 likes/dislikes，从 comment 表查 count）
- feed 接口返回完整数据

### Phase 3：丰富种子数据（数据库，低风险）
- 更新 V9 种子数据的 `content` 字段，加入完整的代码实现和复杂度分析
- 包含 Markdown 代码块（```javascript ... ```）
