/**
 * AuthLayout — prop + slot rendering contract
 *
 * Covers review H1: ensures the layout shell is testable in isolation
 * from the consuming app. Asserts the four consumer-facing props
 * (badge, version, statusText, hidePattern, homeHref) plus both
 * named slots render correctly.
 */
import { describe, it, expect } from "vitest"
import { mount } from "@vue/test-utils"
import AuthLayout from "../layouts/AuthLayout.vue"
import "./setup"

describe("AuthLayout", () => {
  it("renders the default badge 'CODE' when no badge prop is given", () => {
    const wrapper = mount(AuthLayout, {
      slots: { form: '<div class="stub-form" />' },
    })
    expect(wrapper.find(".auth-logo__badge").text()).toBe("CODE")
  })

  it("renders a custom badge via the badge prop", () => {
    const wrapper = mount(AuthLayout, {
      props: { badge: "ADMIN" },
      slots: { form: '<div class="stub-form" />' },
    })
    expect(wrapper.find(".auth-logo__badge").text()).toBe("ADMIN")
  })

  it("renders the version prop in the footer", () => {
    const wrapper = mount(AuthLayout, {
      props: { version: "v3.1.4" },
      slots: { form: '<div class="stub-form" />' },
    })
    expect(wrapper.find(".auth-layout__version").text()).toBe("v3.1.4")
  })

  it("uses the default status text when statusText prop is empty", () => {
    const wrapper = mount(AuthLayout, {
      slots: { form: '<div class="stub-form" />' },
    })
    // i18n mock returns the key as fallback; we don't pin the exact
    // string, but the footer must contain the resolved text.
    expect(wrapper.find(".auth-layout__status").text()).toContain(
      "auth.layout.systemOnline",
    )
  })

  it("overrides status text when statusText prop is set", () => {
    const wrapper = mount(AuthLayout, {
      props: { statusText: "Offline" },
      slots: { form: '<div class="stub-form" />' },
    })
    expect(wrapper.find(".auth-layout__status").text()).toContain("Offline")
  })

  it("renders the form slot", () => {
    const wrapper = mount(AuthLayout, {
      slots: { form: '<p class="my-form">Hello</p>' },
    })
    expect(wrapper.find(".my-form").exists()).toBe(true)
    expect(wrapper.text()).toContain("Hello")
  })

  it("renders the pattern slot by default", () => {
    const wrapper = mount(AuthLayout, {
      slots: {
        form: '<div class="stub-form" />',
        pattern: '<div class="my-pattern" />',
      },
    })
    expect(wrapper.find(".my-pattern").exists()).toBe(true)
  })

  it("hides the pattern slot when hidePattern=true", () => {
    const wrapper = mount(AuthLayout, {
      props: { hidePattern: true },
      slots: {
        form: '<div class="stub-form" />',
        pattern: '<div class="my-pattern" />',
      },
    })
    expect(wrapper.find(".my-pattern").exists()).toBe(false)
  })

  it("uses the homeHref prop on the logo link", () => {
    const wrapper = mount(AuthLayout, {
      props: { homeHref: "/landing" },
      slots: { form: '<div class="stub-form" />' },
    })
    const link = wrapper.find(".auth-logo")
    expect(link.attributes("href")).toBe("/landing")
  })

  it("defaults the home link to '/'", () => {
    const wrapper = mount(AuthLayout, {
      slots: { form: '<div class="stub-form" />' },
    })
    expect(wrapper.find(".auth-logo").attributes("href")).toBe("/")
  })
})