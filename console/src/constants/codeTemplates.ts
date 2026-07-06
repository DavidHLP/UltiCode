/**
 * Code templates constants — static data and pure accessors.
 *
 * Extracted from the former `useCodeTemplates` composable, which wrapped
 * immutable static data in pointless `computed()` calls. The data is
 * module-scope and never changes; callers import functions directly.
 *
 * Architecture review candidate #4 — move static data out of composable.
 */

export type SupportedLanguage =
  | "javascript"
  | "typescript"
  | "python"
  | "java"
  | "cpp"
  | "go"
  | "c"

export interface CodeTemplate {
  id: string
  name: string
  description: string
  language: SupportedLanguage
  code: string
  category: "basic" | "algorithm" | "data-structure"
}

export interface TemplateCategory {
  id: string
  label: string
  templates: CodeTemplate[]
}

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = [
  "javascript",
  "typescript",
  "python",
  "java",
  "cpp",
  "go",
  "c",
]

export const CODE_TEMPLATES: CodeTemplate[] = [
  // JavaScript Templates
  {
    id: "js-main",
    name: "Main Function",
    description: "Basic main function with example",
    language: "javascript",
    category: "basic",
    code: `/**
 * @param {any[]} args
 * @return {any}
 */
function main(args) {
  // Your code here
  return null;
}

// Example usage
`,
  },
  {
    id: "js-two-pointers",
    name: "Two Pointers",
    description: "Two pointers technique template",
    language: "javascript",
    category: "algorithm",
    code: `/**
 * Two pointers technique
 * @param {number[]} arr
 * @return {number[]}
 */
function twoPointers(arr) {
  let left = 0;
  let right = arr.length - 1;

  while (left < right) {
    // Process elements at left and right
    left++;
    right--;
  }

  return arr;
}
`,
  },
  {
    id: "js-sliding-window",
    name: "Sliding Window",
    description: "Sliding window technique template",
    language: "javascript",
    category: "algorithm",
    code: `/**
 * Sliding window technique
 * @param {number[]} arr
 * @param {number} k - window size
 * @return {number}
 */
function slidingWindow(arr, k) {
  let maxSum = 0;
  let windowSum = 0;

  // Calculate sum of first window
  for (let i = 0; i < k; i++) {
    windowSum += arr[i];
  }
  maxSum = windowSum;

  // Slide the window
  for (let i = k; i < arr.length; i++) {
    windowSum = windowSum - arr[i - k] + arr[i];
    maxSum = Math.max(maxSum, windowSum);
  }

  return maxSum;
}
`,
  },

  // TypeScript Templates
  {
    id: "ts-main",
    name: "Main Function",
    description: "Basic main function with types",
    language: "typescript",
    category: "basic",
    code: `interface Args {
  // Define your input structure
}

function main(args: Args): unknown {
  // Your code here
  return null;
}

// Example usage
`,
  },
  {
    id: "ts-bfs",
    name: "BFS Template",
    description: "Breadth-first search template",
    language: "typescript",
    category: "algorithm",
    code: `interface TreeNode {
  val: number;
  left: TreeNode | null;
  right: TreeNode | null;
}

function bfs(root: TreeNode | null): number[] {
  if (!root) return [];

  const result: number[] = [];
  const queue: TreeNode[] = [root];

  while (queue.length > 0) {
    const node = queue.shift()!;
    result.push(node.val);

    if (node.left) queue.push(node.left);
    if (node.right) queue.push(node.right);
  }

  return result;
}
`,
  },
  {
    id: "ts-dfs",
    name: "DFS Template",
    description: "Depth-first search template",
    language: "typescript",
    category: "algorithm",
    code: `interface TreeNode {
  val: number;
  left: TreeNode | null;
  right: TreeNode | null;
}

function dfs(root: TreeNode | null): number[] {
  const result: number[] = [];

  function traverse(node: TreeNode | null): void {
    if (!node) return;

    // Pre-order
    result.push(node.val);
    traverse(node.left);
    traverse(node.right);
  }

  traverse(root);
  return result;
}
`,
  },

  // Python Templates
  {
    id: "py-main",
    name: "Main Function",
    description: "Basic main function with example",
    language: "python",
    category: "basic",
    code: `def main(args):
    """
    Main function
    :type args: list
    :rtype: any
    """
    # Your code here
    pass

if __name__ == "__main__":
    print(main([]))
`,
  },
  {
    id: "py-binary-search",
    name: "Binary Search",
    description: "Binary search template",
    language: "python",
    category: "algorithm",
    code: `def binary_search(arr, target):
    """
    Binary search
    :type arr: List[int]
    :type target: int
    :rtype: int
    """
    left, right = 0, len(arr) - 1

    while left <= right:
        mid = left + (right - left) // 2

        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            left = mid + 1
        else:
            right = mid - 1

    return -1
`,
  },
  {
    id: "py-linked-list",
    name: "Linked List Node",
    description: "Linked list node definition",
    language: "python",
    category: "data-structure",
    code: `class ListNode:
    def __init__(self, val=0, next=None):
        self.val = val
        self.next = next

def create_linked_list(arr):
    """Create linked list from array"""
    if not arr:
        return None

    head = ListNode(arr[0])
    current = head

    for val in arr[1:]:
        current.next = ListNode(val)
        current = current.next

    return head
`,
  },

  // Java Templates
  {
    id: "java-main",
    name: "Main Class",
    description: "Basic main class structure",
    language: "java",
    category: "basic",
    code: `public class Solution {
    public static void main(String[] args) {
        // Your code here
        Solution solution = new Solution();
        System.out.println(solution.solve());
    }

    public Object solve() {
        // Your solution here
        return null;
    }
}
`,
  },
  {
    id: "java-dp",
    name: "Dynamic Programming",
    description: "DP template with memoization",
    language: "java",
    category: "algorithm",
    code: `public class Solution {
    private int[] memo;

    public int solve(int n) {
        memo = new int[n + 1];
        return dp(n);
    }

    private int dp(int n) {
        // Base cases
        if (n <= 1) return n;

        // Check memo
        if (memo[n] != 0) return memo[n];

        // Recurrence relation
        memo[n] = dp(n - 1) + dp(n - 2);
        return memo[n];
    }
}
`,
  },

  // C++ Templates
  {
    id: "cpp-main",
    name: "Main Function",
    description: "Basic C++ main function",
    language: "cpp",
    category: "basic",
    code: `#include <iostream>
#include <vector>
#include <string>

using namespace std;

int main() {
    // Your code here

    return 0;
}
`,
  },
  {
    id: "cpp-sorting",
    name: "Custom Sort",
    description: "Sorting with custom comparator",
    language: "cpp",
    category: "algorithm",
    code: `#include <algorithm>
#include <vector>

using namespace std;

void customSort(vector<int>& arr) {
    sort(arr.begin(), arr.end(), [](int a, int b) {
        // Custom comparison logic
        return a < b;  // Ascending order
    });
}
`,
  },

  // Go Templates
  {
    id: "go-main",
    name: "Main Function",
    description: "Basic Go main function",
    language: "go",
    category: "basic",
    code: `package main

import "fmt"

func main() {
    // Your code here
    fmt.Println("Hello, World!")
}
`,
  },
  {
    id: "go-goroutine",
    name: "Goroutine Pattern",
    description: "Basic goroutine with channel",
    language: "go",
    category: "algorithm",
    code: `package main

import "fmt"

func worker(id int, jobs <-chan int, results chan<- int) {
    for j := range jobs {
        // Process job
        results <- j * 2
    }
}

func main() {
    jobs := make(chan int, 100)
    results := make(chan int, 100)

    // Start workers
    for w := 1; w <= 3; w++ {
        go worker(w, jobs, results)
    }

    // Send jobs
    for j := 1; j <= 5; j++ {
        jobs <- j
    }
    close(jobs)

    // Collect results
    for r := 1; r <= 5; r++ {
        fmt.Println(<-results)
    }
}
`,
  },

  // C Templates
  {
    id: "c-main",
    name: "Main Function",
    description: "Basic C main function",
    language: "c",
    category: "basic",
    code: `#include <stdio.h>
#include <stdlib.h>

int main() {
    // Your code here

    return 0;
}
`,
  },
]

