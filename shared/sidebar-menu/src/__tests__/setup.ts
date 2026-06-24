import { config } from '@vue/test-utils'

// shared/sidebar-menu has no vue-router dependency. Stub RouterLink globally so
// <component :is="'router-link'"> renders in jsdom without "Failed to resolve
// component" warnings — this unlocks spec coverage for the `as='link'` / `:to`
// / SidebarParentItem Mode-A production paths. The stub forwards attrs
// (to, class, data-*) to the <a> via fallthrough, so assertions on the
// rendered element keep working.
config.global.stubs = {
  RouterLink: {
    template: '<a><slot /></a>',
  },
}
