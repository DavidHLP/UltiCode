# Shared UI implementation reference

The filename is retained for existing references. The former Garden/Solarized
specification no longer describes the shared Console/Management implementation.
Landing retains its independent design; its colors, typography, illustrations
and motion are not the shared UI contract.

## Current sources of truth

| Concern | Implementation |
| --- | --- |
| Semantic light/dark colors, appearance overrides, radii and layout | [style.css](../style.css) |
| Appearance options, defaults and validation | [appearance-model.ts](../../theme/src/appearance-model.ts) |
| Account-scoped storage and preview/apply/cancel | [appearance.ts](../../theme/src/appearance.ts) |
| Shared typography | [typography.css](../../theme/src/typography.css) |
| Integrated component implementations | [src/components/ui](../src/components/ui) |
| Renderer fallbacks and CSS color conversion | [palette.ts](../src/palette.ts) |
| Chart palette resolution | [chartPalette.ts](../src/chartPalette.ts) |
| Monaco theme adapter | [monaco-theme.ts](../../../apps/console/src/utils/monaco-theme.ts) |
| Presentation color checks | [color-contract.test.mjs](../test/color-contract.test.mjs) |

## Integration rules

Consume public semantic tokens and exported components. Do not reproduce
hardcoded palette tables in applications or treat renderer fallback values as
the active theme. The CSS foundation uses neutral OKLCH colors; allowlisted
appearance choices adjust base, accent, chart, font, radius, density, menu and
mode. The default medium radius is `0.625rem`, and the radius ladder is derived
by multiplication rather than the former fixed Garden values.

Theme owns DOM application, account selection, browser persistence and
bootstrap. Shared settings preview is reversible; Apply saves only when
persistence succeeds, and Cancel restores the saved appearance. Landing does
not participate in this lifecycle.

Renderer adapters resolve computed semantic tokens with `readCssColor` and
`UI_PALETTE` fallbacks. Status color needs a redundant non-color indication.
Static color checks do not establish contrast or browser accessibility; validate
those against the actual selected appearance.

See the [package integration guide](../README.md) and
[canonical development guide](../../../docs/DEVELOPMENT.md#共享-shadcn-vue-与外观设置).
