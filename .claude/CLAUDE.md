# UltiCode Project

## 技术栈

- **语言**: JavaScript/TypeScript
- **包管理**: pnpm
- **前端**: Vue 3 + Vite
- **后端**: Spring Boot (Java)

## 项目结构

- `management/` - 管理后台前端
- `console/` - 控制台前端
- `backend-spring/` - Spring Boot 后端
- `db-manager/` - 数据库管理
- `shared/` - 共享类型定义
- `docker/` - Docker 配置

## 构建命令

```bash
# 安装依赖
pnpm install

# 开发
pnpm dev

# 构建
pnpm build

# 测试 (management)
cd management && cat vitest.config.ts
```

## Hot Paths (自动加载)

- `.planning/phases/47-frontend-i18n/` - 前端国际化
- `.planning/STATE.md` - 项目状态
- `management/src/components/` - Vue 组件
- ESLint 9.x + `eslint-plugin-vue` ^9.30.0 (10.x 会破坏)

### CSS 规范
- 使用 OKLCH 颜色 (`oklch()`)
- 主题通过 `.dark` class 切换
- 禁用 hex/HSL

### 后端反模式
- ❌ 禁止 `new` 注入 - 使用构造器注入
- ❌ 禁止捕获通用 `Exception` - 使用 `BusinessException`
- ❌ 禁止返回 `null` 集合 - 返回空 list/set
- ❌ 读方法必须加 `@Transactional(readOnly = true)`
- ❌ 禁止 `System.out.println` - 使用 SLF4J

## 热路径

| 需求 | 位置 |
|------|------|
| 后端 Result 封装 | `backend-spring/src/main/java/com/ulticode/common/response/Result.java` |
| 后端全局异常 | `backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java` |
| Console API | `console/src/api/*.ts` |
| Console 认证 | `console/src/stores/auth.ts` + `console/src/utils/csrf.ts` |
| Management API | `management/src/api/admin/*.ts` |
| 问题模块 | `backend-spring/src/main/java/com/ulticode/modules/problem/` |
| 共享类型 | `shared/auth-core/src/` |

## AGENTS.md 索引

各子项目有独立的 AGENTS.md，包含更详细的架构和约定：
- `backend-spring/AGENTS.md` - Spring Boot 约定、DTO 命名、测试规范
- `console/AGENTS.md` - Console 前端结构、Auth flow、API 响应解包
- `management/AGENTS.md` - Management 前端结构、组件规范、API 模式
