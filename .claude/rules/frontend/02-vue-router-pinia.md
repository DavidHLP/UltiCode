---
paths:
  - "console/src/router/**/*.ts"
  - "console/src/stores/**/*.ts"
  - "management/src/router/**/*.ts"
  - "management/src/stores/**/*.ts"
  - "shared/**/*{router,store}*.ts"
kind: rules
summary: 'Vue Router and Pinia store conventions.'
---

# Vue Router and Pinia rules

- Read the affected application's guide before changing route bootstrap, layout, permission metadata, or authentication state.
- Route components **SHOULD** use lazy imports unless they are required for the initial shell.
- Guards **MUST** await the established authentication bootstrap and must not start duplicate login/refresh/profile requests.
- Route metadata and UI gates **MUST NOT** be treated as backend authorization.
- Stores **SHOULD** use the setup-store style already established by the applications. Components destructuring store state **MUST** use `storeToRefs`.
- API modules own HTTP transport details. Stores may orchestrate API functions and state transitions but must not create independent Axios clients.
- Store actions **MUST** leave loading/error state consistent on success, failure, cancellation, and logout/reset.
- Do not execute navigation, network calls, or browser-storage writes as hidden module-import side effects.
- Shareable navigation state such as filters, sorting, tabs, or pagination **SHOULD** live in route params/query when the existing feature follows that model.
- Shared packages **MUST NOT** import an application router or assume a Console/Management route name.
