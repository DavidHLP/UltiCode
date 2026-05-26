# Forum 前后端对齐深度分析

生成时间：2026-05-26 23:54:19 CST  
分析对象：`http://localhost:9002/forum`、`console/src/views/forum/*`、`console/src/api/forum.ts`、`backend-spring/src/main/java/com/ulticode/modules/forum/*`  
结论等级：中高风险。页面可以加载，但列表、详情、排序、分页、字段契约存在多处不一致，后续数据量和功能扩展会放大问题。

## 1. 总览

`/forum` 当前前端是 Vue/Vite 控制台应用，端口 `9002` 返回 SPA 壳，由前端路由决定实际页面。接口不走 `/api` 代理，`console/src/utils/request.ts` 默认直连 `http://localhost:9001`，所以真实后端接口是 `http://localhost:9001/forum/*`。

前端页面颗粒度是页面级复用：

- `/forum`、`/forum/popular`、`/forum/explore`、`/forum/all`、`/forum/c/:category` 复用 `ForumFeedView.vue`
- `/forum/detailed/:postId` 使用 `ForumThreadView.vue`
- `/forum/create`、`/forum/edit/:postId` 使用 `ForumEditorView.vue`
- `/personal/forum-posts` 使用个人帖子页

后端接口颗粒度是资源级：

- 帖子：`GET /forum/posts`、`GET /forum/posts/{id}`、`POST /forum/posts`、`PATCH /forum/posts/{id}`、`DELETE /forum/posts/{id}`
- 详情线程：`GET /forum/posts/{id}/thread`
- 评论：`POST /forum/posts/{id}/comments`、`PATCH /forum/comments/{id}`、`DELETE /forum/comments/{id}`
- 社区：`GET /forum/communities`、`GET /forum/communities/{slugOrId}`、`GET /forum/communities/{slug}/posts`
- 行为：`POST /forum/posts/{id}/view`、`POST /forum/posts/{id}/share`
- 元数据：`GET /forum/tags`、`GET /forum/quick-filters`

整体方向是合理的，但“页面状态”与“资源查询参数”没有完全对齐，导致前端在本地补排序/筛选，后端参数没有被真正使用，DTO 字段形态在不同接口间也不稳定。

## 2. 关键证据

### 2.1 `/forum` 是 SPA 页面，不是接口

访问 `http://localhost:9002/forum` 返回 Vite HTML 壳：

```http
HTTP/1.1 200 OK
Content-Type: text/html
<div id="app"></div>
<script type="module" src="/src/main.ts?..."></script>
```

访问 `http://localhost:9002/api/forum/posts` 也返回同一个 HTML 壳，说明 Vite 配置里没有 `/api` proxy。真实接口是 `9001/forum/*`。

相关文件：

- `console/vite.config.ts`
- `console/src/utils/request.ts`

### 2.2 `GET /forum/posts` 实际响应

实测 `GET http://localhost:9001/forum/posts` 返回：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "post-segtree-visual",
        "communityId": "community-technology",
        "userId": "user-tourist",
        "authorUsername": "tourist",
        "authorAvatar": "...",
        "title": "线段树可视化指南（懒标记传播）",
        "flairType": "showcase",
        "tags": [],
        "media": "[{\"src\":\"...\",\"kind\":\"image\",\"type\":\"image\"}]",
        "stats": "{\"saves\":0,\"views\":0,\"awards\":0,\"shares\":0,\"comments\":5}",
        "views": 0,
        "voteState": "neutral"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 3,
    "totalPages": 1
  }
}
```

重点问题：

- `media` 是字符串，不是前端期望的数组/对象。
- `stats` 是字符串，不是前端期望的对象。
- 没有 `community` 对象，也没有 `communityName/communitySlug`。
- 没有 `flair` 对象，只有 `flairType`。
- `tags` 对样例第一条返回空数组，但详情接口里有真实 tags。

### 2.3 `GET /forum/posts/{id}/thread` 实际响应

实测 `GET http://localhost:9001/forum/posts/post-segtree-visual/thread` 返回的 `post.media` 和 `post.stats` 是正常结构：

