# Landing design boundary

Landing owns its editorial design, styles, bilingual copy, and artwork independently of Console and Management.

- Preserve the existing page composition, fonts, palette, illustrations, motion, and responsive layouts. A platform-wide UI or theme change does not authorize a Landing redesign.
- Do not introduce `@ulticode/design-system`, `@ulticode/theme`, shared appearance settings, or generated theme bootstrap into this app without an explicit user request to change Landing's design.
- Keep platform links, locale switching, SEO metadata, keyboard navigation, reduced-motion support, and the `/landing/` deployment entry working when making an authorized change.
- Review Landing changes against these boundaries and run its existing focused checks. Do not replace its design regression assertions with assertions for a shared component migration.
