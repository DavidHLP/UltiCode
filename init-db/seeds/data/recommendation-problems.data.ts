/**
 * Recommendation system training data - Problems
 *
 * These problems are designed to test the recommendation algorithm.
 * They cover different tags and difficulty levels to create meaningful
 * user behavior patterns.
 */

// New problem IDs (starting from 100 to avoid conflicts with existing problems)
export const REC_PROBLEM_IDS = {
  // Array problems
  TWO_SUM_II: 100,
  THREE_SUM: 101,
  CONTAINER_WITH_MOST_WATER: 102,
  PRODUCT_OF_ARRAY_EXCEPT_SELF: 103,
  FIND_MINIMUM_ROTATED: 104,
  SEARCH_ROTATED: 105,

  // String problems
  VALID_ANAGRAM: 106,
  GROUP_ANAGRAMS: 107,
  LONGEST_PALINDROME: 108,
  VALID_PALINDROME: 109,

  // Dynamic Programming problems
  CLIMBING_STAIRS: 110,
  COIN_CHANGE: 111,
  EDIT_DISTANCE: 112,
  LONGEST_COMMON_SUBSEQUENCE: 113,
  MAX_SUBARRAY: 114,
  REGEX_MATCHING: 115,

  // Tree problems
  BINARY_TREE_INORDER: 116,
  VALIDATE_BST: 117,
  BINARY_TREE_LEVEL_ORDER: 118,
  SERIALIZE_TREE: 119,
  MAX_DEPTH_TREE: 120,

  // Graph problems
  CLONE_GRAPH: 121,
  COURSE_SCHEDULE: 122,
  WORD_LADDER: 123,
  PACIFIC_ATLANTIC: 124,

  // Stack problems
  VALID_PARENTHESES: 125,
  MIN_STACK: 126,
  EVALUATE_RPN: 127,

  // Linked List problems
  REVERSE_LIST: 128,
  MERGE_TWO_LISTS: 129,
  MERGE_K_LISTS: 130,
  LRU_CACHE: 131,

  // Heap problems
  TOP_K_FREQUENT: 132,
  KTH_LARGEST: 133,

  // Binary Search problems
  BINARY_SEARCH: 134,
  SEARCH_INSERT: 135,

  // Two Pointers
  TRAPPING_RAIN_WATER: 136,
  THREE_SUM_CLOSEST: 137,
} as const;