```json
{
  "post": {
    "id": "post-segtree-visual",
    "media": [
      {
        "src": "https://images.unsplash.com/...",
        "kind": "image",
        "type": "image",
        "ratio": 1.777777777777778
      }
    ],
    "stats": {
      "saves": 0,
      "views": 0,
      "awards": 0,
      "shares": 0,
      "comments": 5,
      "likes": 0,
      "dislikes": 0
    },
    "tags": ["tutorial", "segment-tree", "visualization"]
  },
  "comments": [...]
}
```

这说明同一个 `ForumPostVO` 在列表和详情接口返回形态不同。根因大概率是 MyBatis-Plus `selectById` 能走 `JacksonTypeHandler`，而自定义 `@Select` 查询没有稳定应用 JSON type handler。

相关文件：

- `backend-spring/src/main/java/com/ulticode/modules/forum/entity/ForumPost.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/mapper/ForumPostMapper.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumPostServiceImpl.java`

## 3. 前端页面逻辑分析

### 3.1 路由颗粒度

`console/src/router/index.ts` 中，论坛路由结构清晰：

- `forum-home`：`/forum`
- `forum-popular`：`/forum/popular`，传 `filter: "hot"`
- `forum-explore`：`/forum/explore`，传 `filter: "explore"`
- `forum-all`：`/forum/all`，传 `filter: "new"`
- `forum-category`：`/forum/c/:category`
- `forum-thread`：`/forum/detailed/:postId`
- `forum-guidelines`、`forum-feedback`

问题：`forum-explore` 的 `filter: "explore"` 没有对应 sorter，也没有对应后端查询逻辑，最终会 fallback 到 hot 排序。

### 3.2 Feed 页面数据加载

`ForumFeedView.vue` 在全站 Feed 上并行加载：

- `fetchForumPosts()`
- `fetchForumCommunities()`
- `fetchForumQuickFilters()`

社区页并行加载：

- `fetchCommunityPosts(slug, { sortBy })`
- `fetchForumCommunity(slug)`

前端本地做：

- 搜索：title/excerpt/tags 本地过滤
- flair：本地过滤
- 排序：hot/new/top/rising 本地排序
- pinned：本地置顶提升

问题：

- 后端已经有分页，但前端丢弃 `page/total/totalPages`。
- 搜索只覆盖当前第一页，不是全量搜索。
- 本地排序只覆盖当前页，不是全局排序。
- 社区接口传了 `sortBy`，但后端没有实际按 `sortBy` 排序。

### 3.3 帖子卡片字段依赖

`ForumPostCard.vue` 依赖：

- `post.stats.comments/likes/dislikes/saves/shares`
- `post.media` 为对象或数组
- `post.community` 展示 `r/{community.name}` 和社区头像
- `post.flair` 展示 badge

但列表接口实际返回：

- `stats` 为 JSON 字符串
- `media` 为 JSON 字符串
- 只有 `communityId`
- 只有 `flairType`

结果：

- `localStats.value` 可能变成字符串展开后的字符索引对象，统计展示异常。
- 媒体在列表页很可能无法显示。
- 社区名无法展示，退化为用户维度展示。
- flair badge 不显示。
- hot/top 排序依据不可靠。

### 3.4 详情页逻辑

`ForumThreadView.vue` 加载 `fetchForumThread(postId)`，详情接口返回结构较完整，所以详情页比列表页更容易正确渲染。

问题：

- 详情页评论标题使用 `thread.comments.length`，只计算顶层评论。样例中顶层评论 2 条，总评论数 `stats.comments` 是 5。
- 评论投票更新逻辑只在 `thread.value.comments.find(...)` 查找顶层评论，嵌套回复投票后不会更新本地节点。
- `recordForumView` 只在用户已登录时调用，但后端标注 view endpoint 是 public。

## 4. 后端接口逻辑分析

### 4.1 Controller 层

`ForumController` 的接口设计总体资源化：

- 公共读接口不要求登录，但会尝试读取当前用户，用于 `isSaved/isMember/voteState`
- 写接口要求登录，并带 rate limit
- 评论、社区、标签、quick filters 边界清晰

主要问题不在 Controller 路由，而在 Service/Mapper 实现和 DTO 契约。

