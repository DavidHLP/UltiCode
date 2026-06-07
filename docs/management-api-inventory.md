# Management 前端 (localhost:9003) — 后端 API 接口完整清单

> 生成时间：2026-06-07 | 总计 164 个唯一端点（去重后） | 覆盖 22 个 API 模块

---

## HTTP 方法分布

| 方法 | 数量 | 占比 |
|------|------|------|
| GET | 68 | 41.5% |
| POST | 57 | 34.8% |
| PATCH | 18 | 11.0% |
| DELETE | 15 | 9.1% |
| PUT | 5 | 3.0% |
| DOWNLOAD | 2 | 1.2% |

---

## 非 Admin 端点（20 个）

这些端点不以 `/admin/` 为前缀，属于公共或审核模块：

### auth（4 个）

- `POST /auth/login` — 登录
- `POST /auth/logout` — 登出
- `GET /auth/me` — 获取当前用户信息+CSRF
- `GET /auth/permissions` — 获取当前用户权限列表

### moderation（16 个）

- `GET /moderation/queue` — 获取审核队列列表
- `GET /moderation/queue/{id}` — 获取队列项详情
- `GET /moderation/queue/entity/{entityType}/{entityId}` — 按实体查找队列项
- `GET /moderation/queue/stats` — 审核统计
- `POST /moderation/queue/{id}/claim` — 认领队列项
- `POST /moderation/queue/{id}/assign` — 分配队列项给审核员
- `PATCH /moderation/queue/{id}/unassign` — 取消分配
- `POST /moderation/queue/{id}/action` — 对队列项执行审核动作
- `POST /moderation/queue/batch-action` — 批量审核动作
- `POST /moderation/reports` — 创建举报
- `GET /moderation/reports/{id}` — 获取举报详情
- `GET /moderation/reports/entity/{entityType}/{entityId}` — 按实体获取举报
- `GET /moderation/appeals` — 获取申诉列表
- `GET /moderation/appeals/my` — 获取我的申诉
- `GET /moderation/appeals/{id}` — 获取申诉详情
- `GET /moderation/appeals/stats` — 申诉统计
- `POST /moderation/appeals` — 创建申诉
- `POST /moderation/appeals/{id}/review` — 审核申诉

---

## Admin 端点（144 个），按模块分组

### account（4 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/account/profile` | 获取管理员个人信息 |
| PATCH | `/admin/account/profile` | 更新管理员个人信息 |
| POST | `/admin/account/change-password` | 修改密码 |
| GET | `/admin/account/subscription` | 获取订阅信息 |

### analytics（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/analytics` | 通用分析查询（泛型） |
| GET | `/admin/analytics/user-activity` | 用户活跃度报告 |
| GET | `/admin/analytics/problem-completion` | 题目完成率报告 |
| GET | `/admin/analytics/contest-participation` | 竞赛参与度报告 |
| GET | `/admin/analytics/revenue` | 收入报告 |
| GET | `/admin/analytics/performance` | 性能报告 |

### audit（2 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/audit/logs` | 审计日志列表 |
| GET | `/admin/audit/stats` | 审计统计 |
| DOWNLOAD | `/admin/audit/export` | 导出审计日志 |

### backup（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/backup` | 备份列表 |
| POST | `/admin/backup` | 创建备份 |
| GET | `/admin/backup/{id}` | 获取备份详情 |
| DELETE | `/admin/backup/{id}` | 删除备份 |
| POST | `/admin/backup/{id}/restore` | 恢复备份 |
| DOWNLOAD | `/admin/backup/{id}/download` | 下载备份文件 |

### comments（5 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/comments/{type}/{id}` | 获取评论详情 |
| PATCH | `/admin/comments/{type}/{id}/flag` | 标记评论 |
| PATCH | `/admin/comments/{type}/{id}/unflag` | 取消标记 |
| DELETE | `/admin/comments/{type}/{id}` | 删除评论 |
| POST | `/admin/comments/bulk` | 批量评论操作 |

### contests（8 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/contest` | 竞赛列表 |
| POST | `/admin/contest` | 创建竞赛 |
| GET | `/admin/contest/{id}` | 竞赛详情 |
| PATCH | `/admin/contest/{id}` | 更新竞赛 |
| DELETE | `/admin/contest/{id}` | 删除竞赛 |
| POST | `/admin/contest/{id}/problems` | 添加竞赛题目 |
| DELETE | `/admin/contest/{id}/problems/{problemId}` | 移除竞赛题目 |
| GET | `/admin/contest/{id}/rankings` | 竞赛排名 |
| POST | `/admin/contest/{id}/start` | 开始竞赛 |
| POST | `/admin/contest/{id}/end` | 结束竞赛 |

