# UltiCode Solarized Theme Design v1.0

This package is the public design seam for both UltiCode frontends. It owns the
canonical Solarized palette, Light/Dark semantic mappings, shared component
states, chart colors and the small TypeScript token interface.

Consumers import the stylesheet through:

    @import "@ulticode/design-system/style.css";

Application code consumes semantic variables or Tailwind utilities generated
from them. It must not copy palette values or redefine Light/Dark mappings.

## Design principles

1. Use only the eight Solarized monotones and eight invariant accent colors.
2. Monotones communicate hierarchy; accents communicate meaning.
3. Switching modes reverses the monotone relationships without changing an
   accent's meaning.
4. Essential state is never communicated by color alone. Pair color with text,
   an icon, a border, a line style or a shape.
5. Small essential text and control boundaries use the accessible product
   mappings below, even when the original Solarized selective-contrast pair is
   slightly below WCAG AA.

Reference: https://ethanschoonover.com/solarized/

## Canonical palette

| Token   | Hex     | Role                                      |
| ------- | ------- | ----------------------------------------- |
| base03  | #002b36 | Dark canvas                               |
| base02  | #073642 | Dark highlighted/elevated surface         |
| base01  | #586e75 | Dark subtle boundary; Light strong text   |
| base00  | #657b83 | Light raw secondary copy and control boundary |
| base0   | #839496 | Dark body text and control boundary       |
| base1   | #93a1a1 | Dark strong text; Light subtle boundary   |
| base2   | #eee8d5 | Light highlighted/sunken surface          |
| base3   | #fdf6e3 | Light canvas and light-on-accent text     |
| yellow  | #b58900 | Waiting and warning                       |
| orange  | #cb4b16 | Limits, timeouts and compilation problems |
| red     | #dc322f | Failure and destructive actions           |
| magenta | #d33682 | System exceptions and premium states      |
| violet  | #6c71c4 | Privileged and special states             |
| blue    | #268bd2 | Primary action, focus and link decoration |
| cyan    | #2aa198 | Information and running state             |
| green   | #859900 | Success and accepted state                |

## Public semantic mappings

| Semantic token               | Light  | Dark   |
| ---------------------------- | ------ | ------ |
| background / surface         | base3  | base03 |
| surface-elevated             | base3  | base02 |
| surface-highlight            | base2  | base02 |
| surface-sunken               | base2  | base03 |
| overlay                      | base03 blend | base03 blend |
| foreground                   | base01 | base1  |
| foreground-strong            | base03 | base1  |
| foreground-muted             | base01 | base0  |
| border-subtle                | base1  | base01 |
| border-control / input       | base00 | base0  |
| primary action identity      | blue   | blue   |
| primary-control surface      | base03 | base3  |
| primary-control-foreground   | base3  | base03  |
| primary-foreground           | base3  | base3   |
| link-foreground              | base01 | base1  |
| link-decoration / focus ring | blue   | blue   |

`foreground-muted` remains AA-safe for ordinary Light text by mapping to base01;
base00 is reserved for raw secondary copy and control boundaries. Essential
small-text labels use `foreground`/`foreground-strong` (or the
`muted-foreground` component token), which preserve the WCAG AA 4.5:1 target.

Light highlighted surfaces pair with base03 text; normal and muted copy on the
base3 canvas use base01. Link text stays neutral and uses blue decoration so it
remains readable in both modes.

## Status semantics

| Meaning                       | Accent |
| ----------------------------- | ------ |
| success / accepted / solved   | green  |
| warning / pending / attempted | yellow |
| error / failed / destructive  | red    |
| info / judging / running      | cyan   |
| special / privileged          | violet |

Status surfaces use the public status-\*-surface variables. Use
foreground-strong neutral text on the surface and apply the raw `status-*` accent
only when its contrast is sufficient. For text, icons, borders and chart marks,
use the public `status-*-mark` tokens; they preserve the accent hue while mixing
with the opposite Solarized canvas neutral to meet the 3:1 graphical and 4.5:1
text thresholds in both modes.

## Chart semantics

The stable series order is blue, cyan, green, magenta, orange, violet, yellow,
red. Charts must also vary line style, point shape, pattern or direct labels.
Axes use foreground tokens, grids use border-subtle, and tooltips use the
popover foreground/background pair.

## Component states

