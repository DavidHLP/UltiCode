# Service Worker for Offline Code Editing - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable offline code editing with cached app shell and queued submissions for UltiCode console application.

**Architecture:** Use vite-plugin-pwa to auto-generate a Service Worker with Workbox. Cache the app shell (HTML, JS, CSS, Monaco Editor) for offline loading. Queue code submissions to IndexedDB when offline, sync when back online.

**Tech Stack:** Vue 3, Vite, vite-plugin-pwa, Workbox, IndexedDB (via `idb` library)

---

## Task 1: Install Dependencies

**Files:**
- Modify: `console/package.json`

**Step 1: Install vite-plugin-pwa and idb**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm add -D vite-plugin-pwa && pnpm add idb
```

Expected: Dependencies added successfully

**Step 2: Verify installation**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm ls vite-plugin-pwa idb
```

Expected: Both packages listed with versions

**Step 3: Commit**

```bash
git add console/package.json console/pnpm-lock.yaml
git commit -m "feat: add vite-plugin-pwa and idb dependencies for offline support"
```

---

## Task 2: Configure Vite PWA Plugin

**Files:**
- Modify: `console/vite.config.ts`

**Step 1: Update vite.config.ts with PWA configuration**

Replace the entire file content with:

```typescript
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, type PluginOption } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'
import Icons from 'unplugin-icons/vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    tailwindcss(),
    vueDevTools(),
    Icons({ compiler: 'vue3', autoInstall: true }) as PluginOption,
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.ico', 'robots.txt', 'apple-touch-icon.png'],
      manifest: {
        name: 'UltiCode',
        short_name: 'UltiCode',
        description: 'Online coding competition platform',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-cache',
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'gstatic-fonts-cache',
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
        ],
      },
      devOptions: {
        enabled: false, // Disable in dev for faster HMR
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 9002,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Split Monaco Editor into its own chunk (large dependency)
          'monaco-editor': ['monaco-editor'],
          // Vue ecosystem
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          // UI libraries
          'ui-vendor': ['reka-ui', 'lucide-vue-next', '@tabler/icons-vue'],
          // Markdown and code highlighting
          'markdown': ['markdown-it', 'highlight.js', 'katex'],
        },
      },
    },
    // Increase chunk size warning limit
    chunkSizeWarningLimit: 1000,
  },
})
```

**Step 2: Verify TypeScript compilation**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm type-check
```

Expected: No TypeScript errors

**Step 3: Commit**

```bash
git add console/vite.config.ts
git commit -m "feat: configure vite-plugin-pwa for offline support"
```

---

## Task 3: Create PWA Registration Module

**Files:**
- Create: `console/src/pwa-register.ts`

**Step 1: Create pwa-register.ts**

```typescript
/**
 * PWA Service Worker Registration
 *
 * Registers the service worker for offline support and handles update prompts.
 * This module is imported in main.ts to enable PWA functionality.
 */

import { registerSW } from 'virtual:pwa-register'

// Export the update prompt callback type
export type UpdatePromptCallback = (reload: () => void) => void

// Store the update callback
let updateCallback: UpdatePromptCallback | null = null

/**
 * Set the callback to be called when an update is available
 */
export function setUpdateCallback(callback: UpdatePromptCallback): void {
  updateCallback = callback
}

/**
 * Register the service worker
 * Returns a function to check for updates
 */
export const updateServiceWorker = registerSW({
  immediate: true,
  onNeedRefresh() {
    // Called when a new version is available
    if (updateCallback) {
      updateCallback(() => {
        updateServiceWorker(true) // true = reload the page
      })
    }
  },
  onOfflineReady() {
    // Called when the app is ready to work offline
    console.log('[PWA] App ready to work offline')
  },
  onRegistered(swRegistration) {
    // Check for updates every hour
    if (swRegistration) {
      setInterval(() => {
        swRegistration.update()
      }, 60 * 60 * 1000)
    }
  },
  onRegisterError(error) {
    console.error('[PWA] Service worker registration error:', error)
  },
})

// Type declaration for virtual module
declare module 'virtual:pwa-register' {
  export interface RegisterSWOptions {
    immediate?: boolean
    onNeedRefresh?: () => void
    onOfflineReady?: () => void
    onRegistered?: (registration: ServiceWorkerRegistration | undefined) => void
    onRegisterError?: (error: Error) => void
  }

