const problemTagLabelKeys = {
  array: "problem.tags.array",
  "hash-table": "problem.tags.hashTable",
  "linked-list": "problem.tags.linkedList",
  string: "problem.tags.string",
  "sliding-window": "problem.tags.slidingWindow",
  "two-pointers": "problem.tags.twoPointers",
  "binary-search": "problem.tags.binarySearch",
  "divide-and-conquer": "problem.tags.divideAndConquer",
  heap: "problem.tags.heap",
  math: "problem.tags.math",
  recursion: "problem.tags.recursion",
  "problem-category-algorithms": "problem.categories.algorithms",
  "problem-category-database": "problem.categories.database",
  "problem-category-shell": "problem.categories.shell",
  "problem-category-concurrency": "problem.categories.concurrency",
} as const;

export function problemTagLabelKey(tagSlug: string) {
  if (!Object.prototype.hasOwnProperty.call(problemTagLabelKeys, tagSlug)) {
    return undefined;
  }

  return problemTagLabelKeys[tagSlug as keyof typeof problemTagLabelKeys];
}
