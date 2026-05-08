# Coding Style

## Immutability

Immutability is **CRITICAL**:
- **ALWAYS** create new objects, **NEVER** mutate existing ones
- Immutable data prevents hidden side effects

## Core Principles

- **KISS**: Prefer the simplest solution that actually works
- **DRY**: Extract repeated logic into shared functions
- **YAGNI**: Do not build features or abstractions before they are needed

## File Organization

- **MANY SMALL FILES > FEW LARGE FILES**
- 200-400 lines typical, 800 max

## Error Handling

- **ALWAYS** handle errors comprehensively
- Explicit handling at every level
- Never silently swallowing errors

## Input Validation

- **ALWAYS** validate at system boundaries
- Never trust external data

## Naming Conventions

- `camelCase` for variables/functions
- `PascalCase` for types/components
- `UPPER_SNAKE_CASE` for constants
- `use` prefix for custom hooks

## Code Smells to Avoid

- Deep nesting: Prefer early returns
- Magic numbers: Use named constants
- Long functions

## Checklist

- Functions < 50 lines
- Nesting > 4 levels avoided
