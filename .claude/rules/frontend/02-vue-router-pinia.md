---
paths:
  - "console/src/router/**/*.ts"
  - "management/src/router/**/*.ts"
  - "db-manager/src/router/**/*.ts"
  - "**/stores/**/*.ts"
description: Vue Router + Pinia 规范
---

# 路由与状态管理

- 路由懒加载使用动态 `import()`：`component: () => import('@/views/...')`
- Pinia store 使用 setup 风格（`defineStore('name', () => {...})`）
- 不要在 store 中处理副作用（API 调用放 composables/`@/api/`）
- 客户端状态用 Pinia；服务端状态用 TanStack Query / SWR / VueUse fetch，不要重复
- URL 状态（筛选、排序、分页）写入 search params
- 完整规范待后续补充
