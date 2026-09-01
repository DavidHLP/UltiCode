# Management 国际化

Management 与 Console 支持 `zh-CN`、`en-US`。用户可见文本必须使用翻译键；初始化语言、切换语言和 `html[lang]` 更新统一经现有 locale lifecycle，不在页面直接改写 vue-i18n 状态。

## 目录约定

```text
apps/management/src/i18n/
├── index.ts
├── types.ts
├── utils.ts
├── check.ts
└── locales/
    ├── zh-CN/{index.ts,modules/{common,nav,users,problems,contests,dashboard,auth,errors,moderation,settings}.ts}
    └── en-US/{index.ts,modules/...}
```

键使用小驼峰和语义命名空间，例如 `users.title`、`problems.actions.edit`、`users.deleteConfirm`。复数和插值使用 vue-i18n API，不拼接用户可见句子。

## 类型与完整性

```ts
import type zhCN from './locales/zh-CN'
export type MessageSchema = typeof zhCN
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]
```

新增键时同时更新两种 locale，运行：

```bash
pnpm --dir apps/management type-check
pnpm --dir apps/management validate:i18n-keys
```

共享 Garden 设计系统用 `html[lang]` 选择排版和布局 metric profile；颜色与 density 不随语言复制。完整 token 规范见 [Garden 设计系统](design-system.md)。
