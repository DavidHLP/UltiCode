---
status: investigating
trigger: GET http://localhost:9001/admin/problems/1 返回 500 Internal Server Error
created: 2026-05-01
updated: 2026-05-01
---

## Current Focus

- **hypothesis**: 后端 `/admin/problems/1` 端点在序列化响应时抛出未知异常
- **test**: 使用 curl 登录后调用 API，检查后端异常日志
- **expecting**: 在后端日志中找到导致 500 的具体异常堆栈
- **next_action**: 需要用户帮助：检查后端日志或提供完整的 traceId

## Symptoms

- **Endpoint**: `GET /admin/problems/1`
- **Error**: 500 Internal Server Error
- **Error response**: `{"code":50000,"message":"Unknown error","traceId":"t-1777615283878"}`
- **Frontend trace**: request.ts:349 → problems.ts:315 → EditDescriptionView.vue:34

## Evidence

- 2026-05-01: 后端服务运行中 (Java PID 322132)
- 2026-05-01: 未认证调用返回 401 (预期)
- 2026-05-01: 登录后调用返回 50000 Unknown error
- 2026-05-01: GlobalExceptionHandler 捕获了未知异常并返回 500
- 2026-05-01: PM2 错误日志中未找到具体异常堆栈

## Investigation Summary

**Code path analyzed:**
1. AdminProblemController.getProblemById(id) → ProblemService.getProblemById(id)
2. ProblemServiceImpl.getProblemById(id) 查找 problem，返回 ProblemVO
3. 问题存在于数据库 (ID=1, title: "两数之和")

**Root cause hypothesis:**
代码路径本身没问题，但响应序列化时发生未知异常。GlobalExceptionHandler 捕获了 Exception 返回 500。

**Most likely causes:**
1. **JSON 序列化问题**: 返回的 ProblemVO 中某个字段无法序列化
2. **循环引用**: Jackson 遇到对象循环引用无法处理
3. **数据类型不匹配**: 数据库中的数据格式与 VO 期望不符

**需要进一步排查：**
- 需要查看后端日志中的具体异常堆栈
- 或者在开发环境添加断点调试

## Eliminated

- ~~后端服务未启动~~ - 已确认运行
- ~~session/cookie 问题~~ - 已登录成功
- ~~权限问题~~ - admin 登录后应该有权限

## Resolution

- **root_cause**: 待定（需要日志定位具体异常）
- **fix**: 待定
- **verification**: 待定
- **files_changed**: 待定