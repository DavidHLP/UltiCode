# UltiCode Garden Design System

This package is the public design seam for both UltiCode frontends. It owns the
canonical Garden palette, Light/Dark semantic mappings, zh-CN/en-US locale
profiles, shared component states, chart colors and the small TypeScript token
interface. The full visual specification lives in
`docs/GARDEN_DESIGN_SPEC.md`; this README is the integration contract.

Consumers import the stylesheet through:

    @import "@ulticode/design-system/style.css";

Application code consumes semantic variables or Tailwind utilities generated
from them. It must not copy palette values or redefine Light/Dark mappings.

## Design principles

1. Use the shared Garden monotones and invariant accent colors.
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
| base03  | #1c2412 | Deepest olive ink; dark canvas              |
| base02  | #26301b | Dark elevated surface                       |
| base01  | #545c45 | Brand olive; muted light text               |
| base00  | #6a7259 | Control borders and focus ring              |
| base0   | #838f81 | Dim sage-gray text                          |
| base1   | #a2afa9 | Sage boundary and dark muted text           |
| base2   | #eae8d8 | Light highlighted/sunken surface            |
| base3   | #e3e1d1 | Light parchment canvas                      |
| yellow  | #9c7a14 | Waiting and warning                         |
| orange  | #b4622d | Limits, timeouts and compilation problems   |
| red     | #8f4822 | Failure and destructive actions             |
| magenta | #a05c74 | System exceptions and premium states        |
| violet  | #6c71c4 | Privileged and special states               |
| blue    | #46769b | Link decoration and explicit data emphasis  |
| cyan    | #4e7d64 | Information and running state               |
| green   | #588e67 | Success and accepted state                  |

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
| primary action identity      | base03 | base3  |
| primary-control surface      | base03 | base3  |
| primary-control-foreground   | base3  | base03  |
| primary-foreground           | base3  | base03 |
| link-foreground              | base01 | base1  |
| link-decoration              | blue   | blue   |
| focus ring                   | base00 | base0  |

`foreground-muted` remains AA-safe for ordinary Light text by mapping to base01;
base00 is reserved for raw secondary copy and control boundaries. Essential
small-text labels use `foreground`/`foreground-strong` (or the
`muted-foreground` component token), which preserve the WCAG AA 4.5:1 target.

Light highlighted surfaces pair with base03 text; normal and muted copy on the
base3 canvas use base01. Link text stays neutral and uses blue decoration so it
remains readable in both modes.

## Rounded geometry

The public geometry scale uses a shared `--radius` base of `0.5rem` (8px):

| Token | Value | Typical use |
| ----- | ----- | ------------ |
| `--radius-sm` | 4px | compact tags and inline controls |
| `--radius-md` | 6px | buttons, inputs, menu rows |
| `--radius-lg` | 8px | cards and panels |
| `--radius-xl` | 20px | dialogs and elevated surfaces |

`rounded-none` is retained as a compatibility class and resolves to
`--radius-md`, so legacy feature markup follows the same rounded visual system.
Use `rounded-full` only for intentionally circular status marks, avatars and
loading indicators.

## Locale profiles

`html[lang="zh-CN"]` and `html[lang="en-US"]` select the two supported global
design profiles. The shared locale lifecycle owns the attribute, so switching
language changes content and the associated typography/layout metrics together.

- `zh-CN`: Noto Sans/Serif SC first, relaxed CJK leading, normal label tracking,
  40px controls, 16px page gutter.
- `en-US`: Inter/Instrument Serif first, tighter editorial leading and display
  tracking, 36px controls, 20px page gutter.

Both profiles retain the same Garden color semantics and can be combined with
the console `comfortable` or management `compact` density profile.

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
| hover         | surface-highlight      | default boundary; outline/secondary add a visible semantic border |
| active        | surface-highlight      | border-control                         |
| selected      | surface-highlight      | monotone primary indicator plus icon/text |
| focus-visible | unchanged              | two-pixel border-control ring          |
| error         | status-error-surface   | red icon/border plus message           |
| success       | status-success-surface | green icon/border plus message         |
| disabled      | current surface        | disabled attribute and reduced opacity |

Primary buttons use the inverted Solarized monotone pair (`base03:base3` in
Light, `base3:base03` in Dark). This keeps navigation, selection and action
hierarchy aligned with the selective-contrast model instead of turning blue
into a generic reminder color. Blue remains available for link decoration and
explicit data emphasis. The button boundary and adjacent labels provide
redundant interaction feedback; high-contrast text-bearing controls may opt into
the `primary-control` pair when small-text AA is required. Destructive controls
use an error surface, readable neutral text and a red marker instead of relying
on a small white-on-red label.


## Ownership

- packages/design-system/style.css owns palette, semantic mappings and shared
  presentation rules.
- packages/design-system/src/index.ts exposes token references to TypeScript.
- packages/design-system/src/palette.ts owns the canonical runtime palette
  (`SOLARIZED_PALETTE`) and the `readCssColor` helper; non-CSS renderers resolve
  concrete colors through it.
- packages/design-system/src/variants.ts owns the current shadcn-vue Button
  foundation (base classes, shared sizes and semantic variants) plus shared
  Badge and menu state classes; applications may append local variants but do
  not copy the shared contract.
- packages/theme owns theme state, persistence, shared tokens and DOM mode
  application; `html[lang]` is the locale profile selector.
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
  rating convention (`contest.ts`).

Owned first-party runtime files have no outstanding color-literal debt. New or
undeclared literals fail the contract test; add only a documented external
brand/data/vendor exception when the value is not a theme token.

## Acceptance

- All sixteen canonical values match the Garden palette in `style.css`.
- Accent meanings are invariant across modes.
- Essential body text reaches 4.5:1 and functional boundaries reach 3:1.
- Both apps import the package stylesheet through its public export.
- Shared UI does not hardcode alternative palette values.
- Package tests, both app type checks/tests/builds and Light/Dark browser audits
  pass before changing this contract.
