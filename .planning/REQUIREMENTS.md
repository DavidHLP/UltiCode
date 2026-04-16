# Requirements: UltiCode Technical Debt Remediation II

**Defined:** 2026-04-16
**Core Value:** 平台安全性和功能完整性——用户能安全使用所有已有功能，不存在已知的 CSRF 绕过、JWT 伪造、功能占位符或数据不准确的问题

## v1 Requirements

Requirements for milestone v1.1. Each maps to roadmap phases.

### Security — Configuration

- [ ] **SEC-07**: CORS 允许来源从硬编码改为环境变量配置，支持多源动态加载
- [ ] **SEC-08**: XssFilter 停止清理请求 Header，避免干扰 CSRF token 传递
- [ ] **CONF-01**: JWT Cookie Secure 标志在生产环境默认为 true，非生产环境可配置为 false
- [ ] **CONF-02**: 创建 application-prod.yml 生产配置文件，禁用 Swagger UI 和敏感 actuator 端点
- [ ] **CONF-03**: docker-compose.yml 移除弱默认密码，所有密码通过环境变量注入

### Functionality — Admin

- [ ] **AUDIT-01**: BackupController 使用实际认证用户 ID 替代硬编码 "system"，确保审计追踪准确
- [ ] **FUNC-02**: 实现 5 个 Admin TODO 桩（论坛社区统计、题目计数、论坛数据加载、审核详情查询等），返回实际数据
- [ ] **FUNC-03**: 实现审核平均解决时间计算，替代硬编码 0.0，基于实际审核记录统计

### Performance

- [ ] **PERF-01**: 测试用例批量执行替代逐个 Docker 容器启动，单次容器内执行所有测试用例，减少判题延迟
- [ ] **PERF-02**: Admin Analytics 使用数据库聚合查询（GROUP BY / SUM / COUNT）替代全量实体加载和内存计算

### Quality — Code

- [ ] **QUAL-02**: 修复 30+ 处宽泛 catch(Exception e) 为具体异常类型（IOException, SQLException, BusinessException 等）
- [ ] **QUAL-03**: 拆分 AdminAnalyticsServiceImpl（553 行）为多个职责单一的服务类
- [ ] **QUAL-04**: 清理生产代码中的 console.log/console.warn 语句，保留必要的错误日志

### Dependencies

- [ ] **DEP-01**: 移除 git 跟踪的 management/.env，添加到 .gitignore，提供 .env.example 模板
- [ ] **DEP-02**: 替换 pom.xml 中所有 SNAPSHOT 依赖为稳定发布版本
- [ ] **DEP-03**: 评估 SockJS 客户端依赖使用情况，如不再需要则移除

### Testing

- [ ] **TEST-02**: 补充前端 Console 关键路径测试（API 层 request.ts、auth store、problem store）
- [ ] **TEST-03**: 补充前端 Management 关键路径测试（API 层、admin store）
- [ ] **TEST-04**: 添加后端 Controller 层集成测试（@WebMvcTest），覆盖 AuthController、ProblemController 等关键端点

## Out of Scope

| Feature | Reason |
|---------|--------|
| 新功能开发 | v1.x 系列只清偿技术债务，新功能留给 v2.0 |
| CI/CD 流水线搭建 | 独立里程碑处理 |
| 第三方安全审计 | 自查修复，不引入外部审计 |
| UI/UX 重设计 | 仅在拆分大组件时做结构优化 |
| 推荐系统完善 | 需要独立的功能设计和研究 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| _Pending roadmap creation_ | | |

**Coverage:**
- v1 requirements: 19 total
- Mapped to phases: 0
- Unmapped: 19

---
*Requirements defined: 2026-04-16*
*Last updated: 2026-04-16 after initial definition*
