# G-5 Decisions

## Centralize the console sider visual contract

- Context: The console sider mixed local shadcn utility styles, shared row styles, list-specific widths, colored category icons, and a separate user/dropdown treatment.
- Decision: Extend the existing `packages/sidebar-menu` CSS seam with Garden shell, row, group, list, user, and dropdown classes; make console `features/sider` consume those classes and remove local width/color overrides.
- Alternatives: A page-local stylesheet or a second sidebar component was rejected because both would preserve the existing style fork.
- Consequences: Console sidebar contexts share geometry and state treatment; the management app is not visually changed because it does not consume the new console shell classes.