  export function registerSW(options?: RegisterSWOptions): (reloadPage?: boolean) => void
}
```

**Step 2: Create virtual module type declaration**

Create file: `console/src/types/virtual-modules.d.ts`

```typescript
/**
 * Type declarations for virtual modules provided by vite-plugin-pwa
 */

declare module 'virtual:pwa-register' {
  export interface RegisterSWOptions {
    immediate?: boolean
    onNeedRefresh?: () => void
    onOfflineReady?: () => void
    onRegistered?: (registration: ServiceWorkerRegistration | undefined) => void
    onRegisterError?: (error: Error) => void
  }

  export function registerSW(options?: RegisterSWOptions): (reloadPage?: boolean) => void
}
```

**Step 3: Commit**

```bash
git add console/src/pwa-register.ts console/src/types/virtual-modules.d.ts
git commit -m "feat: add PWA service worker registration module"
```

---

## Task 4: Create usePWA Composable

**Files:**
- Create: `console/src/composables/usePWA.ts`
- Create: `console/src/composables/__tests__/usePWA.spec.ts`

**Step 1: Write the failing test first**

Create file: `console/src/composables/__tests__/usePWA.spec.ts`

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'

// Mock the pwa-register module
vi.mock('virtual:pwa-register', () => ({
  registerSW: vi.fn(() => vi.fn()),
}))

// Mock the pwa-register module import
vi.mock('@/pwa-register', () => ({
  setUpdateCallback: vi.fn((callback) => {
    // Store callback for testing
    ;(globalThis as { __testUpdateCallback?: (reload: () => void) => void }).___testUpdateCallback = callback
  }),
  updateServiceWorker: vi.fn(),
}))

describe('usePWA', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should provide offline-ready state', async () => {
    const { usePWA } = await import('../usePWA')
    const { isOfflineReady } = usePWA()

    expect(isOfflineReady.value).toBe(false)
  })

  it('should provide need-refresh state', async () => {
    const { usePWA } = await import('../usePWA')
    const { needRefresh } = usePWA()

    expect(needRefresh.value).toBe(false)
  })

  it('should provide update service worker function', async () => {
    const { usePWA } = await import('../usePWA')
    const { updateServiceWorker } = usePWA()

    expect(typeof updateServiceWorker).toBe('function')
  })
})
```

**Step 2: Run test to verify it fails**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm vitest run src/composables/__tests__/usePWA.spec.ts
```

Expected: Test fails because `usePWA.ts` doesn't exist yet

**Step 3: Create the usePWA composable**

Create file: `console/src/composables/usePWA.ts`

```typescript
/**
 * PWA Composable
 *
 * Provides reactive state for PWA features including:
 * - Offline readiness status
 * - Update availability detection
 * - Service worker update controls
 */

import { ref, onMounted, onUnmounted } from 'vue'
import { setUpdateCallback, updateServiceWorker as updateSW } from '@/pwa-register'

// Global state shared across all instances
const isOfflineReady = ref(false)
const needRefresh = ref(false)
let reloadCallback: (() => void) | null = null

// Set up the update callback once
let initialized = false

function initializePWA(): void {
  if (initialized) return
  initialized = true

  setUpdateCallback((reload) => {
    needRefresh.value = true
    reloadCallback = reload
  })
}

export interface UsePWAReturn {
  /** Whether the app is ready to work offline */
  isOfflineReady: typeof isOfflineReady
  /** Whether a new version is available */
  needRefresh: typeof needRefresh
  /** Update the service worker and reload the page */
  updateServiceWorker: () => void
  /** Dismiss the update prompt */
  close: () => void
}

/**
 * Composable for PWA functionality
 *
 * @example
 * ```vue
 * <script setup>
 * import { usePWA } from '@/composables/usePWA'
 *
 * const { needRefresh, updateServiceWorker, close } = usePWA()
 * </script>
 *
 * <template>
 *   <div v-if="needRefresh">
 *     <p>New version available!</p>
 *     <button @click="updateServiceWorker">Update</button>
 *     <button @click="close">Later</button>
 *   </div>
 * </template>
 * ```
 */
export function usePWA(): UsePWAReturn {
  onMounted(() => {
    initializePWA()
  })

  /**
   * Update the service worker and reload the page
   */
  function handleUpdate(): void {
    if (reloadCallback) {
      reloadCallback()
    } else {
      updateSW(true)
    }
    needRefresh.value = false
  }

  /**
   * Dismiss the update prompt
   */
  function close(): void {
    needRefresh.value = false
  }

  return {
    isOfflineReady,
    needRefresh,
    updateServiceWorker: handleUpdate,
    close,
  }
}
```

**Step 4: Run test to verify it passes**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm vitest run src/composables/__tests__/usePWA.spec.ts
```

