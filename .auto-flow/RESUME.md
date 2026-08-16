# Resume

- Active task: SPLIT-004 (in_progress) — slice-5/6/7 + slice-8（read-routing 能力建设：批量 problem facts seam + 用户读 provider list/detail/best + App controller 经 port 双 adapter + Admin read-group 参数化）已完成并通过 Review/Validation；SPLIT-003 实际切流 gate 依赖本任务读路径迁移（routing.mode=remote + admin read-group=backend-submission + cutover 联动）
- Branch: main (工作区 7 个已修改 + 13 个未跟踪路径，均属 SPLIT-003 切片，未 commit)
- Objective: 将 submission 判题生命周期与 search 索引 worker 拆分为独立的微服务。
- Dependencies: SPLIT-001 → SPLIT-002(done) → SPLIT-003(in_progress) → SPLIT-004 → SPLIT-005; SPLIT-001 → SEARCH-001 → SEARCH-002 → SEARCH-003 → SPLIT-005
- Completed slices:
  - SPLIT-001 contracts/decision boundary (done)
  - SPLIT-002 Submission/Judge runtime seam (done)
  - SPLIT-003 expand-slice 1 (done, Task 未关): init-db/migrations/submission/ 目标态三表 + flyway-submission.conf + submission_rw ACCOUNT LOCK grant + migrate.sh SUBMISSION_DB_NAME 校验 + PerOwnerSchemaGrantTest manifest 同步 + 迁移指南 §5 owner 更新
- Not complete: SPLIT-003 实际切流（启用 APP_SUBMISSION_ROUTING_MODE=remote + owner.mode=local + admin read-group=backend-submission 需 cutover 后数据可读）、grant revocation 执行（唯一 writer 未满足）；SPLIT-004 剩余：实际 read routing 切换（routing flag 翻转 + Admin group 切换 + AC4 退役证据）；SEARCH-001..003、SPLIT-005 gate。不得宣称 SPLIT-003/SPLIT-004 完成。
- Protected boundaries: 不编辑已应用 migration；`submission_test_details` 是 grant 影子名，无独立表；Contest association 不得搬进 submission 库；`.auto-flow/` 不 commit。
- Evidence: PerOwnerSchemaGrantTest+IT 25/0; 真实 MySQL: 默认链 80+3 migrations OK, MIGRATION_SCHEMA=submission 2 OK; slice-2: Focused 9/9; slice-3: Focused 13/13; slice-4: Focused 16/16 + 真实 MySQL cutover 脚本全链路 PASS; slice-5: Focused 20/20; slice-6: Focused 22/22 + App boot 5/5; slice-7: Focused 26/26 + App boot 5/5; slice-8: Focused 30/30（user query IT 8/8 含 findById 权限/list 分页/批量 enrichment/findBest）+ App boot 5/5 + 模块 5/5 + PerOwnerSchemaGrantTest 6/6 + reactor compile + compose dev/prod + bash -n + git diff --check; Review Confirmed=0。
- Rollback: 删除新增迁移/conf 文件即可；schema 目标态未接入 writer，现有 local 路由不受影响。