| State         | Surface                | Marker                                 |
| ------------- | ---------------------- | -------------------------------------- |
| default       | current surface        | default boundary                       |
| hover         | surface-highlight      | default boundary                       |
| active        | surface-highlight      | border-control                         |
| selected      | surface-highlight      | blue indicator plus icon/text          |
| focus-visible | unchanged              | two-pixel blue ring                   |
| error         | status-error-surface   | red icon/border plus message           |
| success       | status-success-surface | green icon/border plus message         |
| disabled      | current surface        | disabled attribute and reduced opacity |

Primary buttons use invariant Solarized blue with base3 text and a blue focus
marker in both modes, as required by the product contract. The button boundary
and adjacent labels provide redundant interaction feedback; high-contrast
text-bearing controls may opt into the `primary-control` pair when small-text
AA is required. Destructive controls use an error surface, readable neutral
text and a red marker instead of relying on a small white-on-red label.


## Ownership

- packages/design-system/style.css owns palette, semantic mappings and shared
  presentation rules.
- packages/design-system/src/index.ts exposes token references to TypeScript.
- packages/design-system/src/palette.ts owns the canonical runtime palette
  (`SOLARIZED_PALETTE`) and the `readCssColor` helper; non-CSS renderers resolve
  concrete colors through it.
- packages/design-system/src/variants.ts owns cross-app Button, Badge and menu
  state classes; applications may append local variants but do not copy them.
- packages/theme alone owns theme state, persistence and DOM mode application.
- App adapters may translate public tokens into Monaco, WebGL or chart-renderer
  configuration through the runtime palette bridge but may not create a second
  palette.
- External brand marks and renderer sentinel values are explicit adapter
  exceptions; they do not become theme tokens.

## Runtime renderer bridge

Non-CSS renderers (ECharts, Monaco, WebGL) need concrete colors. They resolve
them through the runtime palette bridge exported from the package entry:

```ts
import { readCssColor, SOLARIZED_PALETTE } from "@ulticode/design-system";

// Concrete current-theme color with a canonical fallback (no DOM = fallback).
const axis = readCssColor("--foreground", SOLARIZED_PALETTE.base1);

// Static canonical values for shader/default configuration.
const clear = SOLARIZED_PALETTE.base03;
```

- `SOLARIZED_PALETTE` is the single runtime copy of the 16 canonical values,
  mirroring `style.css`. Renderers must not define a second palette.
- `readCssColor(variable, fallback)` prefers the browser-computed value of the
  custom property (accepts `"--name"` or `"var(--name)"`) and returns
  `fallback` when there is no DOM or the property is unset. `fallback` must be
  one of the `SOLARIZED_PALETTE` values — renderers can never paint a
  non-Solarized color, even as a fallback.

## Color literal scan boundary

A contract test (`test/color-contract.test.mjs`) scans first-party runtime
source under `apps/*` and `packages/*` and rejects color literals outside the
canonical palette: non-Solarized hex and `0x` values, direct `rgb`/`rgba`/
`hsl`/`hsla`/`oklch`/`oklab` calls, and legacy `hsl(var(--token))` wrappers.
Tokenized `color-mix(in oklch, var(--token), ...)` and `var(--token)` references
are allowed. Comments and URLs are ignored; test fixtures, third-party
`vendor/` directories, `dist` and `node_modules` are out of scope.

Explicit exceptions (they do not become theme tokens):

- canonical bridge (`src/palette.ts`) and canonical CSS owner (`style.css`);
- static canonical metadata/assets — canonical values always pass;
- external brand and data: Google OAuth brand (`OAuthButton.vue`) and Codeforces
  rating convention (`contest.ts`);
- third-party vendor/generated files: the landing Bootstrap bundle
  (`landing/styles/bundle.css`) and `landing/vendor/**`;
- renderer sentinels: the alpha-0 render-target clear in
  (`landing/experience/Awards.js`).

Owned first-party runtime files have no outstanding color-literal debt. New or
undeclared literals fail the contract test; add only a documented external
brand/data/vendor exception when the value is not a theme token.

## Acceptance

- All sixteen canonical values match Solarized.
- Accent meanings are invariant across modes.
- Essential body text reaches 4.5:1 and functional boundaries reach 3:1.
- Both apps import the package stylesheet through its public export.
- Shared UI does not hardcode alternative palette values.
- Package tests, both app type checks/tests/builds and Light/Dark browser audits
  pass before changing this contract.