### dashboard（2 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/dashboard/stats` | 仪表盘统计数据 |
| GET | `/admin/dashboard/charts` | 仪表盘图表数据 |

### email（8 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/email/send` | 发送邮件 |
| GET | `/admin/email/logs` | 邮件日志列表 |
| GET | `/admin/email/stats` | 邮件统计 |
| GET | `/admin/email/templates` | 模板列表 |
| POST | `/admin/email/templates` | 创建模板 |
| GET | `/admin/email/templates/{id}` | 模板详情 |
| PUT | `/admin/email/templates/{id}` | 更新模板 |
| DELETE | `/admin/email/templates/{id}` | 删除模板 |

### forum（10 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/forum/communities` | 社区列表 |
| GET | `/admin/forum/posts/{id}` | 帖子详情 |
| DELETE | `/admin/forum/posts/{id}` | 删除帖子 |
| POST | `/admin/forum/posts/{id}/pin` | 置顶帖子 |
| POST | `/admin/forum/posts/{id}/unpin` | 取消置顶 |
| POST | `/admin/forum/posts/{id}/lock` | 锁定帖子 |
| POST | `/admin/forum/posts/{id}/unlock` | 解锁帖子 |
| POST | `/admin/forum/posts/{id}/flag` | 标记帖子 |
| POST | `/admin/forum/posts/{id}/unflag` | 取消标记 |
| GET | `/admin/forum/posts/{id}/audit` | 帖子审计日志 |
| POST | `/admin/forum/bulk` | 批量帖子操作 |

### monitoring（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/monitoring/system` | 系统信息 |
| GET | `/admin/monitoring/resources` | 资源使用 |
| GET | `/admin/monitoring/database` | 数据库状态 |
| GET | `/admin/monitoring/queues` | 队列状态 |
| GET | `/admin/monitoring/redis` | Redis 状态 |
| GET | `/admin/monitoring/health` | 系统健康检查 |

### notifications（3 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/notifications` | 创建系统公告 |
| PUT | `/admin/notifications/{id}` | 更新公告 |
| DELETE | `/admin/notifications/{id}` | 删除公告 |

### problem-lists（7 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/problem-lists` | 题单列表 |
| POST | `/admin/problem-lists` | 创建题单 |
| GET | `/admin/problem-lists/{id}` | 题单详情 |
| DELETE | `/admin/problem-lists/{id}` | 删除题单 |
| PATCH | `/admin/problem-lists/{id}/basic-info` | 更新基本信息 |
| PATCH | `/admin/problem-lists/{id}/visibility` | 更新可见性 |
| PATCH | `/admin/problem-lists/{id}/banner` | 更新封面 |
| POST | `/admin/problem-lists/{id}/problems` | 添加题目到题单 |

### problems（20 个 — 最大模块）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/problems` | 题目列表 |
| POST | `/admin/problems` | 创建题目 |
| GET | `/admin/problems/{id}` | 题目详情 |
| PATCH | `/admin/problems/{id}` | 更新题目 |
| DELETE | `/admin/problems/{id}` | 删除题目 |
| POST | `/admin/problems/{id}/publish` | 发布题目 |
| POST | `/admin/problems/{id}/unpublish` | 取消发布 |
| POST | `/admin/problems/{id}/flag` | 标记题目 |
| POST | `/admin/problems/{id}/moderate` | 审核题目 |
| GET | `/admin/problems/{id}/header` | 题目头部信息 |
| GET | `/admin/problems/{id}/description` | 题目描述 |
| GET | `/admin/problems/{id}/code` | 题目代码 |
| GET | `/admin/problems/{id}/cases` | 题目测试用例列表 |
| GET | `/admin/problems/{id}/submissions` | 题目提交列表 |
| POST | `/admin/problems/import` | 批量导入题目 |
| DOWNLOAD | `/admin/problems/export` | 批量导出题目 |
| GET | `/admin/problems/{id}/versions` | 版本列表 |
| GET | `/admin/problems/{id}/versions/{versionId}` | 版本详情 |
| GET | `/admin/problems/{id}/versions/{fromVersionId}/diff/{toVersionId}` | 版本差异对比 |
| POST | `/admin/problems/{id}/versions/create-initial` | 创建初始版本 |
| POST | `/admin/problems/{id}/versions/{versionId}/rollback` | 版本回滚 |

### scoring-rules（5 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/scoring-rules` | 评分规则列表 |
| POST | `/admin/scoring-rules` | 创建评分规则 |
| GET | `/admin/scoring-rules/{id}` | 评分规则详情 |
| PUT | `/admin/scoring-rules/{id}` | 更新评分规则 |
| DELETE | `/admin/scoring-rules/{id}` | 删除评分规则 |

