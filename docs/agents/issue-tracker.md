# Issue tracker: GitHub

工程技能的事项与规格记录在 DavidHLP/UltiCode 的 GitHub Issues。
使用 gh CLI，并显式指定 --repo DavidHLP/UltiCode。

## 操作约定

- 创建前搜索已有事项，避免重复。
- 读取事项时包含正文、标签和评论。
- 创建或更新多行正文时使用 --body-file。
- 子事项优先使用 GitHub 原生 sub-issue 关系。
- 阻塞关系优先使用原生 issue dependencies；不支持时在正文记录 Blocked by: #<number>。
- 技能要求“发布到事项跟踪器”时，创建 GitHub Issue；要求“读取相关 ticket”时，读取对应 Issue。
- 保留 services/docs/SERVICES_ISSUES.md 的服务问题职责，关联已有记录，不复制维护第二份状态。

## Pull requests as a triage surface

PRs as a request surface: no.