Expected: All tests pass

**Step 5: Commit**

```bash
git add console/src/composables/usePWA.ts console/src/composables/__tests__/usePWA.spec.ts
git commit -m "feat: add usePWA composable with tests"
```

---

## Task 5: Create Submission Queue Utility

**Files:**
- Create: `console/src/utils/submitQueue.ts`
- Create: `console/src/utils/__tests__/submitQueue.spec.ts`

**Step 1: Write the failing test first**

Create file: `console/src/utils/__tests__/submitQueue.spec.ts`

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  initSubmitQueue,
  addToQueue,
  getQueue,
  removeFromQueue,
  clearQueue,
  getQueueLength,
  type QueuedSubmission,
} from '../submitQueue'

describe('submitQueue', () => {
  beforeEach(async () => {
    // Clear the queue before each test
    await clearQueue()
  })

  afterEach(async () => {
    // Clean up after each test
    await clearQueue()
  })

  describe('initSubmitQueue', () => {
    it('should initialize without error', async () => {
      await expect(initSubmitQueue()).resolves.not.toThrow()
    })
  })

  describe('addToQueue', () => {
    it('should add a submission to the queue', async () => {
      const submission: Omit<QueuedSubmission, 'id' | 'queuedAt'> = {
        problemId: 'test-problem',
        language: 'typescript',
        code: 'console.log("hello")',
      }

      const id = await addToQueue(submission)
      expect(id).toBeDefined()
      expect(typeof id).toBe('string')
    })

    it('should return the queued submission with id and timestamp', async () => {
      const submission: Omit<QueuedSubmission, 'id' | 'queuedAt'> = {
        problemId: 'test-problem',
        language: 'typescript',
        code: 'console.log("hello")',
      }

      await addToQueue(submission)
      const queue = await getQueue()

      expect(queue).toHaveLength(1)
      expect(queue[0].problemId).toBe('test-problem')
      expect(queue[0].language).toBe('typescript')
      expect(queue[0].code).toBe('console.log("hello")')
      expect(queue[0].id).toBeDefined()
      expect(queue[0].queuedAt).toBeInstanceOf(Date)
    })
  })

  describe('getQueueLength', () => {
    it('should return 0 for empty queue', async () => {
      const length = await getQueueLength()
      expect(length).toBe(0)
    })

    it('should return correct count after adding items', async () => {
      await addToQueue({
        problemId: 'test-1',
        language: 'typescript',
        code: 'code1',
      })
      await addToQueue({
        problemId: 'test-2',
        language: 'python',
        code: 'code2',
      })

      const length = await getQueueLength()
      expect(length).toBe(2)
    })
  })

  describe('removeFromQueue', () => {
    it('should remove a submission by id', async () => {
      const id = await addToQueue({
        problemId: 'test-problem',
        language: 'typescript',
        code: 'code',
      })

      await removeFromQueue(id)
      const queue = await getQueue()

      expect(queue).toHaveLength(0)
    })
  })

  describe('clearQueue', () => {
    it('should remove all submissions', async () => {
      await addToQueue({
        problemId: 'test-1',
        language: 'typescript',
        code: 'code1',
      })
      await addToQueue({
        problemId: 'test-2',
        language: 'python',
        code: 'code2',
      })

      await clearQueue()
      const queue = await getQueue()

      expect(queue).toHaveLength(0)
    })
  })
})
```

**Step 2: Run test to verify it fails**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm vitest run src/utils/__tests__/submitQueue.spec.ts
```

Expected: Test fails because `submitQueue.ts` doesn't exist yet

**Step 3: Create the submitQueue utility**

Create file: `console/src/utils/submitQueue.ts`

