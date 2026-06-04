---
paths:
  - "backend-spring/**/*.java"
  - "backend-spring/**/*.yml"
  - "backend-spring/**/*.yaml"
description: Spring Boot 后端项目特定补充规范
---

# Spring Boot 后端补充

- 控制器禁止持有 Service 之外的业务逻辑；参数校验与权限检查留在 Web 层
- DTO/VO 字段必须有 Javadoc 注释（继承自父类时先 `super.toString()`）
- 事务边界必须在 Service 层显式声明；`@Transactional` 不允许出现在 Controller 或 Mapper
- 错误响应统一走 `common/exception/` 下的全局异常处理器
- API 返回结构使用统一信封（参考 `common/response/ApiResponse`）
- 完整规范见 `backend/01-java-programming.md` 至 `backend/07-java-design.md`
