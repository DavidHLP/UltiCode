# Screenshots

> Console 与 Management UI 2.0 的本地运行截图。视口统一 1496×933 桌面端。
>
> 主题：项目使用 `packages/theme` 维护 light/dark/system 三态。下表「主题」列
> 表示截图实际渲染的主题。
>
> 本组截图沿用本地外观配置：Orange / Stone、Serif 字体、None 圆角、Compact 密度、
> Solid 菜单和 Standard 图表配色。页面内容来自现有本地数据；空状态保留实际结果。

## Console · 用户端 (9002)

| 文件 | 页面 | 主题 | 访问路径 | 用途 |
|------|------|------|----------|------|
| `forum-list-light.png` | 论坛首页 | Light | `/forum` | 浅色主题下的论坛列表 / 板块 / 热门话题 |
| `forum-thread-dark.png` | 论坛首页 | Dark | `/forum` | 深色主题下的论坛列表 |
| `forum-detail-dark.png` | 帖子详情 | Dark | `/forum/detailed/fpost-009` | 帖子正文 / 评论 / Markdown 渲染 |
| `contests-dark.png` | 比赛页 | Dark | `/contest` | 进行中 / 即将开始 / 历史比赛、排行榜与虚拟赛入口 |
| `contest-detail-dark.png` | 比赛详情 | Dark | `/contest/linked-list-special` | 已结束比赛概览、规则与虚拟赛入口 |
| `problem-set-dark.png` | 题库专题 | Dark | `/problemset` | 题单聚合 / 推荐专题 |
| `problem-list-dark.png` | 题单详情 | Dark | `/problemset/list/list-essentials` | 单个题单的题目列表结构 |
| `problem-detail-dark.png` | 题目详情 | Dark | `/problems/two-sum/description` | Markdown 题面、代码编辑器、测试用例与提交入口 |
| `personal-dashboard-dark.png` | 个人 Dashboard | Dark | `/personal/dashboard` | 解题进度、活跃热力图与技能雷达 |
| `submissions-list-dark.png` | 提交记录 | Dark | `/personal/submissions` | 个人判题历史 / Verdict 分布 |
| `achievements-dark.png` | 成就页 | Dark | `/personal/achievements` | 成就概览、分类与当前账号的空状态 |

## Management · 管理端 (9003)

| 文件 | 页面 | 主题 | 访问路径 | 用途 |
|------|------|------|----------|------|
| `admin-dashboard-dark.png` | 仪表板 | Dark | `/` | 总览卡片 + 趋势 + 待办 |
| `analytics-dashboard-dark.png` | 数据分析 | Dark | `/analytics` | 多维图表（注册 / 提交 / 判题） |
| `user-management-dark.png` | 用户管理 | Dark | `/users` | 用户列表 / 角色 / 封禁 / 审计 |
| `contest-management-dark.png` | 比赛管理 | Dark | `/contests` | 比赛 CRUD / 状态切换 |
| `moderation-dark.png` | 审核统计 | Dark | `/moderation/dashboard` | 举报分类、处理状态与申诉入口 |
| `submissions-audit-dark.png` | 提交审计 | Dark | `/submissions` | 全平台提交列表、状态、详情与再判入口 |

## 重新生成

```bash
# 1. 在已满足 Submission cutover gate 的环境启动完整 dev stack
./scripts/dev/up.sh --mode dev-full --skip-install

# 2. 如服务已运行，直接复用；用内置浏览器和现有会话打开对应 URL
# 3. 用户菜单 → Appearance → Color mode，按表格选择 Light / Dark 并 Apply
# 4. 等待页面数据、字体与图表加载完成，以原始 PNG 覆盖 assets/screenshots/<file>.png
# 5. 完成后恢复原来的外观偏好与浏览器视口
```

> 浏览器内部截图工具：视口设为 1496×933，调用 `Page.captureScreenshot`（CDP），
> 使用 PNG、`captureBeyondViewport: true` 与 `clip: { x: 0, y: 0, width: 1496, height: 933, scale: 1 }`，
> base64 解码后直接写回 `assets/screenshots/<file>.png`；不要保存工具自动缩放的预览图。
> 核对 PNG 可解码、像素尺寸、页面、主题以及是否仍有加载遮罩，再检查索引与 README 图片引用。

## 命名约定

`<module>[-subview]-<theme>.png`：

- `module`：`forum` / `contest` / `problem` / `admin` / `analytics` / `personal` …
- `subview`（可选）：`list` `thread` `detail` `set` `audit` …
- `theme`：`light` | `dark`

新增截图时保持同款命名，并在本索引中补充页面、主题和用途。
