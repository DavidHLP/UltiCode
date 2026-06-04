---
paths:
  - "**/*.spec.ts"
  - "**/*.test.ts"
description: Vitest 测试约定
---

# Vitest 测试约定

- 使用 `describe` + `it` 组织用例，命名描述行为而非实现
- 优先 stub 网络调用，避免真实 HTTP 请求
- 组件测试用 `@vue/test-utils` 的 `mount`，配合 `jsdom` 环境
- Pinia store 测试通过 `setActivePinia(createPinia())` 隔离实例
- 完整规范待后续补充
