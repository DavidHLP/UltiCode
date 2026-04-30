
## 2026-04-29: 修复 problems.dialog.delete 国际化键路径不匹配

### 问题
- ProblemsListView.vue:368 调用 `t('problems.dialog.delete.title')`（单数 `dialog`）
- 但 `problems.ts` 中的键名为 `dialogs`（复数），导致 intlify 键缺失警告
- 路径期望: `problems.dialog.delete.title`
- 实际路径: `problems.dialogs.delete.title`

### 修改
1. **`management/src/i18n/locales/zh-CN/modules/problems.ts`**:
   - `dialogs:` → `dialog:`（复数改单数）

2. **`management/src/i18n/locales/en-US/modules/problems.ts`**:
   - `dialogs:` → `dialog:`（复数改单数）

### 验证
- 搜索确认：没有任何代码引用 `problems.dialogs.*`，重命名安全
- `pnpm build-only` 无 intlify 国际化键缺失警告
- vite build 成功
- type-check 阶段的 TS2589 错误（`src/i18n/utils.ts:131`）是预先存在的类型递归问题，与本次修改无关

### 关键学习
- i18n 键名必须与代码中 `t()` 调用的路径完全一致（包括单复数）
- 修改前应先全局搜索确认旧键名是否被引用，避免破坏性变更

