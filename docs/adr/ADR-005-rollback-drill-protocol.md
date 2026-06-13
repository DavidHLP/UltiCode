# ADR-005 Rollback Drill 协议

> 本协议是 ADR-005 §2.6 与 §4 #2 的执行规范. 目标: 每个 milestone 部署到 dev
> 拓扑后, 跑 1 次回滚演练并把"实际耗时 / 完成时间 / 执行人 / 备注"填入 §2.6 表.
> 本协议**不**要求新建 staging 环境, dev 拓扑 (PM2 + Docker compose) 即可演练.

## 协议 (以 M3a 为例, 其余 milestone 替换 flag 即可)

### 前置

- [ ] 当前 backend 在 dev 拓扑稳定运行 ≥ 24h (Canary gate, 见 ADR-005 §2.5)
- [ ] dev `application-dev.yml` 中目标 flag 当前值为新值 (例如
      `app.features.use-judge-outbox: true`)
- [ ] git 工作区干净, 备份 commit SHA: `git rev-parse HEAD > /tmp/adr005-pre-drill-sha`
- [ ] 备份当前 PM2 backend 进程状态: `pm2 jlist > /tmp/adr005-pre-drill-pm2.json`

### 执行 (计时开始)

1. 编辑 `backend-spring/src/main/resources/application-dev.yml`, 切 flag 到旧值
2. `git add . && git commit -m "drill(adr-005 M3a): flag use-judge-outbox true → false"`
3. `pm2 reload ulticode-9001 --update-env` (记录 T_reload 秒)
4. `pm2 logs ulticode-9001 --nostream --lines 100 | grep -E "Started|ERROR|app.features"`
   确认启动成功且 flag 已切 (记录 T_logs)
5. 业务验证: 提交 1 个测试用例, 走完判题, 确认 verdict 正常落地
   (如果 milestone 关联到 verdict 路径, 例 M3a → 提交后 outbox 不写只观察)
6. (计时结束) 总耗时 T_total = 上述 5 步累计秒数

### 复盘 (回到新值)

7. `git revert --no-edit HEAD` 回到 drill 前状态
8. `pm2 reload ulticode-9001 --update-env`
9. 二次验证: 同步骤 5

### 记录

10. 在 ADR-005 §2.6 表中填入:
    - 实际耗时: T_total (秒)
    - 完成时间: ISO 8601 UTC, e.g. `2026-06-15T03:21:14Z`
    - 执行人: GitHub handle
    - 备注: 任何异常 / 改进 / 相关 issue 链接
11. 把"实际耗时"与"期望耗时"对比, 若超 50%:
    - 在 ADR-005 末尾加 §6 "Drill Outliers" 列表, 简述原因
    - 必要时拆出 issue 跟踪

## 矩阵: 4 个 milestone 的 Rollback 动作映射

| Milestone | flag | 旧值 (drill 前) | 新值 (drill 后回滚) |
|-----------|------|-----------------|---------------------|
| M2a | `app.features.sandbox.executor` (待 ADR-002 校核 flag 名) | `hexagonal` | `legacy` |
| M3a | `app.features.use-judge-outbox` | `true` | `false` |
| M3c | `app.features.judge-queue.use-port` | `true` | `false` |
| M4a | `app.features.use-notification-intent` | `true` | `false` |

> M2b / M3d / M4b cutover 回滚需要 `git revert <配置 commit> + 重新部署`,
> 不走热回滚协议, 详见 ADR-005 §2.7 比赛窗口约束.

## 异常处理

- `pm2 reload` 后 backend 启动失败: 立即 `pm2 logs` 看 ERROR, 必要时
  `pm2 restart ulticode-9001` 重启. 若 5min 内无法恢复, `git revert` 回前
  一 commit + `pm2 reload`. 记入 §2.6 备注"启动超时, 走了 hard revert".
- 业务验证阶段发现 verdict 异常: 立即停止 drill, 走"紧急回滚"流程
  (RUNBOOK §10.3), 记入 §2.6 备注"业务异常, drill 失败".
- drill 真实跑出"实际耗时 > 5min 期望"超 50%: 拆 issue 跟踪, 不阻塞
  milestone shipped (flag 切回默认是 rollback 兜底, drill 是过程验证).
