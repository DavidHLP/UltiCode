/// <reference types="vite/client" />
/// <reference types="unplugin-icons/types/vue" />

declare module 'markdown-it-footnote'
declare module 'markdown-it-katex'
declare module 'markdown-it-link-attributes'
declare module 'markdown-it-task-lists'

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_API_USE_MOCK?: 'true' | 'false'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
