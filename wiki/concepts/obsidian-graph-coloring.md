---
title: Obsidian Graph View Coloring
type: concept
tags: [meta, tooling, obsidian, type/concept]
status: living
updated: 2026-07-05
sources:
  - wiki/SCHEMA.md
aliases: [graph coloring, graph.json colorGroups]
---

# Obsidian Graph View Coloring

> [!question] The problem
> Graph node colors come from Obsidian's `.obsidian/graph.json` →
> `colorGroups[]`. **Each Obsidian instance owns its own copy of that file** —
> the app rewrites it on open, normalizing anything an external tool wrote. A
> LLM (or any non-Obsidian editor) can write the perfect 3 rules into
> `graph.json`, and the next time the vault is opened the file is reverted to
> whatever the local UI last had (often empty). External edits are a known no-op.

## The decision

Coloring keys off the **`type/<x>` frontmatter tag** defined in
[SCHEMA §5](../SCHEMA.md). Three color groups, applied once per machine via the
Graph UI. The tag is the single source of truth — Bases, Dataview, tag-pane, and
graph coloring all read it.

## Palette (canonical)

| Tag | Hex | Visual | Meaning |
|-----|-----|--------|---------|
| `tag:#type/entity` | `#7c3aed` | violet | a single module / domain object |
| `tag:#type/concept` | `#0ea5e9` | blue | a decision, pattern, or invariant |
| `tag:#type/overview` | `#10b981` | green | a whole-system synthesis |

Pages without a `type/<x>` tag render in default gray — the **lint signal** that
a page was added without following the template.

## One-time setup (per machine that opens this vault)

The first time you open this vault on a new machine, the Graph view's
**Color groups** (颜色组) panel shows only a **新建颜色组** (Create color
group) button — no existing rules. The button is the only entry point; there is
no JSON to copy, no plugin to install, no CLI command.

1. Open the vault in Obsidian → click the **Graph view** icon in the left ribbon.
2. Click the ⚙️ gear (top-right of the graph panel) → scroll to **Color groups**.
3. Click **新建颜色组** → a row appears with a search box + color swatch.
4. Type `tag:#type/entity` → Enter → click swatch → paste `7c3aed` → Enter.
5. **新建颜色组** again → `tag:#type/concept` → `0ea5e9`.
6. **新建颜色组** once more → `tag:#type/overview` → `10b981`.
7. Close the panel. Obsidian writes the rules to `.obsidian/graph.json`
   itself. **Do not edit that file externally** — Obsidian normalizes it on
   next open.

| Query (search box) | Color (swatch) |
|---|---|
| `tag:#type/entity` | `7c3aed` (violet) |
| `tag:#type/concept` | `0ea5e9` (blue) |
| `tag:#type/overview` | `10b981` (green) |

## Verification

After setup, the graph should show nodes colored by type, with meta pages
(README, SCHEMA, index, log) and daily notes falling through to default gray —
intentional, since they carry `type/index`/`type/log`/`type/schema`/`type/daily`
rather than the three content tags.

If colors don't appear: Settings → Community plugins → ensure the **Graph**
core plugin is enabled, then repeat the setup.

## Trade-offs — alternative programmatic paths

For a fully scriptable pipeline (CI / headless), the same `#type/<x>` tag
convention is reusable via:

- **Obsidian Bases** — a `.base` file filtering by `type-tag`, rendered with
  Graph view's "open in Bases" action (Obsidian 1.7+).
- **Juggl** community plugin — Cytoscape-based renderer with node shapes per
  type and per-property color mapping via CSS-like rules.
- **Excalibrain** community plugin — tree-style hierarchical view colored by
  frontmatter property.

All three read the same tag, so adopting this section now keeps the door open
for any of them later.

## Related

- [[SCHEMA]] §5 — the `type/<x>` tag convention this page consumes
- [[theme-system]] — other Obsidian-rendering-adjacent contract
