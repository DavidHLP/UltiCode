---
name: ADR-005 Milestone Tracking Issue
about: 10 个 milestone 的接受记录
title: "[M{N}] "
labels: ["milestone", "adr-005"]
---

## Milestone
**{MILESTONE_ID}** ({PARENT_ADR} §{SECTION})

## Status
- [ ] 待开始
- [ ] 实现中
- [ ] 部署到 dev
- [ ] Canary gate 24h 通过 (见 ADR-005 §2.5)
- [ ] Rollback drill 完成 (见 ADR-005 §2.6)
- [ ] 标记 shipped (ADR-005 §2.1 表更新)

## 改动范围
{COPY_FROM_ADR}

## Feature Flag 关联
- flag key: {FLAG_KEY}
- env var: {ENV_VAR}
- 默认值: {DEFAULT}
- cutover 入口: {M2b | M3d | M4b | N/A}

## Acceptance Criteria
1. 代码合并 + `mvn verify` 通过
2. CI 包含 features-on 与 features-off 两套 profile 都通过 (见 ADR-005 §4 #4)
3. 部署到 dev 环境, `pm2 status ulticode-9001` 24h 无 unplanned restart
4. 相关指标 (见 ADR-005 §2.5) 在阈值内
5. Rollback drill 完成, 耗时记入 ADR-005 §2.6
6. shipped 后更新 ADR-005 §2.1 表 `shipped at` 列

## 参考
- {PARENT_ADR}
- ADR-005 (Playbook)
- docs/RUNBOOK.md §10 (Feature Flag 切换手册)