```typescript
/**
 * Submission Queue for Offline Support
 *
 * Uses IndexedDB to store code submissions when offline.
 * Submissions are synced when the connection is restored.
 */

import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

const DB_NAME = 'ulticode-offline'
const DB_VERSION = 1
const STORE_NAME = 'submission-queue'

/**
 * Represents a queued code submission
 */
export interface QueuedSubmission {
  /** Unique identifier for this queued submission */
  id: string
  /** Problem ID */
  problemId: string
  /** Programming language */
  language: string
  /** Source code */
  code: string
  /** When this submission was queued */
  queuedAt: Date
}

interface UltiCodeDB extends DBSchema {
  'submission-queue': {
    key: string
    value: QueuedSubmission
    indexes: {
      'by-queuedAt': Date
    }
  }
}

let db: IDBPDatabase<UltiCodeDB> | null = null

/**
 * Initialize the IndexedDB database
 */
export async function initSubmitQueue(): Promise<void> {
  if (db) return

  db = await openDB<UltiCodeDB>(DB_NAME, DB_VERSION, {
    upgrade(database) {
      const store = database.createObjectStore(STORE_NAME, {
        keyPath: 'id',
      })
      store.createIndex('by-queuedAt', 'queuedAt')
    },
  })
}

/**
 * Generate a unique ID for a queued submission
 */
function generateId(): string {
  return `sub_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`
}

/**
 * Add a submission to the queue
 *
 * @param submission - Submission data (without id and queuedAt)
 * @returns The id of the queued submission
 */
export async function addToQueue(
  submission: Omit<QueuedSubmission, 'id' | 'queuedAt'>,
): Promise<string> {
  await initSubmitQueue()

  const id = generateId()
  const queuedSubmission: QueuedSubmission = {
    ...submission,
    id,
    queuedAt: new Date(),
  }

  await db!.put(STORE_NAME, queuedSubmission)
  return id
}

/**
 * Get all queued submissions
 *
 * @returns Array of queued submissions, oldest first
 */
export async function getQueue(): Promise<QueuedSubmission[]> {
  await initSubmitQueue()

  const submissions = await db!.getAllFromIndex(STORE_NAME, 'by-queuedAt')
  return submissions
}

/**
 * Get the number of queued submissions
 */
export async function getQueueLength(): Promise<number> {
  await initSubmitQueue()

  const count = await db!.count(STORE_NAME)
  return count
}

/**
 * Remove a submission from the queue
 *
 * @param id - The id of the submission to remove
 */
export async function removeFromQueue(id: string): Promise<void> {
  await initSubmitQueue()

  await db!.delete(STORE_NAME, id)
}

/**
 * Clear all submissions from the queue
 */
export async function clearQueue(): Promise<void> {
  await initSubmitQueue()

  await db!.clear(STORE_NAME)
}

/**
 * Process the queue by calling the provided handler for each submission
 *
 * @param handler - Async function to process each submission
 * @returns Number of successfully processed submissions
 */
export async function processQueue(
  handler: (submission: QueuedSubmission) => Promise<boolean>,
): Promise<number> {
  const queue = await getQueue()
  let processed = 0

  for (const submission of queue) {
    try {
      const success = await handler(submission)
      if (success) {
        await removeFromQueue(submission.id)
        processed++
      }
    } catch (error) {
      console.error(`[SubmitQueue] Failed to process submission ${submission.id}:`, error)
      // Keep the submission in the queue for retry
    }
  }

  return processed
}
```

**Step 4: Run test to verify it passes**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm vitest run src/utils/__tests__/submitQueue.spec.ts
```

Expected: All tests pass

**Step 5: Commit**

```bash
git add console/src/utils/submitQueue.ts console/src/utils/__tests__/submitQueue.spec.ts
git commit -m "feat: add IndexedDB-based submission queue for offline support"
```

---

## Task 6: Create PWA Update Prompt Component

**Files:**
- Create: `console/src/components/common/PWAUpdatePrompt.vue`

**Step 1: Create the component**

Create file: `console/src/components/common/PWAUpdatePrompt.vue`

```vue
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { usePWA } from '@/composables/usePWA'
import { RefreshCw, X } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'

