/**
 * RegisterForm — callback contract + validation + fieldErrors
 *
 * Verifies the shared form:
 * - Short-circuits when passwords don't match (M2 review fix)
 * - Includes `name` and `email` in payload only when non-empty
 * - Surfaces thrown error messages
 * - Renders per-field errors from `fieldErrors` prop
 * - Honours `showName` and `hideOAuth` props
 */
import { describe, it, expect, vi } from "vitest"
import { mount, flushPromises } from "@vue/test-utils"
import { routerPush } from "./setup"
import RegisterForm from "../components/RegisterForm.vue"

async function fillStandardForm(
  wrapper: ReturnType<typeof mount>,
  overrides: Partial<{
    username: string
    email: string
    password: string
    confirmPassword: string
    name: string
  }> = {},
) {
  // Order matches the template: username, name, email, password, confirm.
  // Default values are aligned with the 5-input default (showName=true).
  const defaults = {
    username: "alice",
    name: "Alice Doe",
    email: "alice@ulticode",
    password: "hunter22",
    confirmPassword: "hunter22",
    ...overrides,
  }
  const inputs = wrapper.findAll("input")
  const keys = ["username", "name", "email", "password", "confirmPassword"] as const
  for (let i = 0; i < inputs.length && i < keys.length; i++) {
    await inputs[i].setValue(defaults[keys[i]])
  }
}

describe("RegisterForm", () => {
  it("renders 5 input fields by default (username, name, email, password, confirm)", () => {
    const wrapper = mount(RegisterForm)
    const inputs = wrapper.findAll("input")
    expect(inputs.length).toBe(5)
  })

  it("renders 4 input fields when showName=false", () => {
    const wrapper = mount(RegisterForm, { props: { showName: false } })
    const inputs = wrapper.findAll("input")
    expect(inputs.length).toBe(4)
  })

  it("renders the optional Name field by default (showName=true)", () => {
    const wrapper = mount(RegisterForm)
    // 5 inputs: username, name, email, password, confirm
    expect(wrapper.findAll("input").length).toBe(5)
  })

  it("hides the Name field when showName=false", () => {
    const wrapper = mount(RegisterForm, { props: { showName: false } })
    expect(wrapper.findAll("input").length).toBe(4)
  })

  it("invokes onSubmit with username + password + email when all filled", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    await fillStandardForm(wrapper)
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(onSubmit).toHaveBeenCalledTimes(1)
    const payload = onSubmit.mock.calls[0][0]
    expect(payload).toMatchObject({
      username: "alice",
      password: "hunter22",
      email: "alice@ulticode",
    })
  })

  it("omits name from the payload when the Name field is empty", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    // fillStandardForm fills Name with default "Alice Doe"; override
    // to empty string to test the empty-name omission path.
    await fillStandardForm(wrapper, { name: "" })
    const inputs = wrapper.findAll("input")
    await inputs[1].setValue("") // explicitly clear the Name input
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    const payload = onSubmit.mock.calls[0][0]
    expect(payload).not.toHaveProperty("name")
  })

  it("includes name in the payload when the Name field is filled", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    await fillStandardForm(wrapper, { name: "Alice Doe" })
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(onSubmit.mock.calls[0][0].name).toBe("Alice Doe")
  })

  it("shows a password-mismatch error and does NOT call onSubmit when passwords differ", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("Alice") // name
    await inputs[2].setValue("alice@ulticode")
    await inputs[3].setValue("hunter22")
    await inputs[4].setValue("DIFFERENT")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(onSubmit).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain("auth.messages.passwordsDoNotMatch")
  })

  it("redirects to redirectAfter on successful submit", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, {
      props: { onSubmit, redirectAfter: "/welcome" },
    })
    await fillStandardForm(wrapper)
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/welcome")
  })

  it("defaults redirectAfter to '/'", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    await fillStandardForm(wrapper)
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/")
  })

  it("surfaces axios-style error messages (response.data.message)", async () => {
    const onSubmit = vi.fn().mockRejectedValue({
      response: { data: { message: "Username already taken" } },
    })
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    await fillStandardForm(wrapper)
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(wrapper.text()).toContain("Username already taken")
  })

  it("falls back to auth.messages.registerFailed when the thrown error has no message", async () => {
    const onSubmit = vi.fn().mockRejectedValue({})
    const wrapper = mount(RegisterForm, { props: { onSubmit } })
    await fillStandardForm(wrapper)
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(wrapper.text()).toContain("auth.messages.registerFailed")
  })

  it("renders per-field errors from the fieldErrors prop", () => {
    const wrapper = mount(RegisterForm, {
      props: { fieldErrors: { email: "Email already in use" } },
    })
    expect(wrapper.text()).toContain("Email already in use")
  })

  it("hides OAuth buttons when hideOAuth=true", () => {
    const wrapper = mount(RegisterForm, { props: { hideOAuth: true } })
    expect(wrapper.find(".register-form__oauth-grid").exists()).toBe(false)
  })

  it("shows OAuth buttons by default", () => {
    const wrapper = mount(RegisterForm)
    expect(wrapper.find(".register-form__oauth-grid").exists()).toBe(true)
  })
})