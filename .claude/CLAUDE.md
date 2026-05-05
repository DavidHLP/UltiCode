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

## 约定
- 使用 TypeScript strict mode
- ESLint + Prettier
- Vitest for testing