### settings（14 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/settings` | 系统设置 |
| PATCH | `/admin/settings` | 更新系统设置 |
| GET | `/admin/settings/all` | 所有设置 |
| GET | `/admin/settings/email` | 邮件设置 |
| PATCH | `/admin/settings/email` | 更新邮件设置 |
| GET | `/admin/settings/rate-limits` | 速率限制设置 |
| PATCH | `/admin/settings/rate-limits` | 更新速率限制 |
| GET | `/admin/settings/uploads` | 上传设置 |
| PATCH | `/admin/settings/uploads` | 更新上传设置 |
| GET | `/admin/settings/features` | 功能开关 |
| PATCH | `/admin/settings/features` | 更新功能开关 |
| POST | `/admin/settings/cache/clear` | 清除缓存 |
| POST | `/admin/settings/reset` | 重置设置 |
| POST | `/admin/settings/maintenance` | 维护模式 |

### solutions（5 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/solutions` | 解题报告列表 |
| GET | `/admin/solutions/flagged` | 已标记解题列表 |
| GET | `/admin/solutions/{id}` | 解题报告详情 |
| POST | `/admin/solutions/{id}/flag` | 标记解题 |
| POST | `/admin/solutions/{id}/unflag` | 取消标记 |
| DELETE | `/admin/solutions/{id}` | 删除解题 |
| POST | `/admin/solutions/bulk` | 批量解题操作 |

### submissions（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/submissions/statistics` | 提交统计 |
| GET | `/admin/submissions/statuses` | 提交状态选项 |
| GET | `/admin/submissions/languages` | 语言选项 |
| GET | `/admin/submissions/{id}` | 提交详情 |
| POST | `/admin/submissions/{id}/rejudge` | 重新评测 |
| POST | `/admin/submissions/batch-rejudge` | 批量重新评测 |

### tags（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/tags` | 标签列表 |
| POST | `/admin/tags` | 创建标签 |
| GET | `/admin/tags/{id}` | 标签详情 |
| PATCH | `/admin/tags/{id}` | 更新标签 |
| DELETE | `/admin/tags/{id}` | 删除标签 |
| POST | `/admin/tags/merge` | 合并标签 |

### test-cases（9 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/problems/{problemId}/test-cases` | 测试用例列表 |
| POST | `/admin/problems/{problemId}/test-cases` | 创建测试用例 |
| GET | `/admin/problems/{problemId}/test-cases/{testCaseId}` | 测试用例详情 |
| PUT | `/admin/problems/{problemId}/test-cases/{testCaseId}` | 更新测试用例 |
| DELETE | `/admin/problems/{problemId}/test-cases/{testCaseId}` | 删除测试用例 |
| POST | `/admin/problems/{problemId}/test-cases/bulk` | 批量创建测试用例 |
| GET | `/admin/problems/{problemId}/test-cases/export` | 导出测试用例 |
| DOWNLOAD | `/admin/problems/{problemId}/test-cases/export` | 下载测试用例 |
| PUT | `/admin/problems/{problemId}/test-cases/reorder` | 重排测试用例 |

### users（12 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/users` | 用户列表 |
| POST | `/admin/users` | 创建用户 |
| GET | `/admin/users/{id}` | 用户详情 |
| PATCH | `/admin/users/{id}` | 更新用户 |
| DELETE | `/admin/users/{id}` | 删除用户 |
| POST | `/admin/users/{id}/ban` | 封禁用户 |
| POST | `/admin/users/{id}/unban` | 解封用户 |
| POST | `/admin/users/{id}/reset-password` | 重置密码 |
| POST | `/admin/users/{id}/permissions` | 授权 |
| DELETE | `/admin/users/{id}/permissions` | 撤销权限 |
| POST | `/admin/users/bulk-ban` | 批量封禁 |
| POST | `/admin/users/bulk-unban` | 批量解封 |
| DELETE | `/admin/users/bulk-delete` | 批量删除 |

---

## 关键发现

1. **Problems 是最复杂的模块**（20+ 端点），包含版本控制、发布/取消、审核、导入导出等完整生命周期
2. **Moderation 端点不走 `/admin/` 路径**——这是设计上的有意区分，审核系统允许审核员（非管理员）使用
3. **Settings 模块最细致**——按领域拆分了 email/rate-limits/uploads/features/maintenance/cache 等 14 个子端点
4. **所有端点都通过 `@/utils/request.ts` 统一调用**，没有直接 axios 实例
5. **存在 2 个 DOWNLOAD 类端点**（audit 导出 + backup 下载），使用 `apiDownload` 而非 `apiGet`

## 数据来源

- 前端 API 定义文件：`management/src/api/admin/*.ts` + `management/src/api/auth.ts`
- 共 22 个 API 模块文件（不含测试文件）
- 去重规则：模板字符串 `${id}` 归一化为 `{id}`，合并静态/动态路径