export const REC_PROBLEMS = [
  // ============ Array Problems ============
  {
    id: REC_PROBLEM_IDS.TWO_SUM_II,
    slug: 'two-sum-ii-input-array-is-sorted',
    title: 'Two Sum II - Input Array Is Sorted',
    difficulty: 'Easy',
    acceptance_rate: 60.5,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.THREE_SUM,
    slug: '3sum',
    title: '3Sum',
    difficulty: 'Medium',
    acceptance_rate: 35.2,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER,
    slug: 'container-with-most-water',
    title: 'Container With Most Water',
    difficulty: 'Medium',
    acceptance_rate: 54.3,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.PRODUCT_OF_ARRAY_EXCEPT_SELF,
    slug: 'product-of-array-except-self',
    title: 'Product of Array Except Self',
    difficulty: 'Medium',
    acceptance_rate: 64.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.FIND_MINIMUM_ROTATED,
    slug: 'find-minimum-in-rotated-sorted-array',
    title: 'Find Minimum in Rotated Sorted Array',
    difficulty: 'Medium',
    acceptance_rate: 48.9,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.SEARCH_ROTATED,
    slug: 'search-in-rotated-sorted-array',
    title: 'Search in Rotated Sorted Array',
    difficulty: 'Medium',
    acceptance_rate: 39.6,
    is_premium: false,
    has_solution: true,
  },

  // ============ String Problems ============
  {
    id: REC_PROBLEM_IDS.VALID_ANAGRAM,
    slug: 'valid-anagram',
    title: 'Valid Anagram',
    difficulty: 'Easy',
    acceptance_rate: 63.4,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.GROUP_ANAGRAMS,
    slug: 'group-anagrams',
    title: 'Group Anagrams',
    difficulty: 'Medium',
    acceptance_rate: 68.1,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.LONGEST_PALINDROME,
    slug: 'longest-palindromic-substring',
    title: 'Longest Palindromic Substring',
    difficulty: 'Medium',
    acceptance_rate: 32.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.VALID_PALINDROME,
    slug: 'valid-palindrome',
    title: 'Valid Palindrome',
    difficulty: 'Easy',
    acceptance_rate: 50.2,
    is_premium: false,
    has_solution: true,
  },

  // ============ Dynamic Programming Problems ============
  {
    id: REC_PROBLEM_IDS.CLIMBING_STAIRS,
    slug: 'climbing-stairs',
    title: 'Climbing Stairs',
    difficulty: 'Easy',
    acceptance_rate: 51.3,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.COIN_CHANGE,
    slug: 'coin-change',
    title: 'Coin Change',
    difficulty: 'Medium',
    acceptance_rate: 42.5,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.EDIT_DISTANCE,
    slug: 'edit-distance',
    title: 'Edit Distance',
    difficulty: 'Hard',
    acceptance_rate: 52.9,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.LONGEST_COMMON_SUBSEQUENCE,
    slug: 'longest-common-subsequence',
    title: 'Longest Common Subsequence',
    difficulty: 'Medium',
    acceptance_rate: 58.2,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.MAX_SUBARRAY,
    slug: 'maximum-subarray',
    title: 'Maximum Subarray',
    difficulty: 'Medium',
    acceptance_rate: 49.7,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.REGEX_MATCHING,
    slug: 'regular-expression-matching',
    title: 'Regular Expression Matching',
    difficulty: 'Hard',
    acceptance_rate: 29.1,
    is_premium: false,
    has_solution: true,
  },

  // ============ Tree Problems ============
  {
    id: REC_PROBLEM_IDS.BINARY_TREE_INORDER,
    slug: 'binary-tree-inorder-traversal',
    title: 'Binary Tree Inorder Traversal',
    difficulty: 'Easy',
    acceptance_rate: 72.4,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.VALIDATE_BST,
    slug: 'validate-binary-search-tree',
    title: 'Validate Binary Search Tree',
    difficulty: 'Medium',
    acceptance_rate: 32.6,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.BINARY_TREE_LEVEL_ORDER,
    slug: 'binary-tree-level-order-traversal',
    title: 'Binary Tree Level Order Traversal',
    difficulty: 'Medium',
    acceptance_rate: 65.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.SERIALIZE_TREE,
    slug: 'serialize-and-deserialize-binary-tree',
    title: 'Serialize and Deserialize Binary Tree',
    difficulty: 'Hard',
    acceptance_rate: 55.4,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.MAX_DEPTH_TREE,
    slug: 'maximum-depth-of-binary-tree',
    title: 'Maximum Depth of Binary Tree',
    difficulty: 'Easy',
    acceptance_rate: 74.8,
    is_premium: false,
    has_solution: true,
  },

  // ============ Graph Problems ============
  {
    id: REC_PROBLEM_IDS.CLONE_GRAPH,
    slug: 'clone-graph',
    title: 'Clone Graph',
    difficulty: 'Medium',
    acceptance_rate: 42.3,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.COURSE_SCHEDULE,
    slug: 'course-schedule',
    title: 'Course Schedule',
    difficulty: 'Medium',
    acceptance_rate: 46.1,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.WORD_LADDER,
    slug: 'word-ladder',
    title: 'Word Ladder',
    difficulty: 'Hard',
    acceptance_rate: 35.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.PACIFIC_ATLANTIC,
    slug: 'pacific-atlantic-water-flow',
    title: 'Pacific Atlantic Water Flow',
    difficulty: 'Medium',
    acceptance_rate: 51.2,
    is_premium: false,
    has_solution: true,
  },

  // ============ Stack Problems ============
  {
    id: REC_PROBLEM_IDS.VALID_PARENTHESES,
    slug: 'valid-parentheses',
    title: 'Valid Parentheses',
    difficulty: 'Easy',
    acceptance_rate: 40.3,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.MIN_STACK,
    slug: 'min-stack',
    title: 'Min Stack',
    difficulty: 'Medium',
    acceptance_rate: 50.6,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.EVALUATE_RPN,
    slug: 'evaluate-reverse-polish-notation',
    title: 'Evaluate Reverse Polish Notation',
    difficulty: 'Medium',
    acceptance_rate: 52.4,
    is_premium: false,
    has_solution: true,
  },

  // ============ Linked List Problems ============
  {
    id: REC_PROBLEM_IDS.REVERSE_LIST,
    slug: 'reverse-linked-list',
    title: 'Reverse Linked List',
    difficulty: 'Easy',
    acceptance_rate: 71.2,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.MERGE_TWO_LISTS,
    slug: 'merge-two-sorted-lists',
    title: 'Merge Two Sorted Lists',
    difficulty: 'Easy',
    acceptance_rate: 61.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.MERGE_K_LISTS,
    slug: 'merge-k-sorted-lists',
    title: 'Merge k Sorted Lists',
    difficulty: 'Hard',
    acceptance_rate: 48.7,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.LRU_CACHE,
    slug: 'lru-cache',
    title: 'LRU Cache',
    difficulty: 'Medium',
    acceptance_rate: 43.9,
    is_premium: false,
    has_solution: true,
  },

  // ============ Heap Problems ============
  {
    id: REC_PROBLEM_IDS.TOP_K_FREQUENT,
    slug: 'top-k-frequent-elements',
    title: 'Top K Frequent Elements',
    difficulty: 'Medium',
    acceptance_rate: 62.3,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.KTH_LARGEST,
    slug: 'kth-largest-element-in-an-array',
    title: 'Kth Largest Element in an Array',
    difficulty: 'Medium',
    acceptance_rate: 61.5,
    is_premium: false,
    has_solution: true,
  },

  // ============ Binary Search Problems ============
  {
    id: REC_PROBLEM_IDS.BINARY_SEARCH,
    slug: 'binary-search',
    title: 'Binary Search',
    difficulty: 'Easy',
    acceptance_rate: 57.8,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.SEARCH_INSERT,
    slug: 'search-insert-position',
    title: 'Search Insert Position',
    difficulty: 'Easy',
    acceptance_rate: 43.7,
    is_premium: false,
    has_solution: true,
  },

  // ============ Two Pointers Problems ============
  {
    id: REC_PROBLEM_IDS.TRAPPING_RAIN_WATER,
    slug: 'trapping-rain-water',
    title: 'Trapping Rain Water',
    difficulty: 'Hard',
    acceptance_rate: 57.9,
    is_premium: false,
    has_solution: true,
  },
  {
    id: REC_PROBLEM_IDS.THREE_SUM_CLOSEST,
    slug: '3sum-closest',
    title: '3Sum Closest',
    difficulty: 'Medium',
    acceptance_rate: 45.8,
    is_premium: false,
    has_solution: true,
  },
] as const;

