/**
 * Re-export shim — the canonical `Separator` lives in
 * `@ulticode/design-system` (`shared/design-system/src/components/separator/`).
 * The management's `import { Separator } from '@/components/ui/separator'`
 * contract is preserved so consumers don't change (arch review 2026-07-10,
 * candidate #1).
 */
export { default as Separator } from '@/shared/design-system/src/components/separator/Separator.vue'
export { default } from '@/shared/design-system/src/components/separator/Separator.vue'
