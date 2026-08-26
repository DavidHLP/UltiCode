# G-5 Decisions

## Centralize the console sider visual contract

- Context: The console sider mixed local shadcn utility styles, shared row styles, list-specific widths, colored category icons, and a separate user/dropdown treatment.
- Decision: Extend the existing `packages/sidebar-menu` CSS seam with Garden shell, row, group, list, user, and dropdown classes; make console `features/sider` consume those classes and remove local width/color overrides.
- Alternatives: A page-local stylesheet or a second sidebar component was rejected because both would preserve the existing style fork.
- Consequences: Console sidebar contexts share geometry and state treatment; the management app is not visually changed because it does not consume the new console shell classes.

## Locale-aware Garden profile seam

- Context: Console and Management both support `zh-CN` and `en-US`, but initial locale detection is duplicated, the landing switcher directly mutates vue-i18n, and the global design system has no locale-specific metric layer. Browser evidence showed English landing copy with `html lang="zh-CN"`.
- Decision: Use the existing semantic `html lang` attribute as the single profile selector. Add explicit zh/en typography and layout metric profiles to shared theme/design-system tokens; keep Garden colors and app density shared. Unify initial locale resolution and make every switch path pass through the existing locale lifecycle; seed the same marker in the pre-bundle bootstrap.
- Alternatives: Duplicate zh/en page stylesheets (rejected: creates a second design system); put theme imports inside locale-preference (rejected: wrong dependency direction); rewrite all translation strings to equal character counts (rejected: copy quality and i18n semantics are not layout contracts).
- Consequences: One locale change updates content and CSS custom properties through `html[lang]`; components remain locale-agnostic. CJK gets its own font/leading/space metrics while English keeps editorial Latin metrics. Density remains an independent app policy.
- Affected tasks: I18N-DESIGN-001, I18N-DESIGN-002, I18N-DESIGN-003, I18N-DESIGN-004.
