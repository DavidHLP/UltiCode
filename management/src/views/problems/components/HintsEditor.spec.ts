import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useForm } from 'vee-validate'
import HintsEditor from './HintsEditor.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      const messages: Record<string, string> = {
        'problems.descriptionForm.hintsSection.empty': 'No hints added yet',
        'problems.descriptionForm.hintsSection.add': 'Add new hint',
      }
      return messages[key] || key
    },
    locale: { value: 'en-US' },
  }),
}))

function createWrapper(initialValues?: { hints?: string[] }) {
  const Wrapper = defineComponent({
    setup() {
      useForm({
        initialValues: {
          hints: initialValues?.hints ?? [],
        },
      })
      return {}
    },
    render() {
      return h(HintsEditor, { name: 'hints' })
    },
  })

  return mount(Wrapper)
}

describe('HintsEditor', () => {
  it('renders empty state when no hints exist', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('No hints added yet')
    expect(wrapper.findAll('textarea')).toHaveLength(0)
  })

  it('renders initial hints', () => {
    const wrapper = createWrapper({
      hints: ['Try using a hash map', 'Consider time complexity'],
    })

    const textareas = wrapper.findAll('textarea')
    expect(textareas).toHaveLength(2)
    expect((textareas[0].element as HTMLTextAreaElement).value).toBe('Try using a hash map')
    expect((textareas[1].element as HTMLTextAreaElement).value).toBe('Consider time complexity')
  })

  it('adds a new hint when add button is clicked', async () => {
    const wrapper = createWrapper()

    expect(wrapper.findAll('textarea')).toHaveLength(0)

    const addButton = wrapper.find('button')
    await addButton.trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAll('textarea')
    expect(textareas).toHaveLength(1)
    expect((textareas[0].element as HTMLTextAreaElement).value).toBe('')
  })

  it('removes a hint when delete button is clicked', async () => {
    const wrapper = createWrapper({
      hints: ['Try using a hash map', 'Consider time complexity'],
    })

    expect(wrapper.findAll('textarea')).toHaveLength(2)

    const deleteButtons = wrapper.findAll('[aria-label="Delete hint"]')

    await deleteButtons[0].trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAll('textarea')
    expect(textareas).toHaveLength(1)
    expect((textareas[0].element as HTMLTextAreaElement).value).toBe('Consider time complexity')
  })

  it('moves a hint up when up button is clicked', async () => {
    const wrapper = createWrapper({
      hints: ['First hint', 'Second hint', 'Third hint'],
    })

    const upButtons = wrapper.findAll('[aria-label="Move hint up"]')
    expect(upButtons).toHaveLength(3)
    await upButtons[1].trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAll('textarea')
    expect(textareas).toHaveLength(3)
    expect((textareas[0].element as HTMLTextAreaElement).value).toBe('Second hint')
    expect((textareas[1].element as HTMLTextAreaElement).value).toBe('First hint')
    expect((textareas[2].element as HTMLTextAreaElement).value).toBe('Third hint')
  })

  it('moves a hint down when down button is clicked', async () => {
    const wrapper = createWrapper({
      hints: ['First hint', 'Second hint', 'Third hint'],
    })

    const downButtons = wrapper.findAll('[aria-label="Move hint down"]')
    expect(downButtons).toHaveLength(3)
    await downButtons[0].trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAll('textarea')
    expect(textareas).toHaveLength(3)
    expect((textareas[0].element as HTMLTextAreaElement).value).toBe('Second hint')
    expect((textareas[1].element as HTMLTextAreaElement).value).toBe('First hint')
    expect((textareas[2].element as HTMLTextAreaElement).value).toBe('Third hint')
  })

  it('hides empty state after adding a hint', async () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('No hints added yet')

    const addButton = wrapper.find('button')
    await addButton.trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('No hints added yet')
  })

  it('shows add new hint button', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('Add new hint')
  })

  it('disables up button on first hint', () => {
    const wrapper = createWrapper({
      hints: ['Only hint'],
    })

    const upButton = wrapper.find('[aria-label="Move hint up"]')
    expect(upButton.exists()).toBe(true)
    expect((upButton.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('disables down button on last hint', () => {
    const wrapper = createWrapper({
      hints: ['Only hint'],
    })

    const downButton = wrapper.find('[aria-label="Move hint down"]')
    expect(downButton.exists()).toBe(true)
    expect((downButton.element as HTMLButtonElement).disabled).toBe(true)
  })
})
