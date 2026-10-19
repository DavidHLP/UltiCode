import { describe, expect, it } from "vitest";
import { problemTagLabelKey } from "./problemTagLabel";

describe("problemTagLabelKey", () => {
  it.each([
    ["array", "problem.tags.array"],
    ["hash-table", "problem.tags.hashTable"],
    ["linked-list", "problem.tags.linkedList"],
    ["string", "problem.tags.string"],
    ["sliding-window", "problem.tags.slidingWindow"],
    ["two-pointers", "problem.tags.twoPointers"],
    ["binary-search", "problem.tags.binarySearch"],
    ["divide-and-conquer", "problem.tags.divideAndConquer"],
    ["heap", "problem.tags.heap"],
    ["math", "problem.tags.math"],
    ["recursion", "problem.tags.recursion"],
    ["problem-category-algorithms", "problem.categories.algorithms"],
    ["problem-category-database", "problem.categories.database"],
    ["problem-category-shell", "problem.categories.shell"],
    ["problem-category-concurrency", "problem.categories.concurrency"],
  ])("maps %s to %s", (slug, key) => {
    expect(problemTagLabelKey(slug)).toBe(key);
  });

  it.each(["custom-tag", "problem-category-custom", "__proto__"])(
    "leaves unknown slug %s unmapped",
    (slug) => {
      expect(problemTagLabelKey(slug)).toBeUndefined();
    },
  );
});