### 4.2 Service 层

`ForumPostServiceImpl.findAllPosts`：

- 固定按 `findRecentPosts(limit, offset)` 查最新
- 最大 pageSize 限制 50
- 转换为 `ForumPostVO`

`ForumServiceImpl.findPostsByCommunity`：

- 接收 `sortBy`
- 但实际调用 `forumPostService.findByCommunityId(c.getId(), limit, offset)`
- Mapper SQL 固定 `ORDER BY created_at DESC`

这导致 `sortBy=hot/top/new` 只有参数，没有行为。

### 4.3 JSON 字段处理

`ForumPost` 使用：

```java
@TableName(value = "forum_posts", autoResultMap = true)
@TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
private Object tags;
@TableField(value = "media", typeHandler = JacksonTypeHandler.class)
private Object media;
@TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
private Object stats;
```

但 `ForumPostMapper` 里大量 `@Select("SELECT * FROM forum_posts ...")` 自定义查询可能没有稳定应用这些 type handler。实测结果已经证明列表与详情的 JSON 字段形态不同。

### 4.4 权限与管理动作

`updatePost` 允许作者更新：

- `isPinned`
- `isLocked`

这两个字段通常是管理/版主权限，不应在普通帖子编辑接口中由作者直接控制。当前只校验 `post.getUserId().equals(userId)`，没有管理员或社区版主权限检查。

## 5. 对齐问题清单

| 优先级 | 问题 | 影响 |
|---|---|---|
| P0 | 列表接口 `media/stats/tags` 与详情接口形态不一致 | 列表页媒体、统计、排序、标签展示不可靠 |
| P0 | 前端期望 `community/flair` 对象，后端列表只返回 `communityId/flairType` | 社区与 flair UI 缺失 |
| P1 | 后端分页存在，前端丢弃分页元数据 | 当前页被误当全量，搜索/排序不准确 |
| P1 | `sortBy` 参数未真正实现 | `/popular`、`/all`、社区排序语义不真实 |
| P1 | `/forum/explore` 没有后端或前端明确语义 | 路由存在但行为 fallback |
| P1 | 评论数量顶层/总数不一致 | 详情页评论计数误导 |
| P1 | 嵌套回复投票后本地状态不更新 | 交互反馈不一致 |
| P2 | `recordShare` 更新 impressions，不更新 shares | 分享统计与 UI 文案不一致 |
| P2 | `recordView` 前端仅登录触发，后端是 public | 访问统计偏低 |
| P2 | 作者可修改 `isPinned/isLocked` | 权限边界风险 |
| P2 | 社区 detail 返回空 rules/links | Sidebar 设计有位但数据缺失 |
| P3 | `CommentThreadView.vue` 直接 fetch `/api/forum/...`，与 axios 基线不一致 | 组件若被使用会请求错误路径 |

## 6. 推荐目标契约

### 6.1 统一帖子响应 DTO

建议所有用户侧帖子接口都返回一致结构：

```ts
interface ForumPostDTO {
  id: string;
  title: string;
  excerpt?: string;
  createdAt: string;
  author: {
    id: string;
    username: string;
    avatar?: string;
    karma?: number;
  };
  community: {
    id: string;
    name: string;
    slug: string;
    icon?: string;
    color?: string;
  };
  flair?: {
    type: "discussion" | "question" | "announcement" | "showcase" | "hiring";
    text?: string;
  };
  tags: string[];
  media: ForumPostMedia[];
  stats: {
    views: number;
    likes: number;
    dislikes: number;
    comments: number;
    score: number;
    saves: number;
    shares: number;
    awards?: number;
  };
  userVote: -1 | 0 | 1;
  voteState: "upvoted" | "downvoted" | "neutral";
  isSaved: boolean;
  isPinned: boolean;
  isLocked: boolean;
  isMember?: boolean;
  isAuthor?: boolean;
}
```

如果后端短期不想改 DTO，可以在前端 `console/src/api/forum.ts` 增加 `normalizeForumPost(raw)`，统一解析：

- JSON string -> object/array
- `flairType/flairLabel` -> `flair`
- `communityId/communityName/communitySlug` -> `community`
- `authorUsername/authorAvatar/userId` -> `author`
- 补默认 `stats`

