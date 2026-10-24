# UltiCode Design System

Shared shadcn-vue components, semantic styles and renderer adapters for Console
and Management. Landing owns its independent visual identity and does not
consume this package's theme or appearance settings.

## Consumption

Import the shared stylesheet once per consuming application:

```css
@import "@ulticode/design-system/style.css";
```

Use the package entry point or declared subpath exports, such as
`@ulticode/design-system/ui/button`. The package's `components.json` owns the
shadcn-vue CLI configuration; component additions and verification are described
in [Development](../../docs/DEVELOPMENT.md#共享-shadcn-vue-与外观设置).

## Ownership

- [style.css](style.css) owns semantic colors, appearance mappings, radius scales,
  shared layout metrics and component presentation.
- [src/components/ui](src/components/ui) owns the integrated shadcn-vue components,
  including Button, Badge, Message, Bubble, Item and Trace.
- [src/variants.ts](src/variants.ts) owns difficulty badge mappings.
- [src/palette.ts](src/palette.ts) owns `UI_PALETTE` renderer fallbacks and
  `readCssColor`; [src/chartPalette.ts](src/chartPalette.ts) resolves chart tokens.
- [packages/theme](../theme) owns validated appearance state, account-scoped
  browser persistence, preview/apply/cancel and DOM/bootstrap application.
- [AppearanceSettings.vue](src/components/appearance/AppearanceSettings.vue)
  supplies the shared settings interface.
- Applications own business behavior and adapt specialized renderers through
  these public seams.

## Semantic styles and appearance

Use semantic utilities such as `bg-background`, `text-foreground`,
`border-border` and status tokens instead of copying palette values.
The CSS foundation uses neutral OKLCH colors, with light/dark and allowlisted
base, accent and chart overrides. Status meanings remain independent of the
selected accent. Pair status color with text, an icon or another visible cue.

Appearance options and defaults are defined by
[appearance-model.ts](../theme/src/appearance-model.ts): accent, base, chart,
font, radius, density, menu and mode. Only the theme package applies appearance
to the DOM. Preferences are local to the current origin and authenticated
account; they do not provide server-side or cross-device synchronization.

The default medium radius is `0.625rem`. CSS derives sm/md/lg/xl using
0.6/0.8/1/1.4 multiples of that base; appearance may select none, small or large
instead. Read the current stylesheet for the complete scale and overrides.
Shared typography and locale metrics remain separate from Landing's local styles.

## Renderer bridge

Canvas renderers need concrete colors rather than CSS variables:

```ts
import { readCssColor, UI_PALETTE } from "@ulticode/design-system";

const foreground = readCssColor("--foreground", UI_PALETTE.foreground);
```

`readCssColor` validates the variable reference, reads its computed value and
converts supported browser colors through Canvas, preserving alpha. Without a
usable DOM, variable or Canvas context it returns the supplied fallback.
`UI_PALETTE` supplies default-theme fallbacks; it is not an enforced global
palette. Charts can use `resolveChartPalette` or `useChartPalette` to consume
semantic series, status, surface and typography tokens. Monaco's adapter is
[monaco-theme.ts](../../apps/console/src/utils/monaco-theme.ts).

## Verification

Run from this package:

```bash
pnpm type-check
pnpm test
```

[test/color-contract.test.mjs](test/color-contract.test.mjs) checks the renderer
bridge and scans Console, Management, auth-ui, sidebar-menu and badge-config
runtime presentation sources for hardcoded hex colors and named Tailwind color
utilities, with narrow external-data exceptions. It excludes Landing, tests,
vendor and generated output. It does not enforce the former Garden/Solarized
palette or prove browser contrast.

For behavior or visual changes, also run relevant consuming-app checks and
inspect keyboard focus, contrast, light/dark modes and responsive behavior.
See the [implementation reference](docs/GARDEN_DESIGN_SPEC.md) for source ownership.
