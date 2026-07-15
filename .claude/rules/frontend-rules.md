---
paths:
  - "console/**/*.{ts,vue,js,mjs,cjs,json,css,html}"
  - "management/**/*.{ts,vue,js,mjs,cjs,json,css,html}"
  - "shared/**/*.{ts,vue,js,mjs,cjs,json,css}"
---

# UltiCode frontend rules

- Read the nearest application/shared guide first; Console and Management intentionally use different API, routing, layout, and permission seams.
- HTTP calls **MUST** use the application's request helper or an established shared package that owns the transport/authentication seam. Do not create a component-local Axios/fetch client that bypasses auth, CSRF, retry, or error handling.
- Request and response contracts **MUST** be typed at the API boundary. Do not spread transport-specific envelopes, snake/camel conversion, or error parsing across components.
- User-visible text **MUST** use the established i18n modules and remain complete in both supported locales.
- Cross-application behavior belongs in a focused `shared/` package only when both apps share the same stable meaning. Direct Console-to-Management imports are forbidden.
- Markdown, KaTeX, and HTML rendering **MUST** use the shared sanitization pipeline. A component must not locally weaken sanitizer options.
- Theme state and `data-theme` writes **MUST** remain owned by the shared theme package; components consume tokens and APIs rather than reimplementing bootstrap logic.
- Interactive UI **MUST** support keyboard operation, visible focus, semantic controls/labels, and correct disabled/loading states.
- Async views **MUST** handle cancellation or stale results, normalize errors at the owned boundary, and avoid duplicate submissions.
- Build-time environment variables are public client data. Secrets, private service credentials, and privileged-only data **MUST NOT** enter frontend bundles.
- Keep generated component primitives and application-specific components separate; do not bulk-edit generated UI code for a one-off feature style.
- Changes to shared contracts, auth, theme, or rendering **MUST** run the affected package checks and both consuming applications' relevant checks.