const { t } = useI18n()
const { needRefresh, updateServiceWorker, close } = usePWA()
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-transform duration-300"
      enter-from-class="translate-y-full"
      enter-to-class="translate-y-0"
      leave-active-class="transition-transform duration-300"
      leave-from-class="translate-y-0"
      leave-to-class="translate-y-full"
    >
      <div
        v-if="needRefresh"
        class="fixed bottom-4 left-4 right-4 md:left-auto md:right-4 md:w-auto z-50"
        role="alert"
        aria-live="polite"
      >
        <div
          class="bg-card border rounded-lg shadow-lg p-4 flex items-center gap-3 flex-wrap justify-between"
        >
          <div class="flex items-center gap-2">
            <RefreshCw class="size-5 text-primary flex-shrink-0" />
            <span class="text-sm font-medium">
              {{ t('common.pwa.updateAvailable') }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <Button size="sm" @click="updateServiceWorker">
              {{ t('common.pwa.update') }}
            </Button>
            <Button variant="ghost" size="sm" @click="close">
              <X class="size-4" />
              <span class="sr-only">{{ t('common.actions.close') }}</span>
            </Button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
```

**Step 2: Commit**

```bash
git add console/src/components/common/PWAUpdatePrompt.vue
git commit -m "feat: add PWA update prompt component"
```

---

## Task 7: Add i18n Translations for PWA

**Files:**
- Modify: `console/src/i18n/locales/en-US/common.ts`
- Modify: `console/src/i18n/locales/zh-CN/common.ts`

**Step 1: Add English translations**

In `console/src/i18n/locales/en-US/common.ts`, add the `pwa` section after `search`:

```typescript
  // PWA (Progressive Web App)
  pwa: {
    updateAvailable: "A new version is available",
    update: "Update",
    offlineReady: "Ready to work offline",
    installPrompt: "Install UltiCode for a better experience",
    install: "Install",
    queuedSubmissions: "{count} submission(s) pending",
    syncing: "Syncing offline submissions...",
    syncComplete: "Offline submissions synced",
    syncFailed: "Failed to sync some submissions",
  },
} as const;
```

**Step 2: Add Chinese translations**

In `console/src/i18n/locales/zh-CN/common.ts`, add the `pwa` section after `search`:

```typescript
  // PWA (渐进式 Web 应用)
  pwa: {
    updateAvailable: "有新版本可用",
    update: "更新",
    offlineReady: "已准备好离线使用",
    installPrompt: "安装 UltiCode 获得更好的体验",
    install: "安装",
    queuedSubmissions: "{count} 个提交等待同步",
    syncing: "正在同步离线提交...",
    syncComplete: "离线提交已同步",
    syncFailed: "部分提交同步失败",
  },
} as const;
```

**Step 3: Commit**

```bash
git add console/src/i18n/locales/en-US/common.ts console/src/i18n/locales/zh-CN/common.ts
git commit -m "feat: add i18n translations for PWA features"
```

---

## Task 8: Integrate PWA into Main App

**Files:**
- Modify: `console/src/main.ts`
- Modify: `console/src/App.vue`

**Step 1: Update main.ts to import PWA registration**

Modify `console/src/main.ts`:

```typescript
import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./style.css";
import "./assets/markdown.css";
import VueDnDKitPlugin from "@vue-dnd-kit/core";

// Import PWA registration (this registers the service worker)
import "@/pwa-register";

async function bootstrap() {
  const app = createApp(App);

  app.use(createPinia());
  app.use(i18n);
  app.use(router);
  app.use(VueDnDKitPlugin);

  // Set initial document language
  document.documentElement.lang = (
    i18n.global.locale as unknown as { value: string }
  ).value;

  app.mount("#app");
}

bootstrap();
```

**Step 2: Update App.vue to include PWAUpdatePrompt**

Modify `console/src/App.vue`:

```vue
<script setup lang="ts">
import PWAUpdatePrompt from "@/components/common/PWAUpdatePrompt.vue";
</script>

<template>
  <RouterView />
  <PWAUpdatePrompt />
</template>

<style scoped></style>
```

**Step 3: Verify TypeScript compilation**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm type-check
```

Expected: No TypeScript errors

**Step 4: Commit**

```bash
git add console/src/main.ts console/src/App.vue
git commit -m "feat: integrate PWA into main application"
```

---

## Task 9: Create Offline Submission Indicator Component

**Files:**
- Create: `console/src/components/common/OfflineQueueIndicator.vue`

**Step 1: Create the component**

Create file: `console/src/components/common/OfflineQueueIndicator.vue`