// Tag relations for recommendation problems
export const REC_PROBLEM_TAG_RELATIONS = [
  // Array problems
  { problem_id: REC_PROBLEM_IDS.TWO_SUM_II, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.TWO_SUM_II, tag_id: 'two-pointers' },
  { problem_id: REC_PROBLEM_IDS.TWO_SUM_II, tag_id: 'binary-search' },

  { problem_id: REC_PROBLEM_IDS.THREE_SUM, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.THREE_SUM, tag_id: 'sorting' },
  { problem_id: REC_PROBLEM_IDS.THREE_SUM, tag_id: 'two-pointers' },

  { problem_id: REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER, tag_id: 'array' },
  {
    problem_id: REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER,
    tag_id: 'two-pointers',
  },
  { problem_id: REC_PROBLEM_IDS.CONTAINER_WITH_MOST_WATER, tag_id: 'greedy' },

  { problem_id: REC_PROBLEM_IDS.PRODUCT_OF_ARRAY_EXCEPT_SELF, tag_id: 'array' },
  {
    problem_id: REC_PROBLEM_IDS.PRODUCT_OF_ARRAY_EXCEPT_SELF,
    tag_id: 'prefix-sum',
  },

  { problem_id: REC_PROBLEM_IDS.FIND_MINIMUM_ROTATED, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.FIND_MINIMUM_ROTATED, tag_id: 'binary-search' },

  { problem_id: REC_PROBLEM_IDS.SEARCH_ROTATED, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.SEARCH_ROTATED, tag_id: 'binary-search' },

  // String problems
  { problem_id: REC_PROBLEM_IDS.VALID_ANAGRAM, tag_id: 'string' },
  { problem_id: REC_PROBLEM_IDS.VALID_ANAGRAM, tag_id: 'hash-table' },
  { problem_id: REC_PROBLEM_IDS.VALID_ANAGRAM, tag_id: 'sorting' },

  { problem_id: REC_PROBLEM_IDS.GROUP_ANAGRAMS, tag_id: 'string' },
  { problem_id: REC_PROBLEM_IDS.GROUP_ANAGRAMS, tag_id: 'hash-table' },
  { problem_id: REC_PROBLEM_IDS.GROUP_ANAGRAMS, tag_id: 'sorting' },

  { problem_id: REC_PROBLEM_IDS.LONGEST_PALINDROME, tag_id: 'string' },
  {
    problem_id: REC_PROBLEM_IDS.LONGEST_PALINDROME,
    tag_id: 'dynamic-programming',
  },

  { problem_id: REC_PROBLEM_IDS.VALID_PALINDROME, tag_id: 'string' },
  { problem_id: REC_PROBLEM_IDS.VALID_PALINDROME, tag_id: 'two-pointers' },

  // Dynamic Programming problems
  {
    problem_id: REC_PROBLEM_IDS.CLIMBING_STAIRS,
    tag_id: 'dynamic-programming',
  },
  { problem_id: REC_PROBLEM_IDS.CLIMBING_STAIRS, tag_id: 'math' },
  { problem_id: REC_PROBLEM_IDS.CLIMBING_STAIRS, tag_id: 'memoization' },

  { problem_id: REC_PROBLEM_IDS.COIN_CHANGE, tag_id: 'dynamic-programming' },
  { problem_id: REC_PROBLEM_IDS.COIN_CHANGE, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.COIN_CHANGE, tag_id: 'array' },

  { problem_id: REC_PROBLEM_IDS.EDIT_DISTANCE, tag_id: 'dynamic-programming' },
  { problem_id: REC_PROBLEM_IDS.EDIT_DISTANCE, tag_id: 'string' },

  {
    problem_id: REC_PROBLEM_IDS.LONGEST_COMMON_SUBSEQUENCE,
    tag_id: 'dynamic-programming',
  },
  { problem_id: REC_PROBLEM_IDS.LONGEST_COMMON_SUBSEQUENCE, tag_id: 'string' },

  { problem_id: REC_PROBLEM_IDS.MAX_SUBARRAY, tag_id: 'dynamic-programming' },
  { problem_id: REC_PROBLEM_IDS.MAX_SUBARRAY, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.MAX_SUBARRAY, tag_id: 'divide-and-conquer' },

  { problem_id: REC_PROBLEM_IDS.REGEX_MATCHING, tag_id: 'dynamic-programming' },
  { problem_id: REC_PROBLEM_IDS.REGEX_MATCHING, tag_id: 'string' },
  { problem_id: REC_PROBLEM_IDS.REGEX_MATCHING, tag_id: 'recursion' },

  // Tree problems
  { problem_id: REC_PROBLEM_IDS.BINARY_TREE_INORDER, tag_id: 'tree' },
  { problem_id: REC_PROBLEM_IDS.BINARY_TREE_INORDER, tag_id: 'stack' },
  { problem_id: REC_PROBLEM_IDS.BINARY_TREE_INORDER, tag_id: 'dfs' },

  { problem_id: REC_PROBLEM_IDS.VALIDATE_BST, tag_id: 'tree' },
  { problem_id: REC_PROBLEM_IDS.VALIDATE_BST, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.VALIDATE_BST, tag_id: 'binary-search-tree' },

  { problem_id: REC_PROBLEM_IDS.BINARY_TREE_LEVEL_ORDER, tag_id: 'tree' },
  { problem_id: REC_PROBLEM_IDS.BINARY_TREE_LEVEL_ORDER, tag_id: 'bfs' },

  { problem_id: REC_PROBLEM_IDS.SERIALIZE_TREE, tag_id: 'tree' },
  { problem_id: REC_PROBLEM_IDS.SERIALIZE_TREE, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.SERIALIZE_TREE, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.SERIALIZE_TREE, tag_id: 'string' },

  { problem_id: REC_PROBLEM_IDS.MAX_DEPTH_TREE, tag_id: 'tree' },
  { problem_id: REC_PROBLEM_IDS.MAX_DEPTH_TREE, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.MAX_DEPTH_TREE, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.MAX_DEPTH_TREE, tag_id: 'recursion' },

  // Graph problems
  { problem_id: REC_PROBLEM_IDS.CLONE_GRAPH, tag_id: 'graph' },
  { problem_id: REC_PROBLEM_IDS.CLONE_GRAPH, tag_id: 'hash-table' },
  { problem_id: REC_PROBLEM_IDS.CLONE_GRAPH, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.CLONE_GRAPH, tag_id: 'bfs' },

  { problem_id: REC_PROBLEM_IDS.COURSE_SCHEDULE, tag_id: 'graph' },
  { problem_id: REC_PROBLEM_IDS.COURSE_SCHEDULE, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.COURSE_SCHEDULE, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.COURSE_SCHEDULE, tag_id: 'topological-sort' },

  { problem_id: REC_PROBLEM_IDS.WORD_LADDER, tag_id: 'graph' },
  { problem_id: REC_PROBLEM_IDS.WORD_LADDER, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.WORD_LADDER, tag_id: 'string' },
  { problem_id: REC_PROBLEM_IDS.WORD_LADDER, tag_id: 'hash-table' },

  { problem_id: REC_PROBLEM_IDS.PACIFIC_ATLANTIC, tag_id: 'graph' },
  { problem_id: REC_PROBLEM_IDS.PACIFIC_ATLANTIC, tag_id: 'dfs' },
  { problem_id: REC_PROBLEM_IDS.PACIFIC_ATLANTIC, tag_id: 'bfs' },
  { problem_id: REC_PROBLEM_IDS.PACIFIC_ATLANTIC, tag_id: 'matrix' },

  // Stack problems
  { problem_id: REC_PROBLEM_IDS.VALID_PARENTHESES, tag_id: 'stack' },
  { problem_id: REC_PROBLEM_IDS.VALID_PARENTHESES, tag_id: 'string' },

  { problem_id: REC_PROBLEM_IDS.MIN_STACK, tag_id: 'stack' },
  { problem_id: REC_PROBLEM_IDS.MIN_STACK, tag_id: 'design' },

  { problem_id: REC_PROBLEM_IDS.EVALUATE_RPN, tag_id: 'stack' },
  { problem_id: REC_PROBLEM_IDS.EVALUATE_RPN, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.EVALUATE_RPN, tag_id: 'math' },

  // Linked List problems
  { problem_id: REC_PROBLEM_IDS.REVERSE_LIST, tag_id: 'linked-list' },
  { problem_id: REC_PROBLEM_IDS.REVERSE_LIST, tag_id: 'recursion' },

  { problem_id: REC_PROBLEM_IDS.MERGE_TWO_LISTS, tag_id: 'linked-list' },
  { problem_id: REC_PROBLEM_IDS.MERGE_TWO_LISTS, tag_id: 'recursion' },

  { problem_id: REC_PROBLEM_IDS.MERGE_K_LISTS, tag_id: 'linked-list' },
  { problem_id: REC_PROBLEM_IDS.MERGE_K_LISTS, tag_id: 'heap' },
  { problem_id: REC_PROBLEM_IDS.MERGE_K_LISTS, tag_id: 'divide-and-conquer' },

  { problem_id: REC_PROBLEM_IDS.LRU_CACHE, tag_id: 'linked-list' },
  { problem_id: REC_PROBLEM_IDS.LRU_CACHE, tag_id: 'hash-table' },
  { problem_id: REC_PROBLEM_IDS.LRU_CACHE, tag_id: 'design' },
  { problem_id: REC_PROBLEM_IDS.LRU_CACHE, tag_id: 'doubly-linked-list' },

  // Heap problems
  { problem_id: REC_PROBLEM_IDS.TOP_K_FREQUENT, tag_id: 'heap' },
  { problem_id: REC_PROBLEM_IDS.TOP_K_FREQUENT, tag_id: 'hash-table' },
  { problem_id: REC_PROBLEM_IDS.TOP_K_FREQUENT, tag_id: 'sorting' },
  { problem_id: REC_PROBLEM_IDS.TOP_K_FREQUENT, tag_id: 'array' },

  { problem_id: REC_PROBLEM_IDS.KTH_LARGEST, tag_id: 'heap' },
  { problem_id: REC_PROBLEM_IDS.KTH_LARGEST, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.KTH_LARGEST, tag_id: 'sorting' },
  { problem_id: REC_PROBLEM_IDS.KTH_LARGEST, tag_id: 'divide-and-conquer' },

  // Binary Search problems
  { problem_id: REC_PROBLEM_IDS.BINARY_SEARCH, tag_id: 'binary-search' },
  { problem_id: REC_PROBLEM_IDS.BINARY_SEARCH, tag_id: 'array' },

  { problem_id: REC_PROBLEM_IDS.SEARCH_INSERT, tag_id: 'binary-search' },
  { problem_id: REC_PROBLEM_IDS.SEARCH_INSERT, tag_id: 'array' },

  // Two Pointers problems
  { problem_id: REC_PROBLEM_IDS.TRAPPING_RAIN_WATER, tag_id: 'two-pointers' },
  {
    problem_id: REC_PROBLEM_IDS.TRAPPING_RAIN_WATER,
    tag_id: 'dynamic-programming',
  },
  { problem_id: REC_PROBLEM_IDS.TRAPPING_RAIN_WATER, tag_id: 'stack' },
  {
    problem_id: REC_PROBLEM_IDS.TRAPPING_RAIN_WATER,
    tag_id: 'monotonic-stack',
  },

  { problem_id: REC_PROBLEM_IDS.THREE_SUM_CLOSEST, tag_id: 'array' },
  { problem_id: REC_PROBLEM_IDS.THREE_SUM_CLOSEST, tag_id: 'sorting' },
  { problem_id: REC_PROBLEM_IDS.THREE_SUM_CLOSEST, tag_id: 'two-pointers' },
] as const;

export default {
  problems: REC_PROBLEMS,
  problem_tag_relations: REC_PROBLEM_TAG_RELATIONS,
};
