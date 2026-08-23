import { globalIgnores } from 'eslint/config'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

// To allow more languages other than `ts` in `.vue` files, uncomment the following lines:
// import { configureVueProject } from '@vue/eslint-config-typescript'
// configureVueProject({ scriptLangs: ['ts', 'tsx'] })
// More info at https://github.com/vuejs/eslint-config-typescript/#advanced-setup

export default defineConfigWithVueTs(
  {
    files: ['**/*.{ts,mts,tsx,vue}'],
  },

  globalIgnores([
    '**/dist/**',
    '**/dist-ssr/**',
    '**/coverage/**',
    // Vendored third-party bundle and the verbatim-ported landing experience
    // (see src/views/landing/REPLICA_CHECKLIST.md) — kept byte-faithful to the
    // extracted pre-build sources; not held to app lint rules.
    'src/views/landing/vendor/**',
    'src/views/landing/experience/**',
    // Static public assets, including vendored third-party decoders
    // (draco_decoder.js) — never held to app lint rules.
    'public/**',
  ]),

  pluginVue.configs['flat/essential'],
  {
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', {
        // Underscore prefix marks intentionally unused args/vars.
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
      }],
      'vue/multi-word-component-names': ['error', {
        ignores: [
          'Accordion',
          'Alert',
          'Avatar',
          'Badge',
          'Breadcrumb',
          'Button',
          'Calendar',
          'Card',
          'Carousel',
          'Checkbox',
          'Collapsible',
          'Combobox',
          'Command',
          'Dialog',
          'Drawer',
          'Empty',
          'Field',
          'Input',
          'Item',
          'Kbd',
          'Label',
          'Menubar',
          'Pagination',
          'Panel',
          'Popover',
          'Select',
          'Separator',
          'Sheet',
          'Sidebar',
          'Skeleton',
          'Slider',
          'Sonner',
          'Spinner',
          'Stepper',
          'Switch',
          'Table',
          'Tabs',
          'Textarea',
          'Toggle',
          'Tooltip'
        ]
      }]
    }
  },
  vueTsConfigs.recommended,
  skipFormatting,
)