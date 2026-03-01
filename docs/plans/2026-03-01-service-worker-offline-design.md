# Service Worker for Offline Code Editing - Design Document

> **Date**: 2026-03-01
> **Status**: Approved
> **Scope**: Basic Offline Support

## Overview

Implement a Service Worker using Vite PWA Plugin to enable offline code editing for the UltiCode console application.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser                                  │
├─────────────────────────────────────────────────────────────┤
│  Vue App                    │  Service Worker               │
│  ┌─────────────────────┐    │  ┌─────────────────────────┐ │
│  │ CodeView.vue        │    │  │ Cache Strategy:         │ │
│  │ - useCodeAutosave   │    │  │ - Precache (app shell)  │ │
│  │ - useNetworkStatus  │    │  │ - NetworkFirst (API)    │ │
│  └─────────────────────┘    │  └─────────────────────────┘ │
│                             │                               │
│  localStorage               │  Cache Storage                │
│  ┌─────────────────────┐    │  ┌─────────────────────────┐ │
│  │ ulticode_autosave_* │    │  │ / (app shell)           │ │
│  │ - Code snapshots    │    │  │ - JS, CSS, fonts        │ │
│  │ - Metadata          │    │  │ - Monaco Editor         │ │
│  └─────────────────────┘    │  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Caching Strategy

| Resource Type | Strategy | Reason |
|---------------|----------|--------|
| **App Shell** (JS, CSS, HTML) | Precache | Required for app to load offline |
| **Monaco Editor** | Precache | Large but essential for code editing |
| **Static Assets** (fonts, images) | CacheFirst | Rarely changes, cache for speed |
| **API Calls** | NetworkFirst | Always fetch fresh data, fall back to cache |

### Precache Resources

- `index.html`
- All JS/CSS bundles
- Monaco Editor files
- Font files (if local)
- Favicon

## Component Design

### Files to Create

| File | Purpose |
|------|---------|
| `console/vite.config.ts` | Add PWA plugin configuration |
| `console/src/pwa-register.ts` | Service Worker registration |
| `console/src/composables/usePWA.ts` | PWA status composable (update detection, install prompt) |
| `console/src/components/common/PWAUpdatePrompt.vue` | UI for update notifications |
| `console/src/utils/submitQueue.ts` | IndexedDB-based submission queue |

### Files to Modify

| File | Changes |
|------|---------|
| `console/src/main.ts` | Import PWA registration |
| `console/src/App.vue` | Add PWAUpdatePrompt component |
| `console/src/views/problems/code/CodeView.vue` | Integrate offline queue status |
| `console/src/i18n/locales/*/common.ts` | Add PWA-related translations |

## Data Flow

### Offline Code Editing Flow

```
1. User types code (offline)
   ┌──────────────┐
   │ CodeView.vue │ ──useCodeAutosave──▶ localStorage
   └──────────────┘

2. User clicks "Submit" (offline)
   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
   │ CodeView.vue │ ──▶ │ SubmitQueue  │ ──▶ │ IndexedDB    │
   └──────────────┘     │ (new)        │     │ (queue store)│
                        └──────────────┘     └──────────────┘

3. Network restored
   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
   │ NetworkStatus│ ──▶ │ SubmitQueue  │ ──▶ │ Backend API  │
   │ .ts          │     │ .process()   │     │              │
   └──────────────┘     └──────────────┘     └──────────────┘
                              │
                              ▼
                        ┌──────────────┐
                        │ Toast:       │
                        │ "Submitted!" │
                        └──────────────┘
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| **SW registration fails** | Log error, app continues to work online-only |
| **Cache update available** | Show toast with "Update available, refresh?" |
| **Offline submission** | Queue to IndexedDB, show "Will submit when online" toast |
| **Queue sync fails** | Retry with exponential backoff, keep in queue |
| **Storage quota exceeded** | Clear oldest cached submissions, notify user |
| **User closes tab offline** | Data persists in localStorage + IndexedDB |

## Testing Strategy

### Unit Tests

- `console/src/composables/__tests__/usePWA.spec.ts`
- `console/src/utils/__tests__/submitQueue.spec.ts`

### Integration Tests

- Service Worker registration
- Cache retrieval

### E2E Tests

- Offline mode simulation
- Submission queue

### Manual Testing

1. Chrome DevTools → Application → Service Workers
2. Chrome DevTools → Network → Offline mode
3. Test code editing → submit → restore connection

## Dependencies

### New Dependencies

| Package | Purpose |
|---------|---------|
| `vite-plugin-pwa` | PWA plugin for Vite with Workbox |
| `idb` | IndexedDB wrapper for submission queue |

## Implementation Order

1. Install `vite-plugin-pwa` and configure in `vite.config.ts`
2. Create `pwa-register.ts` for SW registration
3. Create `usePWA.ts` composable
4. Create `PWAUpdatePrompt.vue` component
5. Create `submitQueue.ts` utility with IndexedDB
6. Integrate offline queue in `CodeView.vue`
7. Add i18n translations
8. Write unit tests

## Success Criteria

- [ ] App loads and functions offline
- [ ] Code editor works offline with autosave
- [ ] Submissions are queued when offline
- [ ] Queued submissions sync when back online
- [ ] Update prompt appears when new version available
- [ ] Unit tests pass with >80% coverage
