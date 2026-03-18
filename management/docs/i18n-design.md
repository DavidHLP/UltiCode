# i18n 国际化设计文档

## 目录结构

```
src/i18n/
├── index.ts                 # 主入口，创建 i18n 实例
├── types.ts                 # 类型定义和常量
├── utils.ts                 # 工具函数
├── check.ts                 # 翻译完整性检查脚本
└── locales/
    ├── zh-CN/               # 简体中文
    │   ├── index.ts         # 模块聚合
    │   └── modules/
    │       ├── common.ts    # 通用翻译
    │       ├── nav.ts       # 导航翻译
    │       ├── users.ts     # 用户管理
    │       ├── problems.ts  # 题目管理
    │       ├── contests.ts  # 比赛管理
    │       ├── dashboard.ts # 仪表板
    │       ├── auth.ts      # 认证
    │       ├── errors.ts    # 错误消息
    │       ├── moderation.ts# 审核管理
    │       └── settings.ts  # 系统设置
    └── en-US/               # 英文
        ├── index.ts
        └── modules/
            └── ... (同上)
```

## 使用方式

### 1. 基础使用

```vue
<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <h1>{{ t('users.title') }}</h1>
  <p>{{ t('users.searchPlaceholder') }}</p>
</template>
```

### 2. 使用 composable（推荐）

```vue
<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'

const { t, currentLocale, switchLocale, isRtl } = useLocale()

// 切换语言
const toggleLocale = () => {
  switchLocale(currentLocale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
}
</script>
```

### 3. 命名空间翻译

```vue
<script setup lang="ts">
import { useNamespacedTranslations } from '@/composables/useLocale'

// 创建命名空间翻译函数
const { t } = useNamespacedTranslations('users')

// t('title') 等同于 t('users.title')
</script>
```

### 4. 工具函数

```ts
import {
  formatDateByLocale,
  formatNumberByLocale,
  formatRelativeTime,
  hasTranslation,
  tWithFallback
} from '@/i18n/utils'

// 格式化日期
formatDateByLocale(new Date()) // "2026年3月18日" 或 "Mar 18, 2026"

// 格式化相对时间
formatRelativeTime(new Date(Date.now() - 3600000)) // "1小时前" 或 "1 hour ago"

// 带回退的翻译
tWithFallback('some.key', '默认文本')
```

## 命名规范

### 1. 键名命名

- **小驼峰**: `searchPlaceholder`, `deleteConfirm`
- **模块化**: `users.title`, `problems.actions.edit`
- **语义化**: 使用完整词汇而非缩写

### 2. 模块划分

| 模块 | 说明 |
|------|------|
| `common` | 通用操作、状态、标签 |
| `nav` | 导航菜单 |
| `users` | 用户管理 |
| `problems` | 题目管理 |
| `contests` | 比赛管理 |
| `dashboard` | 仪表板 |
| `auth` | 认证相关 |
| `errors` | 错误消息 |
| `moderation` | 内容审核 |
| `settings` | 系统设置 |

### 3. 常见模式

```ts
// CRUD 操作
actions: {
  view: '查看',
  edit: '编辑',
  delete: '删除',
}

// 表单字段
form: {
  title: '标题',
  titlePlaceholder: '请输入标题',
}

// Toast 消息
toast: {
  createSuccess: '创建成功',
  createFailed: '创建失败',
}

// 对话框
dialogs: {
  deleteTitle: '确认删除',
  deleteDescription: '确定要删除吗？',
}

// 列定义
columns: {
  id: 'ID',
  title: '标题',
}
```

## 类型安全

### 1. MessageSchema 类型

```ts
// types.ts
import type zhCN from './locales/zh-CN'
export type MessageSchema = typeof zhCN
```

### 2. SupportedLocale 类型

```ts
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]
```

## 翻译完整性检查

运行以下命令检查翻译完整性：

```bash
cd management
npx tsx src/i18n/check.ts
```

输出示例：
```
=== i18n Translation Completeness Check ===

Total keys in zh-CN: 245
Total keys in en-US: 245

✅ All translations are complete!
```

## 添加新翻译

1. 在 `locales/zh-CN/modules/` 中添加键值对
2. 在 `locales/en-US/modules/` 中添加对应翻译
3. 运行 `pnpm type-check` 验证类型
4. 运行 `npx tsx src/i18n/check.ts` 检查完整性

## 最佳实践

1. **避免硬编码**: 所有用户可见文本都应通过 i18n
2. **保持同步**: 修改翻译时同步更新所有语言
3. **使用命名空间**: 相关翻译组织在同一命名空间下
4. **参数化翻译**: 使用插值而非字符串拼接
   ```ts
   // ✅ 好
   t('users.deleteConfirm', { count: 5 })

   // ❌ 坏
   `确定要删除 ${count} 个用户吗？`
   ```
5. **复数处理**: 使用 vue-i18n 的复数功能
   ```ts
   // zh-CN
   "users.count": "无用户 | 1 个用户 | {count} 个用户"

   // 使用
   t('users.count', count)
   ```
