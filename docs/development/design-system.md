# Garden 设计系统

Garden 是 Console、Management 和共享包共同使用的视觉契约。完整规范唯一入口是 [`packages/design-system/docs/GARDEN_DESIGN_SPEC.md`](../../packages/design-system/docs/GARDEN_DESIGN_SPEC.md)；本文只说明查找入口与验证命令。

- 颜色 token、明暗主题和布局/控件几何：`packages/design-system/style.css`。
- 语言感知 typography：`packages/theme/src/typography.css`；`html[lang]` 是 profile selector。
- ECharts/Monaco runtime palette：`packages/design-system/src/palette.ts`。
- shadcn-vue variants 和共享尺寸：`packages/design-system/src/variants.ts`。
- 侧栏几何和状态契约：`packages/sidebar-menu`。
- `.paper-texture-overlay`、`.reveal-on-scroll` 和 reduced-motion 规则由设计系统提供。

新业务组件应消费语义 token，不添加第一方颜色字面量或局部 density 分支。落地页可保留其已登记的装饰性/排版豁免；新文件不得加入豁免。

验证：

```bash
./scripts/dev/verify-garden-design.sh
./scripts/dev/verify-garden-design.sh --with-build
```
