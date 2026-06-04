---
name: solarized-terminal-design-style
description: Solarized-based terminal/CLI design style guide for UltiCode. Defines color palette, spacing, typography, and component patterns for dark terminal-style UIs. Trigger when building dashboard, admin, or developer-facing interfaces.
---

# Solarized Terminal Design Style

## Overview

A design system inspired by terminal aesthetics and the Solarized color palette. Used for UltiCode's developer-facing interfaces (dashboards, admin panels, code viewers).

## Color Palette

### Base Colors (Dark Theme)

```
base03:    #002b36  (background)
base02:    #073642  (background highlight)
base01:    #586e75  (comment / secondary text)
base00:    #657b83  (body text)
base0:     #839496  (emphasized text)
base1:     #93a1a1  (primary text)
base2:     #eee8d5  (light bg)
base3:     #fdf6e3  (light bg highlight)
```

### Accent Colors

```
yellow:    #b58900
orange:    #cb4b16
red:       #dc322f
magenta:   #d33682
violet:    #6c71c4
blue:      #268bd2
cyan:      #2aa198
green:     #859900
```

## Typography

- **Primary:** Monospace font stack (JetBrains Mono, Fira Code, SF Mono, Menlo, monospace)
- **Size scale:** 12px / 14px / 16px / 20px / 24px
- **Line height:** 1.6 for body text, 1.4 for code blocks
- **Letter spacing:** -0.02em for headings

## Component Patterns

### Terminal Card

```
bg: base02
border: base01
border-radius: 8px
padding: 16px
shadow: 0 4px 24px rgba(0, 0, 0, 0.3)
```

### Code Block

```
bg: base03
text: base1
accent: cyan/blue for keywords
comment: base01
```

### Status Indicators

```
success: green (#859900)
warning: yellow (#b58900)
error:   red   (#dc322f)
info:    blue  (#268bd2)
```

## Spacing System

```
xs: 4px
sm: 8px
md: 16px
lg: 24px
xl: 32px
2xl: 48px
```

## Layout Principles

1. Content-first: maximize code/data visibility
2. Dense information display (respects developer expectations)
3. Minimal decoration — functional color only
4. Clear hierarchy through font size and color weight, not borders/shadows
5. Dark theme as primary; light theme as secondary

## When to Use

- Building admin dashboards or developer tools
- Code viewer / editor interfaces
- Status monitors and log viewers
- Any UI where terminal aesthetics match user expectations
