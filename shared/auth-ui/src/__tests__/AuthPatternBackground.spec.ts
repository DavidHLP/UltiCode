/**
 * AuthPatternBackground — `spec` prop drives terminal panel output
 *
 * Verifies that each `AuthPatternLine` is rendered as either a
 * prompt (`$ <command>`) or output (verbatim), and that `tone` controls
 * the CSS class so console + management can colour their spec lines
 * consistently.
 */
import { describe, it, expect } from "vitest"
import { mount } from "@vue/test-utils"
import AuthPatternBackground from "../layouts/AuthPatternBackground.vue"
import "./setup"

const spec = [
  { prompt: "systemctl status ulticode.service", output: { text: "● UltiCode Platform", tone: "success" as const } },
  { output: { text: "Active: active (running)", tone: "muted" as const } },
  { prompt: "check_db_connection", output: { text: "mysql@localhost", tone: "success" as const } },
  { output: "Plain string output" },
]

describe("AuthPatternBackground", () => {
  it("renders prompt lines with a leading `$`", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec },
    })
    const prompts = wrapper.findAll(".auth-pattern-terminal__content > div")
    expect(prompts[0].text()).toContain("$ systemctl status ulticode.service")
  })

  it("renders output lines verbatim", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec },
    })
    expect(wrapper.text()).toContain("● UltiCode Platform")
    expect(wrapper.text()).toContain("Active: active (running)")
  })

  it("applies the success tone class", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec },
    })
    expect(wrapper.html()).toContain("text-[var(--terminal-green)]")
  })

  it("applies the muted tone class", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec },
    })
    expect(wrapper.html()).toContain("text-[var(--solarized-base01)]")
  })

  it("accepts plain strings as output (defaults to normal tone)", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec },
    })
    expect(wrapper.text()).toContain("Plain string output")
  })

  it("renders the title with whitespace-pre-line", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Line1\nLine2" },
    })
    expect(wrapper.find(".auth-pattern-text__title").classes()).toContain(
      "whitespace-pre-line",
    )
  })

  it("renders the subtitle when provided", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", subtitle: "My subtitle" },
    })
    expect(wrapper.find(".auth-pattern-text__subtitle").text()).toBe(
      "My subtitle",
    )
  })

  it("uses custom prefix when set", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", prefix: ">" },
    })
    expect(wrapper.find(".auth-pattern-text__prefix").text()).toBe(">")
  })

  it("defaults to `$` prefix when not set", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T" },
    })
    expect(wrapper.find(".auth-pattern-text__prefix").text()).toBe("$")
  })

  it("uses custom windowTitle in the panel header", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", windowTitle: "system_check.sh" },
    })
    expect(wrapper.find(".auth-pattern-terminal__title").text()).toBe(
      "system_check.sh",
    )
  })

  it("falls back to systemOnline text when spec is empty", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T" },
    })
    // i18n mock returns key; we only assert it's present
    expect(wrapper.text()).toContain("auth.layout.systemOnline")
  })
})