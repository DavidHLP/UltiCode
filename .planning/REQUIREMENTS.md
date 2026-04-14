# Requirements: UltiCode Technical Debt Remediation

**Defined:** 2026-04-14
**Core Value:** 平台安全性和功能完整性——用户能安全使用所有已有功能，不存在已知的 CSRF 绕过、JWT 伪造、功能占位符或数据不准确的问题

## v1 Requirements

Requirements for milestone v1.0. Each maps to roadmap phases.

### Security — Filter Chain

- [ ] **SEC-06**: 用户提交的内容通过正确的输出编码（OWASP Encoder）防止 XSS，而非正则输入过滤；移除 XssFilter 的 header 清理以避免数据损坏
- [ ] **SEC-01**: 所有状态变更端点（POST/PUT/PATCH/DELETE）受 Spring Security CSRF 保护，通过 CsrfTokenRepository 桥接现有 Redis-backed CsrfService，移除自定义 CsrfInterceptor
- [ ] **SEC-05**: 应用在 JWT secret 为空或过弱时拒绝启动（@PostConstruct 校验），防止认证完全可绕过
- [ ] **SEC-03**: 移除未被任何代码引用的 UserDetailsServiceImpl 占位符，消除混淆和潜在攻击面

### Security — Sandbox

- [ ] **SEC-04**: Docker 沙箱使用 seccomp profile 限制 syscall、cap-drop ALL 去除所有 Linux capabilities、并实施网络隔离，防止恶意代码逃逸

### Functionality

- [ ] **SEC-02**: 用户可通过邮件链接重置密码，PasswordResetService 调用已有的 EmailServiceImpl 实际发送邮件（非仅打日志）
- [ ] **FUNC-01**: 管理员可对指定提交触发 Rejudge，复用现有 QueueService.enqueueJudgeJob() 判题流程，支持批量操作

### Quality

- [ ] **QUAL-01**: 14 个超过 600 行的 Vue 组件被拆分为更小的组合式组件，保持功能不变
- [ ] **TEST-01**: auth、submission、CodeExecution 三个关键模块具有 Testcontainers 集成测试和单元测试，覆盖率显著提升

## v2 Requirements

Deferred to future milestones (MEDIUM + LOW severity items from original 28).

### Security — Configuration

- **SEC-07**: CORS 允许来源外部化为环境变量
- **SEC-08**: XssFilter 停止清理请求 Header
- **CONF-01**: JWT Cookie Secure 标志在生产环境默认为 true
- **CONF-02**: 创建 application-prod.yml 生产配置（禁用 Swagger、actuator 端点）
- **CONF-03**: docker-compose.yml 移除弱默认密码

### Functionality — Admin

- **AUDIT-01**: BackupController 使用实际认证用户 ID 替代硬编码 "system"
- **FUNC-02**: 实现 5 个 Admin TODO 桩（论坛社区、题目计数、论坛数据、审核详情）
- **FUNC-03**: 实现审核平均解决时间计算（当前硬编码 0.0）

### Performance

- **PERF-01**: 测试用例批量执行替代逐个 Docker 容器启动
- **PERF-02**: Admin Analytics 使用数据库聚合替代全量实体加载

### Quality — Code

- **QUAL-02**: 修复 30+ 处宽泛 catch(Exception e) 为具体异常类型
- **QUAL-03**: 拆分 AdminAnalyticsServiceImpl（553 行）
- **QUAL-04**: 清理生产代码中的 console.log 语句

### Dependencies

- **DEP-01**: 移除 git 跟踪的 management/.env
- **DEP-02**: 替换 SNAPSHOT 依赖为稳定版本
- **DEP-03**: 评估并移除 SockJS 客户端依赖

### Testing

- **TEST-02**: 补充前端 Console 关键路径测试（API 层、stores）
- **TEST-03**: 补充前端 Management 关键路径测试
- **TEST-04**: 添加后端 Controller 集成测试（@WebMvcTest）

## Out of Scope

| Feature | Reason |
|---------|--------|
| 新功能开发 | 本轮只清偿技术债务，新功能留给未来里程碑 |
| UI/UX 重设计 | 除非拆分大组件时必要，否则不在范围 |
| CI/CD 流水线搭建 | 不在本次范围内 |
| 第三方安全审计 | 自查修复，不引入外部审计 |
| 性能优化基准测试 | 只修已识别的具体问题 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEC-06 | — | Pending |
| SEC-01 | — | Pending |
| SEC-05 | — | Pending |
| SEC-03 | — | Pending |
| SEC-04 | — | Pending |
| SEC-02 | — | Pending |
| FUNC-01 | — | Pending |
| QUAL-01 | — | Pending |
| TEST-01 | — | Pending |

**Coverage:**
- v1 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9 (pending roadmap)

---
*Requirements defined: 2026-04-14*
*Last updated: 2026-04-14 after initial definition*
