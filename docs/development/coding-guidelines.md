# 编码指南与规则入口

仓库级规则唯一入口是 [`AGENTS.md`](../../AGENTS.md)；后端、前端、包和数据库的嵌套规则位于最近的 `AGENTS.md` 与 `.omp/rules/` / `.claude/rules/`。本文只提供查找地图，不复制规则正文。

## 必须保持的结构

- 后端保持 `controller → service/projection/port → mapper → entity`，不为局部变化引入平行架构。
- 跨 Owner 只通过 typed DTO、provider-owned contract 或 consumer-owned port；保留 `Result` / `RpcResult` envelope 和字段映射。
- 信任边界验证输入，数据库访问使用参数化 MyBatis/SQL；异常显式处理，不能吞错或伪造成功。
- 不提交 secret；access/refresh 仍是 HttpOnly cookie，refresh 仅接受 hash-only DB-backed rotation。
- 共享前端行为放在聚焦 package；Markdown/KaTeX 必须经 `packages/markdown-utils` 清洗。
- 迁移只新增时间戳更大的 Flyway 文件，不编辑已应用 migration。

## 命名与提交

环境变量使用 `SCREAMING_SNAKE_CASE`；服务模块使用 `backend-*` 语义；端口接口使用 `*Port`，实现使用 `Default*`；DTO 按 `*Query`、`*Request`、`*VO` 区分。提交使用 `<type>: <description>`，例如 `docs: update operations guide`。

## 变更前后

1. 阅读根规则、最近嵌套规则、实现、配置和测试。
2. 修改 exported symbol 前检查所有引用；跨栈 contract 同步后端、共享类型和两个前端。
3. 为新行为和重要失败路径补测试；按 [测试与质量](testing.md) 选择最小完整门禁。
4. 检查 diff、敏感信息、无关文件、错误路径、资源释放、兼容性和文档漂移。

如果本页与规则文件冲突，以 `AGENTS.md`、最近嵌套规则和实现为准。
