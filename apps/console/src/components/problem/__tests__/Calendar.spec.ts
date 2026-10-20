import { CalendarDate } from "@internationalized/date";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { Calendar } from "@ulticode/design-system/ui/calendar";

describe("shared completion calendar", () => {
  it("marks only completed dates and updates when activity changes", async () => {
    const wrapper = mount(Calendar, {
      props: {
        defaultPlaceholder: new CalendarDate(2026, 10, 20),
        completedDates: ["2026-10-20"],
      },
    });
    const completed = () => wrapper.get('[data-value="2026-10-20"]');
    const other = () => wrapper.get('[data-value="2026-10-21"]');
    expect(completed().classes()).toContain("bg-status-warning-surface");
    expect(other().classes()).not.toContain("bg-status-warning-surface");
    await wrapper.setProps({ completedDates: ["2026-10-21"] });
    expect(completed().classes()).not.toContain("bg-status-warning-surface");
    expect(other().classes()).toContain("bg-status-warning-surface");
    expect(wrapper.attributes("completed-dates")).toBeUndefined();
    wrapper.unmount();
  });
});
