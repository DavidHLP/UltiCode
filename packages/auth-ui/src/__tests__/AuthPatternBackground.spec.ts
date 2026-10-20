/**
 * AuthPatternBackground — `spec` prop drives terminal panel output
 *
 * Verifies that each `AuthPatternLine` is rendered as either a
 * prompt (`$ <command>`) or output (verbatim), and that `tone` controls
 * the CSS class so console + management can colour their spec lines
 * consistently.
 */
import { describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import AuthPatternBackground from "../layouts/AuthPatternBackground.vue";
import "./setup";

const spec = [
  {
    prompt: "systemctl status ulticode.service",
    output: { text: "● UltiCode Platform", tone: "success" as const },
  },
  { output: { text: "Active: active (running)", tone: "muted" as const } },
  {
    prompt: "check_db_connection",
    output: { text: "mysql@localhost", tone: "success" as const },
  },
  { output: "Plain string output" },
];

describe("AuthPatternBackground", () => {
  it("renders prompt lines with a leading `$`", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec, prefix: "$" },
    });
    const prompts = wrapper.findAll('[data-slot="card-content"] p');
    expect(prompts[0].text()).toContain("$ systemctl status ulticode.service");
  });

  it("renders output lines verbatim", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec, prefix: "$" },
    });
    expect(wrapper.text()).toContain("● UltiCode Platform");
    expect(wrapper.text()).toContain("Active: active (running)");
  });

  it("applies the success tone class", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec, prefix: "$" },
    });
    expect(wrapper.html()).toContain("text-status-success-mark");
  });

  it("applies the muted tone class", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec, prefix: "$" },
    });
    expect(wrapper.html()).toContain("text-foreground");
  });

  it("accepts plain strings as output (defaults to normal tone)", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Test", spec, prefix: "$" },
    });
    expect(wrapper.text()).toContain("Plain string output");
  });

  it("renders the title with whitespace-pre-line", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "Line1\nLine2" },
    });
    expect(wrapper.find("h2").classes()).toContain("whitespace-pre-line");
  });

  it("renders the subtitle when provided", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", subtitle: "My subtitle" },
    });
    expect(wrapper.find("h2 + p").text()).toBe("My subtitle");
  });

  it("uses custom prefix when set", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", prefix: ">" },
    });
    expect(wrapper.find("h2 span").text()).toBe(">");
  });

  it("does not impose a terminal prompt on the title", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T" },
    });
    expect(wrapper.find("h2 span").exists()).toBe(false);
  });

  it("uses custom windowTitle in the panel header", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T", windowTitle: "system_check.sh", spec },
    });
    expect(wrapper.find('[data-slot="card-title"]').text()).toBe(
      "system_check.sh",
    );
  });

  it("does not present sample runtime status when spec is empty", () => {
    const wrapper = mount(AuthPatternBackground, {
      props: { title: "T" },
    });
    expect(wrapper.find('[data-slot="card"]').exists()).toBe(false);
    expect(wrapper.get("h2").text()).toBe("T");
  });
});
