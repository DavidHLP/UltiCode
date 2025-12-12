// prisma/seed/data/solutions.data.ts
// prisma/seed/data/solutions.data.ts
import { USER_IDS } from './users.data';

const data = {
  solutions: [
    {
      id: 'sol-001',
      problem_id: 1,
      user_id: USER_IDS.YUKI,
      title: 'Hash Map Approach - O(n) Time Complexity',
      content: `# Hash Map Solution

This solution uses a hash map to solve the Two Sum problem in O(n) time.

## Approach

1. Create a hash map to store numbers and their indices
2. Iterate through the array once
3. For each element, check if the complement exists in the hash map
4. If found, return the indices

## Code

\`\`\`python {group="sol-001"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`java {group="sol-001"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        return new int[0];
    }
}
\`\`\`
\`\`\`cpp {group="sol-001"}
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        unordered_map<int, int> seen;
        for (int i = 0; i < nums.size(); ++i) {
            int complement = target - nums[i];
            if (seen.count(complement)) {
                return {seen[complement], i};
            }
            seen[nums[i]] = i;
        }
        return {};
    }
};
\`\`\`

## Complexity Analysis

- **Time Complexity**: O(n) - Single pass through the array
- **Space Complexity**: O(n) - Hash map storage

## Why This Works

By storing each number as we iterate, we can check for complements in constant time, making this solution optimal.`,
      summary:
        'Efficient O(n) solution using hash map to find two numbers that add up to target',
      language: 'Python',
      tags: ['Hash Table', 'Array', 'Two Pointers'],
      views: 1250,
      likes: 340,
      dislikes: 12,
    },
    {
      id: 'sol-002',
      problem_id: 1,
      user_id: USER_IDS.ALEX,
      title: 'Brute Force Solution - Easy to Understand',
      content: `# Brute Force Approach

A straightforward solution checking all pairs.

## Approach

Check every possible pair of numbers to see if they sum to the target.

\`\`\`javascript {group="sol-002"}
function twoSum(nums, target) {
    for (let i = 0; i < nums.length; i++) {
        for (let j = i + 1; j < nums.length; j++) {
            if (nums[i] + nums[j] === target) {
                return [i, j];
            }
        }
    }
    return [];
}
\`\`\`
\`\`\`python {group="sol-002"}
def twoSum(nums, target):
    for i in range(len(nums)):
        for j in range(i + 1, len(nums)):
            if nums[i] + nums[j] == target:
                return [i, j]
    return []
\`\`\`
\`\`\`java {group="sol-002"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[] { i, j };
                }
            }
        }
        return new int[0];
    }
}
\`\`\`

## Complexity

- **Time**: O(n²)
- **Space**: O(1)

Not optimal but easy to understand!`,
      summary:
        'Simple brute force approach checking all pairs - good for beginners',
      language: 'JavaScript',
      tags: ['Array', 'Brute Force'],
      views: 850,
      likes: 125,
      dislikes: 35,
    },
    {
      id: 'sol-003',
      problem_id: 1,
      user_id: USER_IDS.CHEN,
      title: 'C++ STL unordered_map Solution',
      content: `# C++ Solution with STL

Using C++ STL for a clean and efficient solution.

\`\`\`cpp {group="sol-003"}
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        unordered_map<int, int> seen;
        for (int i = 0; i < nums.size(); i++) {
            int complement = target - nums[i];
            if (seen.find(complement) != seen.end()) {
                return {seen[complement], i};
            }
            seen[nums[i]] = i;
        }
        return {};
    }
};
\`\`\`
\`\`\`python {group="sol-003"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`java {group="sol-003"}
class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        return new int[0];
    }
}
\`\`\`

Fast and memory efficient with C++ STL!`,
      summary: 'C++ implementation using unordered_map for O(n) solution',
      language: 'C++',
      tags: ['Hash Table', 'C++', 'STL'],
      views: 620,
      likes: 89,
      dislikes: 5,
    },
    {
      id: 'sol-004',
      problem_id: 1,
      user_id: USER_IDS.TOURIST,
      title: 'Java HashMap Implementation',
      content: `# Java Solution

\`\`\`java {group="sol-004"}
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) {
            return new int[] { map.get(complement), i };
        }
        map.put(nums[i], i);
    }
    throw new IllegalArgumentException("No solution");
}
\`\`\`
\`\`\`python {group="sol-004"}
def twoSum(nums, target):
    seen = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return [seen[complement], i]
        seen[num] = i
    return []
\`\`\`
\`\`\`cpp {group="sol-004"}
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        unordered_map<int, int> seen;
        for (int i = 0; i < nums.size(); ++i) {
            int complement = target - nums[i];
            if (seen.count(complement)) {
                return {seen[complement], i};
            }
            seen[nums[i]] = i;
        }
        return {};
    }
};
\`\`\``,
      summary: 'Clean Java implementation with HashMap',
      language: 'Java',
      tags: ['Hash Table', 'Java'],
      views: 480,
      likes: 67,
      dislikes: 3,
    },
  ],
  comments: [
    {
      id: 'comment-001',
      solution_id: 'sol-001',
      parent_id: null,
      user_id: USER_IDS.MAX,
      content: 'Great explanation! This helped me understand hash maps better.',
      likes: 25,
    },
    {
      id: 'comment-002',
      solution_id: 'sol-001',
      parent_id: 'comment-001',
      user_id: USER_IDS.YUKI,
      content: 'Thanks! Glad it was helpful 😊',
      likes: 8,
    },
    {
      id: 'comment-003',
      solution_id: 'sol-001',
      parent_id: null,
      user_id: USER_IDS.SARA,
      content: 'What if there are duplicate numbers in the array?',
      likes: 15,
    },
    {
      id: 'comment-004',
      solution_id: 'sol-001',
      parent_id: 'comment-003',
      user_id: USER_IDS.YUKI,
      content:
        "Good question! The hash map will overwrite the previous index, but that's fine since we only need to find one valid pair.",
      likes: 20,
    },
    {
      id: 'comment-005',
      solution_id: 'sol-002',
      parent_id: null,
      user_id: USER_IDS.LILY,
      content: 'This is a good starting point for beginners!',
      likes: 12,
    },
    {
      id: 'comment-006',
      solution_id: 'sol-003',
      parent_id: null,
      user_id: USER_IDS.DAVID,
      content: 'Love the C++ STL approach, very clean!',
      likes: 7,
    },
  ],
  votes: [
    // Votes for sol-001 (340 upvotes, 12 downvotes)
    { solution_id: 'sol-001', user_id: USER_IDS.MAX, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.SARA, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.TOM, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.LILY, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.DAVID, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.EMMA, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.KEVIN, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.TOURIST, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.JIANGLY, vote_type: 1 },
    { solution_id: 'sol-001', user_id: USER_IDS.BENQ, vote_type: 1 },

    // Votes for sol-002 (125 upvotes, 35 downvotes)
    { solution_id: 'sol-002', user_id: USER_IDS.LILY, vote_type: 1 },
    { solution_id: 'sol-002', user_id: USER_IDS.EMMA, vote_type: 1 },
    { solution_id: 'sol-002', user_id: USER_IDS.YUKI, vote_type: -1 },
    { solution_id: 'sol-003', user_id: USER_IDS.KEVIN, vote_type: 1 },

    // Votes for sol-004 (67 upvotes, 3 downvotes)
    { solution_id: 'sol-004', user_id: USER_IDS.SARA, vote_type: 1 },
    { solution_id: 'sol-004', user_id: USER_IDS.TOM, vote_type: 1 },
  ],
} as const;

export default data;
