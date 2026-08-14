/**
 * LoginForm — callback contract + hide-* flags
 *
 * Verifies that the shared form:
 * - Invokes `onSubmit` with the credentials
 * - Redirects to the route's `?redirect` query (or "/") on success
 * - Surfaces thrown error messages
 * - Honours `hideForgot` / `hideSignUp` / `hideOAuth` props
 */
import { describe, it, expect, vi } from "vitest"
import { mount, flushPromises } from "@vue/test-utils"
import { router, routerPush } from "./setup"
import LoginForm from "../components/LoginForm.vue"

describe("LoginForm", () => {
  it("renders username and password fields", () => {
    const wrapper = mount(LoginForm)
    const inputs = wrapper.findAll("input")
    expect(inputs.length).toBeGreaterThanOrEqual(2)
  })

  it("invokes onSubmit with the entered credentials", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(onSubmit).toHaveBeenCalledWith({
      username: "alice",
      password: "hunter2",
    })
  })

  it("redirects to '/' when no ?redirect query is set", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/")
  })

  it("uses fallbackRedirect when no explicit or query redirect is set", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, {
      props: { onSubmit, fallbackRedirect: "/problemset" },
    })
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/problemset")
  })

  it("redirects to redirectAfter prop when set", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, {
      props: { onSubmit, redirectAfter: "/dashboard" },
    })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/dashboard")
  })

  it("redirects to the current route's internal redirect query", async () => {
    await router.push({
      path: "/welcome",
      query: { redirect: "/private?tab=solutions" },
    })
    routerPush.mockClear()

    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(routerPush).toHaveBeenCalledWith("/private?tab=solutions")
    await router.replace("/")
  })

  it("ignores external redirect queries and falls back to the app root", async () => {
    await router.push({
      path: "/welcome",
      query: { redirect: "https://evil.example/phishing" },
    })
    routerPush.mockClear()

    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(routerPush).toHaveBeenCalledWith("/")
    await router.replace("/")
  })

  it("rejects backslash-based external redirect queries", async () => {
    await router.push({
      path: "/welcome",
      query: { redirect: "/\\evil.example/phishing" },
    })
    routerPush.mockClear()

    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(routerPush).toHaveBeenCalledWith("/")
    await router.replace("/")
  })

  it("shows the error message from an axios-style error (response.data.message)", async () => {
    const onSubmit = vi.fn().mockRejectedValue({
      response: { data: { message: "Invalid password" } },
    })
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("wrong")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(wrapper.text()).toContain("Invalid password")
  })

  it("falls back to auth.messages.loginFailed when the error has no message", async () => {
    const onSubmit = vi.fn().mockRejectedValue({})
    const wrapper = mount(LoginForm, { props: { onSubmit } })
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("wrong")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    // i18n mock returns the key as fallback
    expect(wrapper.text()).toContain("auth.messages.loginFailed")
  })

  it("hides the forgot-password link when hideForgot=true", () => {
    const wrapper = mount(LoginForm, { props: { hideForgot: true } })
    expect(wrapper.find(".login-form__forgot").exists()).toBe(false)
  })

  it("shows the forgot-password link by default", () => {
    const wrapper = mount(LoginForm)
    expect(wrapper.find(".login-form__forgot").exists()).toBe(true)
  })

  it("hides the OAuth buttons when hideOAuth=true", () => {
    const wrapper = mount(LoginForm, { props: { hideOAuth: true } })
    expect(wrapper.find(".login-form__oauth-grid").exists()).toBe(false)
  })

  it("shows the OAuth buttons by default", () => {
    const wrapper = mount(LoginForm)
    expect(wrapper.find(".login-form__oauth-grid").exists()).toBe(true)
  })

  it("hides the sign-up link when hideSignUp=true", () => {
    const wrapper = mount(LoginForm, { props: { hideSignUp: true } })
    expect(wrapper.find(".login-form__signup").exists()).toBe(false)
  })

  it("shows the sign-up link by default", () => {
    const wrapper = mount(LoginForm)
    expect(wrapper.find(".login-form__signup").exists()).toBe(true)
  })

  it("works without onSubmit prop (just redirects on form submit)", async () => {
    const wrapper = mount(LoginForm)
    const inputs = wrapper.findAll("input")
    await inputs[0].setValue("alice")
    await inputs[1].setValue("hunter2")
    await wrapper.find("form").trigger("submit")
    await flushPromises()
    expect(routerPush).toHaveBeenCalledWith("/")
  })
})
