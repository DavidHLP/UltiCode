declare module 'markdown-it-katex' {
  import type MarkdownIt from 'markdown-it'

  interface KatexOptions {
    throwOnError?: boolean
    errorColor?: string
    strict?: boolean | 'ignore' | 'warn' | 'error'
    displayMode?: boolean
    output?: 'html' | 'mathml'
  }

  const katexPlugin: MarkdownIt.PluginWithOptions<KatexOptions>
  export default katexPlugin
}