但这只是前端兼容层，长期应以后端响应契约稳定为准。

### 6.2 统一列表查询参数

建议将 Feed 查询收敛到：

```http
GET /forum/posts?page=1&pageSize=20&sortBy=hot&q=&community=technology&flair=question
```

其中：

- `sortBy=new`：`created_at DESC`
- `sortBy=hot`：可先用 `score/comments/views/shares` 简化公式
- `sortBy=top`：按 score 或 likes
- `sortBy=rising`：短时间窗口热度
- `q`：服务端搜索 title/excerpt/tags
- `community`：slug
- `flair`：flair type

这样前端 `/forum`、`/forum/popular`、`/forum/all`、`/forum/c/:category` 都只是参数映射，不需要本地伪全量排序。

### 6.3 明确 `/forum/explore`

二选一：

1. 删除或隐藏 `/forum/explore`，直到有真实推荐/探索逻辑。
2. 定义 `sortBy=explore`，后端使用推荐、随机、未读、新社区等策略。

当前存在路由但没有行为，容易误导。

## 7. 建议修复顺序

### Phase 1：契约止血

1. 修复后端列表接口 JSON 字段反序列化，保证 `tags/media/stats` 与详情一致。
2. 在 `ForumPostVO` 中补齐或组装 `community`/`flair`，或前端统一 normalizer。
3. 前端 `fetchForumPosts/fetchCommunityPosts/fetchMyForumPosts/fetchForumThread/fetchForumPost` 全部走同一个 normalizer。
4. 增加接口契约测试：列表和详情同一帖子字段类型必须一致。

### Phase 2：列表查询对齐

1. 后端实现 `sortBy`，至少支持 `new/hot/top`。
2. 前端保留当前 filter 状态，但将排序参数传给后端。
3. 前端消费 `PageResult`，实现分页或加载更多。
4. 搜索和 flair 过滤迁移到服务端，避免只过滤当前页。

### Phase 3：行为与权限收口

1. `recordShare` 改为更新 `stats.shares` 或独立 shares 字段，不应更新 impressions。
2. `recordView` 前端按 public 行为调用，或后端明确要求登录。
3. 普通 `PATCH /forum/posts/{id}` 移除 `isPinned/isLocked`；新增 admin/moderator 接口处理置顶锁帖。
4. 嵌套评论投票更新逻辑改为递归更新，或投票后局部刷新评论树。

## 8. 验收标准

### 接口契约

- `GET /forum/posts` 和 `GET /forum/posts/{id}/thread` 对同一帖子返回相同字段类型。
- `media` 永远是数组或 `null`，不是 JSON 字符串。
- `stats` 永远是对象，至少包含 `comments/likes/dislikes/saves/shares/views`。
- `tags` 永远是字符串数组。
- `flair` 和 `community` 前后端约定一致。

### 页面行为

- `/forum` 展示帖子列表，社区名、flair、图片、统计全部可见。
- `/forum/popular` 与 `/forum/all` 排序结果不同且可解释。
- `/forum/c/technology?sortBy=top` 或对应 UI 排序能由服务端返回稳定顺序。
- 搜索结果不局限于第一页。
- 评论标题数量与总评论数一致。
- 嵌套回复投票后 UI 即时更新。

### 安全/权限

- 普通作者不能通过编辑接口置顶或锁帖。
- 未登录用户不能创建/编辑/删除帖子评论。
- public view/share 行为与后端安全策略一致。

## 9. 参考文件

- `console/src/router/index.ts`
- `console/src/api/forum.ts`
- `console/src/views/forum/ForumFeedView.vue`
- `console/src/views/forum/ForumThreadView.vue`
- `console/src/views/forum/components/ForumPostCard.vue`
- `console/src/views/forum/components/ThreadContent.vue`
- `console/src/components/comments/comment-tree-builder.ts`
- `backend-spring/src/main/java/com/ulticode/modules/forum/controller/ForumController.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/ForumService.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumServiceImpl.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumPostServiceImpl.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/mapper/ForumPostMapper.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/entity/ForumPost.java`
- `db-manager/migrations/V4__forum_schema.sql`
