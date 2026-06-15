import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

function readSource() {
  return readFileSync(
    resolve(process.cwd(), "src/views/problems/headers/LayoutHeaderLeft.vue"),
    "utf8",
  );
}

describe("LayoutHeaderLeft problemset external link", () => {
  it("renders RouterLink as the direct child of HoverCardTrigger for the open-in-new-tab action", () => {
    const source = readSource();

    // Find the open-in-new-tab HoverCard block.
    const block = source.match(
      /External link button HoverCard[\s\S]*?<\/HoverCard>/,
    );
    expect(block, "open-in-new-tab HoverCard block should exist").toBeTruthy();

    // The HoverCardTrigger must contain RouterLink directly. A wrapper
    // <div> between them used to swallow clicks on the problemset link.
    // An explanatory HTML comment may sit between the opening tag and the
    // RouterLink, so we look for the first RouterLink/div element instead
    // of requiring it to be the very first node.
    const trigger = block![0].match(
      /<HoverCardTrigger[^>]*>([\s\S]*?)<\/HoverCardTrigger>/,
    );
    expect(trigger, "HoverCardTrigger should wrap RouterLink").toBeTruthy();
    expect(trigger![1]).toMatch(/<RouterLink\b/);
    // Strip HTML comments before scanning for an actual <div> element so
    // prose mentions of "<div>" inside explanatory comments do not trip
    // the assertion.
    const triggerWithoutComments = trigger![1].replace(/<!--[\s\S]*?-->/g, "");
    expect(triggerWithoutComments).not.toMatch(/<div[\s>]/);

    // The link targets the problemset route and opens in a new tab.
    expect(block![0]).toContain(":to=\"{ name: 'problemset' }\"");
    expect(block![0]).toContain('target="_blank"');
    expect(block![0]).toContain('rel="noopener noreferrer"');
  });

  it("keeps the link hidden by default and reveals it on group hover", () => {
    const source = readSource();

    const block = source.match(
      /External link button HoverCard[\s\S]*?<\/HoverCard>/,
    );
    expect(block).toBeTruthy();

    // The hidden/visible states must be on the link itself (or the
    // immediate child of HoverCardTrigger) so they still work after
    // removing the wrapper <div>.
    expect(block![0]).toMatch(/<RouterLink[^>]*\bhidden\b/);
    expect(block![0]).toContain("group-hover/nav-back:flex");
  });
});