```vue
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { getQueueLength, processQueue, type QueuedSubmission } from '@/utils/submitQueue'
import { submitCode } from '@/api/submission'
import { CloudOff, CloudUpload, Loader2 } from 'lucide-vue-next'
import { toast } from 'vue-sonner'

const { t } = useI18n()
const { isOnline } = useNetworkStatus()

const queueLength = ref(0)
const isSyncing = ref(false)
let syncInterval: ReturnType<typeof setInterval> | null = null

/**
 * Update the queue length display
 */
async function updateQueueLength(): Promise<void> {
  queueLength.value = await getQueueLength()
}

/**
 * Sync queued submissions when online
 */
async function syncQueue(): Promise<void> {
  if (!isOnline.value || isSyncing.value) return

  isSyncing.value = true

  try {
    const processed = await processQueue(async (submission: QueuedSubmission) => {
      try {
        await submitCode({
          problemId: submission.problemId,
          language: submission.language,
          code: submission.code,
        })
        return true
      } catch {
        return false
      }
    })

    if (processed > 0) {
      toast.success(t('common.pwa.syncComplete'))
    }

    await updateQueueLength()
  } catch (error) {
    toast.error(t('common.pwa.syncFailed'))
    console.error('[OfflineQueue] Sync error:', error)
  } finally {
    isSyncing.value = false
  }
}

onMounted(() => {
  updateQueueLength()

  // Check for sync opportunity every 30 seconds
  syncInterval = setInterval(() => {
    if (isOnline.value && queueLength.value > 0) {
      syncQueue()
    }
  }, 30000)

  // Also sync when coming back online
  window.addEventListener('online', () => {
    if (queueLength.value > 0) {
      syncQueue()
    }
  })
})

onUnmounted(() => {
  if (syncInterval) {
    clearInterval(syncInterval)
  }
})
</script>

<template>
  <div
    v-if="queueLength > 0"
    class="flex items-center gap-2 text-sm text-muted-foreground"
    role="status"
    aria-live="polite"
  >
    <template v-if="isSyncing">
      <Loader2 class="size-4 animate-spin" />
      <span>{{ t('common.pwa.syncing') }}</span>
    </template>
    <template v-else-if="!isOnline">
      <CloudOff class="size-4" />
      <span>{{ t('common.pwa.queuedSubmissions', { count: queueLength }) }}</span>
    </template>
    <template v-else>
      <CloudUpload class="size-4" />
      <span>{{ t('common.pwa.queuedSubmissions', { count: queueLength }) }}</span>
    </template>
  </div>
</template>
```

**Step 2: Commit**

```bash
git add console/src/components/common/OfflineQueueIndicator.vue
git commit -m "feat: add offline queue indicator component"
```

---

## Task 10: Update BUSINESS_IMPROVEMENT_PLAN.md

**Files:**
- Modify: `BUSINESS_IMPROVEMENT_PLAN.md`

**Step 1: Update the plan to mark offline support as completed**

In `BUSINESS_IMPROVEMENT_PLAN.md`, find the line:
```
- [ ] 离线编辑支持
```

Replace with:
```
- [x] 离线编辑支持 ✅ (2026-03-01)
```

**Step 2: Add implementation details**

In the "本次完成" section for 2026-03-01, add:

```markdown
**本次完成** (2026-03-01 PWA 离线支持):
- [x] Service Worker 离线支持 (vite-plugin-pwa 集成)
- [x] 离线代码编辑 (IndexedDB 队列)
- [x] 更新提示组件 (PWAUpdatePrompt)
- [x] 离线队列指示器 (OfflineQueueIndicator)

**实现位置**:
- `console/vite.config.ts` - PWA 插件配置
- `console/src/pwa-register.ts` - Service Worker 注册
- `console/src/composables/usePWA.ts` - PWA 状态管理
- `console/src/utils/submitQueue.ts` - IndexedDB 提交队列
- `console/src/components/common/PWAUpdatePrompt.vue` - 更新提示
- `console/src/components/common/OfflineQueueIndicator.vue` - 队列状态
```

**Step 3: Commit**

```bash
git add BUSINESS_IMPROVEMENT_PLAN.md
git commit -m "docs: update business plan with PWA offline support completion"
```

---

## Task 11: Run Full Test Suite and Verify

**Step 1: Run all tests**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm test
```

Expected: All tests pass

**Step 2: Run TypeScript check**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm type-check
```

Expected: No TypeScript errors

**Step 3: Run lint**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm lint
```

Expected: No lint errors

**Step 4: Verify build**

Run:
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console && pnpm build
```

Expected: Build succeeds, service worker generated

---

## Summary

| Task | Description | Status |
|------|-------------|--------|
| 1 | Install dependencies (vite-plugin-pwa, idb) | ⬜ |
| 2 | Configure Vite PWA Plugin | ⬜ |
| 3 | Create PWA Registration Module | ⬜ |
| 4 | Create usePWA Composable + Tests | ⬜ |
| 5 | Create Submission Queue Utility + Tests | ⬜ |
| 6 | Create PWA Update Prompt Component | ⬜ |
| 7 | Add i18n Translations for PWA | ⬜ |
| 8 | Integrate PWA into Main App | ⬜ |
| 9 | Create Offline Submission Indicator | ⬜ |
| 10 | Update BUSINESS_IMPROVEMENT_PLAN.md | ⬜ |
| 11 | Run Full Test Suite and Verify | ⬜ |
