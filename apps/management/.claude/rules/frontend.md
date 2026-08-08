# ulticode-frontend — frontend rules 入口

本文件是 frontend agent 的 rules 软链入口,内容仅为路径引用,避免与
`/home/davidhlp/project/UltiCode/.claude/rules/` 下的源文件双源。

## 路径引用(按需 Read)

- `/home/davidhlp/project/UltiCode/.claude/rules/frontend/01-vue3-typescript.md` — `<script setup lang="ts">` + Composition API + `defineProps/Emits`
- `/home/davidhlp/project/UltiCode/.claude/rules/frontend/02-vue-router-pinia.md` — router + Pinia setup store
- `/home/davidhlp/project/UltiCode/.claude/rules/frontend/03-vitest-testing.md` — Vitest 规范
- `/home/davidhlp/project/UltiCode/.claude/rules/frontend-rules.md` — console + management 跨端总则
- `/home/davidhlp/project/UltiCode/management/AGENTS.md` — management 端约定(已读)

## 关键发现 4 条(2026-06-14 16:53 frontend 拉取)

1. **`vue/multi-word-component-names: OFF`**:单字组件名允许;`HiddenTestCasesEditor` / `HiddenCasesView` / `TestCaseForm` 命名合规
2. **Composition API 优先,不用 Options API**:Phase B 重构 `TestCaseForm.vue` 用的就是 `<script setup lang="ts">` + Composition API(`computed` for caseScope),与规则一致
3. **i18n 走 `vue-i18n` 9+ Composition API 风格**:`const { t } = useI18n()` 而非 `$t`,与规则一致
4. **管理端 `pnpm test` 脚本** = `vitest --run --passWithNoTests --exclude '**/packages/theme/**'`:40 files / 269 tests 都按这条跑

## 协调备忘

- reviewer 16:48 已确认 `.codex/` 是 Codex 子项目,Slock agent 不跑 Codex 工具,**不拉**
- backend / database rules 与我辖区不重叠,**不拉**
- 项目根 CLAUDE.md / AGENTS.md 在我 MEMORY.md Active Context 段已含
