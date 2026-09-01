# Screenshots

> Console 与 Management 的设计/回归参考截图。视口统一 1496×933 桌面端。
>
> 主题：项目使用 `packages/theme` 维护 light/dark/system 三态。下表「主题」列
> 表示截图实际渲染的主题。

## Console · 用户端 (9002)

| 文件 | 页面 | 主题 | 访问路径 | 用途 |
|------|------|------|----------|------|
| `forum-list-light.png` | 论坛首页 | Light | `/forum` | 浅色主题下的论坛列表 / 板块 / 热门话题 |
| `forum-thread-dark.png` | 论坛首页 | Dark | `/forum` | 深色主题下的论坛列表 |
| `forum-detail-dark.png` | 帖子详情 | Dark | `/forum/detailed/fpost-009` | 帖子正文 / 评论 / Markdown 渲染 |
| `contests-dark.png` | 比赛页 | Dark | `/contest` | 个人 / 团队 / 公开 / 虚拟赛入口 |
| `contest-detail-dark.png` | 比赛详情 | Dark | `/contest/linked-list-special` | 比赛题目 tab（含倒计时、注册状态） |
| `problem-set-dark.png` | 题库专题 | Dark | `/problemset` | 题单聚合 / 推荐专题 |
| `problem-list-dark.png` | 题单详情 | Dark | `/problemset/list/list-essentials` | 单个题单的题目列表结构 |
| `problem-detail-dark.png` | 题目详情 | Dark | `/problems/two-sum` | Markdown 题面 + 示例 + 提交入口 |
| `personal-dashboard-dark.png` | 个人 Dashboard | Dark | `/personal/dashboard` | 解题进度 / Rating / 成就总览 |
| `submissions-list-dark.png` | 提交记录 | Dark | `/personal/submissions` | 个人判题历史 / Verdict 分布 |
| `achievements-dark.png` | 成就页 | Dark | `/personal/achievements` | 徽章墙 / 解题连击 / 排行榜 |

## Management · 管理端 (9003)

| 文件 | 页面 | 主题 | 访问路径 | 用途 |
|------|------|------|----------|------|
| `admin-dashboard-dark.png` | 仪表板 | Dark | `/` | 总览卡片 + 趋势 + 待办 |
| `analytics-dashboard-dark.png` | 数据分析 | Dark | `/analytics` | 多维图表（注册 / 提交 / 判题） |
| `user-management-dark.png` | 用户管理 | Dark | `/users` | 用户列表 / 角色 / 封禁 / 审计 |
| `contest-management-dark.png` | 比赛管理 | Dark | `/contests` | 比赛 CRUD / 状态切换 |
| `moderation-dark.png` | 审核管理 | Dark | `/moderation/dashboard` | 举报队列 / 申诉 / 敏感词 |
| `submissions-audit-dark.png` | 提交审计 | Dark | `/submissions` | 全平台提交流 / Replay / 再判 |

## 重新生成

```bash
# 1. 在已满足 Submission cutover gate 的环境启动完整 dev stack
./scripts/dev/up.sh --mode dev-full --skip-install

# 2. 用 IDE 内置浏览器打开对应 URL
# 3. 在 console 中切换主题 (右上角用户菜单 → 主题 → 浅色/深色)
# 4. 截图覆盖 assets/screenshots/<file>.png
```

> 浏览器内部截图工具：调用 `Page.captureScreenshot`（CDP），
> base64 解码后写回 `assets/screenshots/<file>.png`。

## 命名约定

`<module>[-subview]-<theme>.png`：

- `module`：`forum` / `contest` / `problem` / `admin` / `analytics` / `personal` …
- `subview`（可选）：`list` `thread` `detail` `set` `audit` …
- `theme`：`light` | `dark`

新增截图时保持同款命名，并在本索引中补充页面、主题和用途。
