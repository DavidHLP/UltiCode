import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsHeatmap from './AnalyticsHeatmap.vue'

describe('AnalyticsHeatmap sparse axes', () => {
  it('renders all declared rows and columns while preserving sparse cell data', () => {
    const wrapper = mount(AnalyticsHeatmap, {
      props: {
        data: [{ x: 0, y: 3, value: 8 }],
        rows: Array.from({ length: 4 }, (_, i) => ({ label: `Row ${i}` })),
        columns: Array.from({ length: 6 }, (_, i) => ({ label: `Column ${i}` })),
      },
      global: { mocks: { $t: (key: string) => key } },
    })
    expect(wrapper.findAll('[role="row"]')).toHaveLength(4)
    expect(wrapper.findAll('[role="rowgroup"] > div')).toHaveLength(24)
    expect(wrapper.findAll('[role="gridcell"]')).toHaveLength(1)
    expect(wrapper.find('[role="gridcell"]').attributes('aria-label')).toBe('Row 3, Column 0: 8')
    expect(wrapper.text()).toContain('Column 5')
    wrapper.unmount()
  })

  it('retains cells outside declared axes without truncating the grid', () => {
    const wrapper = mount(AnalyticsHeatmap, {
      props: {
        data: [{ x: 2, y: 1, value: 4 }],
        rows: [{ label: 'Row' }],
        columns: [{ label: 'Column' }],
      },
      global: { mocks: { $t: (key: string) => key } },
    })
    expect(wrapper.findAll('[role="row"]')).toHaveLength(2)
    expect(wrapper.findAll('[role="rowgroup"] > div')).toHaveLength(6)
    expect(wrapper.find('[role="gridcell"]').attributes('aria-label')).toBe('row 2, column 3: 4')
    wrapper.unmount()
  })
})
