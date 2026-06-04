---
paths:
  - "console/src/**/*.{vue,ts}"
  - "management/src/**/*.{vue,ts}"
  - "db-manager/src/**/*.{vue,ts}"
description: Vue 3 + TypeScript 通用规范
---

# Vue 3 + TypeScript 通用规范

- 组件使用 `<script setup lang="ts">` 语法糖
- 优先使用 Composition API，禁止使用 Options API
- 避免 `any`，必须显式声明类型；必要时使用 `unknown` + 类型守卫
- 基本类型（string/number/boolean）状态优先用 `ref`，复杂对象才用 `reactive`
- 组件 props 使用 `defineProps<{...}>()` 显式声明类型
- 组件 emits 使用 `defineEmits<{...}>()` 显式声明
- 完整规范待后续补充