/** Group templates into labeled categories for UI rendering. */
export function getTemplateCategories(): TemplateCategory[] {
  return [
    {
      id: "basic",
      label: "Basic",
      templates: CODE_TEMPLATES.filter((t) => t.category === "basic"),
    },
    {
      id: "algorithm",
      label: "Algorithms",
      templates: CODE_TEMPLATES.filter((t) => t.category === "algorithm"),
    },
    {
      id: "data-structure",
      label: "Data Structures",
      templates: CODE_TEMPLATES.filter((t) => t.category === "data-structure"),
    },
  ]
}

export function getTemplatesByLanguage(
  language: SupportedLanguage,
): CodeTemplate[] {
  return CODE_TEMPLATES.filter((t) => t.language === language)
}

export function getTemplatesByCategory(
  category: CodeTemplate["category"],
): CodeTemplate[] {
  return CODE_TEMPLATES.filter((t) => t.category === category)
}

export function getTemplateById(id: string): CodeTemplate | undefined {
  return CODE_TEMPLATES.find((t) => t.id === id)
}

const LANG_MAP: Record<string, SupportedLanguage> = {
  js: "javascript",
  ts: "typescript",
  typescript: "typescript",
  javascript: "javascript",
  python: "python",
  py: "python",
  java: "java",
  cpp: "cpp",
  "c++": "cpp",
  go: "go",
  golang: "go",
  c: "c",
}

/** Normalize a backend language code to a `SupportedLanguage`. */
export function normalizeLanguage(lang: string): SupportedLanguage {
  return LANG_MAP[lang.toLowerCase()] ?? "javascript"
}

/** Get templates for a language code (normalized). */
export function getTemplatesForLanguage(lang: string): CodeTemplate[] {
  return getTemplatesByLanguage(normalizeLanguage(lang))
}

/** Check if any templates exist for a language code. */
export function hasTemplatesForLanguage(lang: string): boolean {
  return getTemplatesForLanguage(lang).length > 0
}
