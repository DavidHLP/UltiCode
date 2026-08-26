# UltiCode Garden Design System

Canonical visual language extracted from `apps/console/src/views/landing/`
(LandingView, HeroSection, ProductProofSection, UseCasesSection,
HumanControlSection, FinalStorySection, LandingHeader, LandingFooter).
As of this document, these values are the project-wide design contract:
every console view, every management view, and every shared package renders
through them. Implementation lives in:

- `packages/design-system/style.css` (color / radius / shadow tokens, light + dark)
- `packages/theme/src/typography.css` (font families, sizes, type roles)
- `packages/design-system/src/palette.ts` (runtime palette bridge for ECharts/Monaco)

## 1. Palette - Light ("Parchment Garden")

Raw bridge scale (historical variable names `--solarized-*` are kept for
compatibility; the values below replace Solarized):

| Token | Value | Role |
| --- | --- | --- |
| `--solarized-base03` | `#1c2412` | deepest olive ink; dark canvas anchor |
| `--solarized-base02` | `#26301b` | dark elevated surface |
| `--solarized-base01` | `#545c45` | brand olive; muted text on light |
| `--solarized-base00` | `#6a7259` | control borders, inputs, focus ring |
| `--solarized-base0`  | `#838f81` | dim sage-gray text |
| `--solarized-base1`  | `#a2afa9` | sage |
| `--solarized-base2`  | `#eae8d8` | parchment highlight tint |
| `--solarized-base3`  | `#e3e1d1` | parchment page background |

Semantic light mappings:

- `--background: #e3e1d1`, `--surface-elevated/--card/--popover: #f7f6f0`
- `--foreground: #19220e`, `--foreground-muted: #545c45`, dim: `#838f81`
- `--primary: #545c45` (hover `#3e4433`), `--primary-foreground: #f7f6f0`
- `--border-subtle: rgba(84,92,69,0.18)` via color-mix
- Sky accent fill `#92b3cf`; readable info ink `#46769b`
- Hero accent greens: dot `#588e67`, moss `#3f683b`; ochre `#6e5b28`; rust `#8f4822`

Status accents (light): success `#588e67`, warning `#9c7a14`, error `#8f4822`,
info `#46769b`, special `#6c71c4`; marks are color-mixed toward base03 ink for
WCAG AA; surfaces mix ~14% of the accent into `--surface`.

## 2. Palette - Dark ("Night Garden")

Derived dark variant consistent with the landing mood:

- background `#161a10`, elevated/card/popover `#1f2617`, highlight `#26301b`
- foreground `#e3e1d1`, muted `#a2afa9`
- primary control inverted: parchment `#e3e1d1` on ink `#1c2412`
- status marks lifted by mixing each accent toward base2 parchment

## 3. Geometry

- Radius ladder from `--radius: 0.5rem`: sm = 4px, md = 6px, lg = 8px (controls),
  xl = calc(+12px) = 20px (cards/panels). Landing uses small=4, control=8,
  card/panel=20.
- Shadows: subtle `0 4px 20px -4px rgba(25,34,14,.10)`;
  elevated/hover `0 12px 36px -8px rgba(25,34,14,.16)`.
- Base line-height 1.6; display headlines use tight tracking (-0.04em range).

## 4. Typography

Landing fonts become the global contract:

- Display/headings: `"Instrument Serif", "Newsreader", Georgia, "Noto Serif SC", serif`
  (editorial serif; italic accents allowed as on the landing hero)
- UI/body: `"Inter", system-ui, ... "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`
- Code/data: `"JetBrains Mono", "Fira Code", monospace` with CJK fallbacks
- The previous project-wide LXGW WenKai (楷体) contract is retired; all
  `--uc-font-*` roles, Tailwind `font-mono`, Monaco editor fonts, and chart
  font families now resolve through the stacks above.
- Page titles and section titles use the display serif via
  `--uc-font-display`.

Font loading is centralized in `packages/theme/src/typography.css`
(jsDelivr @fontsource imports); app `index.html` Google Fonts links mirror it.

## 5. Texture & Motion (app-wide)

- Paper grain: fixed full-viewport dot grid
  (`radial-gradient(ink 1px, transparent 0)` on a 4px tile) at opacity 0.035,
  pointer-events none - exposed as `.paper-texture-overlay` from the design
  system and mounted once per app root.
- Reveal-on-scroll: `opacity 0 -> 1`, `translateY(18px) -> 0`,
  0.75s `cubic-bezier(0.16, 1, 0.3, 1)`, always disabled under
  `prefers-reduced-motion`. Utility class `.reveal-on-scroll` ships globally;
  pages opt in per element.
- The landing blueprint side frames stay landing-only decoration.

## 6. Compatibility notes

- `--solarized-*` custom property names are historical; their VALUES are the
  garden palette above. `packages/design-system/src/palette.ts` mirrors the
  same values for non-CSS renderers (ECharts, Monaco).
- Monaco themes (`apps/console/src/utils/monaco-solarized-theme.ts`) consume
  `SOLARIZED_PALETTE` and follow automatically.
