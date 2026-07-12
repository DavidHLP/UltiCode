import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useForm } from 'vee-validate'
import ConstraintsEditor from './ConstraintsEditor.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      const messages: Record<string, string> = {
        'problems.descriptionForm.constraintsSection.emptyDescription':
          'No constraints added yet. Constraints describe the limits and rules for the problem (e.g., array length, value ranges).',
        'problems.descriptionForm.constraintsSection.addNew': 'Add new constraint',
        'problems.descriptionForm.constraintsSection.placeholder': 'e.g., 1 <= nums.length <= 10^5',
      }
      return messages[key] || key
    },
    locale: { value: 'en-US' },
  }),
}))

function createWrapper(initialValues?: { constraints?: string[] }) {
  const Wrapper = defineComponent({
    setup() {
      useForm({
        initialValues: {
          constraints: initialValues?.constraints ?? [],
        },
      })
      return {}
    },
    render() {
      return h(ConstraintsEditor, { name: 'constraints' })
    },
  })

  return mount(Wrapper)
}

describe('ConstraintsEditor', () => {
  it('renders empty state when no constraints exist', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('No constraints added yet')
    expect(wrapper.findAll('input')).toHaveLength(0)
  })

  it('renders initial constraints', () => {
    const wrapper = createWrapper({
      constraints: ['1 <= n <= 10^5', '0 <= nums[i] <= 100'],
    })

    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(2)
    expect((inputs[0].element as HTMLInputElement).value).toBe('1 <= n <= 10^5')
    expect((inputs[1].element as HTMLInputElement).value).toBe('0 <= nums[i] <= 100')
  })

  it('adds a new constraint when add button is clicked', async () => {
    const wrapper = createWrapper()

    expect(wrapper.findAll('input')).toHaveLength(0)

    const addButton = wrapper.find('button')
    await addButton.trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(1)
    expect((inputs[0].element as HTMLInputElement).value).toBe('')
  })

  it('removes a constraint when delete button is clicked', async () => {
    const wrapper = createWrapper({
      constraints: ['1 <= n <= 10^5', '0 <= nums[i] <= 100'],
    })

    expect(wrapper.findAll('input')).toHaveLength(2)

    const deleteButtons = wrapper.findAll('button').filter((btn) => !btn.text().includes('Add'))

    await deleteButtons[0].trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(1)
    expect((inputs[0].element as HTMLInputElement).value).toBe('0 <= nums[i] <= 100')
  })

  it('hides empty state after adding a constraint', async () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('No constraints added yet')

    const addButton = wrapper.find('button')
    await addButton.trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('No constraints added yet')
  })

  it('shows add new constraint button', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('Add new constraint')
  })

  it('adds constraint by calling method directly', async () => {
    const wrapper = createWrapper()
    const editor = wrapper.findComponent(ConstraintsEditor)

    expect(wrapper.findAll('input')).toHaveLength(0)

    editor.vm.addConstraint()
    await flushPromises()
    await wrapper.vm.$nextTick()

    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(1)
  })
})
