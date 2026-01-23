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

  globalIgnores(['**/dist/**', '**/dist-ssr/**', '**/coverage/**']),

  pluginVue.configs['flat/essential'],
  {
    rules: {
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