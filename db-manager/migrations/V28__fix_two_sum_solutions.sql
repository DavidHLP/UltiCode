SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;

-- V28: Fix Two Sum solution content

UPDATE `solutions` SET `content` = '# 哈希表解法 — O(n) 时间复杂度\n\n## 思路\n\n遍历数组，将每个元素的值和索引存入哈希表。对于当前元素 `nums[i]`，检查 `target - nums[i]` 是否已在哈希表中。如果存在，说明找到了一对解。\n\n关键点：边查边插，不需要先将所有元素存入哈希表。\n\n```typescript\nfunction twoSum(nums: number[], target: number): number[] {\n  const map = new Map<number, number>();\n  for (let i = 0; i < nums.length; i++) {\n    const complement = target - nums[i];\n    if (map.has(complement)) {\n      return [map.get(complement)!, i];\n    }\n    map.set(nums[i], i);\n  }\n  return [];\n}\n```\n\n## 复杂度分析\n\n- **时间复杂度**：O(n)，只需一次遍历，哈希表查找 O(1)\n- **空间复杂度**：O(n)，最坏情况需要存储 n-1 个元素', `summary` = 'O(n) 时间复杂度的哈希表解法，遍历数组时边查边插，一次遍历即可找到目标对。' WHERE `id` = 'sol-001';

UPDATE `solutions` SET `content` = '# 暴力枚举解法\n\n## 思路\n\n最直观的方法：双重循环枚举所有可能的数对 `(i, j)`，检查 `nums[i] + nums[j] === target`。\n\n虽然时间复杂度不理想，但实现简单，适合理解问题本质。\n\n```javascript\nfunction twoSum(nums, target) {\n  for (let i = 0; i < nums.length; i++) {\n    for (let j = i + 1; j < nums.length; j++) {\n      if (nums[i] + nums[j] === target) {\n        return [i, j];\n      }\n    }\n  }\n  return [];\n}\n```\n\n## 复杂度分析\n\n- **时间复杂度**：O(n²)，两层嵌套循环\n- **空间复杂度**：O(1)，不需要额外空间', `summary` = 'O(n²) 暴力枚举解法，双重循环检查所有数对。实现简单，适合入门理解。' WHERE `id` = 'sol-002';

UPDATE `solutions` SET `content` = '# TypeScript Map 解法\n\n## 思路\n\n使用 TypeScript 的 `Map` 对象，利用其 `has()` 和 `get()` 方法实现 O(1) 查找。与普通对象 `{}` 不同，`Map` 的 key 可以是任意类型，且不会受到原型链属性的干扰。\n\n```typescript\nfunction twoSum(nums: number[], target: number): number[] {\n  const prev = new Map<number, number>();\n\n  for (let i = 0; i < nums.length; i++) {\n    const need = target - nums[i];\n    if (prev.has(need)) {\n      return [prev.get(need)!, i];\n    }\n    prev.set(nums[i], i);\n  }\n\n  throw new Error("No solution found");\n}\n```\n\n## 为什么用 Map 而不是 {}\n\n1. `Map.has()` 不会误判原型链上的属性（如 `"constructor"`）\n2. key 可以是 `number` 类型，不需要字符串转换\n3. `Map.get()` 返回 `undefined` 而非实际值时语义清晰\n\n## 复杂度分析\n\n- **时间复杂度**：O(n)\n- **空间复杂度**：O(n)', `summary` = 'TypeScript Map 解法，利用 Map 的类型安全和 O(1) 查找特性，比普通对象更可靠。' WHERE `id` = 'sol-003';

UPDATE `solutions` SET `content` = '# JavaScript 哈希表解法\n\n## 思路\n\n使用原生 `Map` 对象实现 O(n) 解法。与普通对象相比，`Map` 在 JavaScript 中性能更优，且支持任意类型的 key。\n\n```javascript\nfunction twoSum(nums, target) {\n  const map = new Map();\n  for (let i = 0; i < nums.length; i++) {\n    const complement = target - nums[i];\n    if (map.has(complement)) {\n      return [map.get(complement), i];\n    }\n    map.set(nums[i], i);\n  }\n}\n```\n\n## 关键细节\n\n- **边查边插**：先检查 complement 是否存在，再插入当前元素。这保证不会用同一个元素匹配自己\n- **返回值**：题目保证有且仅有一个解，所以不需要处理无解情况\n- **Map vs Object**：JavaScript 中 Map 的 `has/get/set` 操作比对象属性访问更快\n\n## 复杂度分析\n\n- **时间复杂度**：O(n)，一次遍历\n- **空间复杂度**：O(n)，哈希表存储', `summary` = 'JavaScript Map 实现 O(n) 两数之和，边查边插避免自匹配，利用原生 Map 的高性能查找。' WHERE `id` = 'sol-004';

-- Insert solutions sol-009, sol-010, sol-011 using ON DUPLICATE KEY UPDATE
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-009', 1, 'user-yuki', N'双指针夹逼法', N'## 题目理解\n\n两数之和的双指针夹逼解法。\n\n## 解题思路\n\n先排序，然后用左右指针向中间逼近：\n- 如果和大于目标，右指针左移\n- 如果和小于目标，左指针右移\n\n## 方法\n\n```typescript\nfunction twoSum(nums: number[], target: number): number[] {\n    const sorted = nums.map((v, i) => [v, i]).sort((a, b) => a[0] - b[0]);\n    let left = 0, right = sorted.length - 1;\n    \n    while (left < right) {\n        const sum = sorted[left][0] + sorted[right][0];\n        if (sum === target) {\n            return [sorted[left][1], sorted[right][1]];\n        } else if (sum < target) {\n            left++;\n        } else {\n            right--;\n        }\n    }\n    \n    return [];\n}\n```\n\n## 复杂度分析\n- 时间复杂度：O(n log n)\n- 空间复杂度：O(n)', N'双指针 O(n log n)', 'typescript', '["two-pointers","array"]', 213, NOW(3), NOW(3), 1, NOW(3), 'user-yuki', 0, NULL, NULL, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  summary = VALUES(summary),
  language = VALUES(language),
  tags = VALUES(tags),
  views = VALUES(views),
  updated_at = NOW(3);

INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-010', 1, 'user-alex', N'数学公式法', N'## 题目理解\n\n利用数学公式 target - nums[i] 求解。\n\n## 解题思路\n\n对于每个数，检查 target - num 是否存在。\n\n```python\ndef two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []\n```\n\n## 复杂度分析\n- 时间复杂度：O(n)\n- 空间复杂度：O(n)', N'哈希表 O(n)', 'python', '["math"]', 78, NOW(3), NOW(3), 1, NOW(3), 'user-alex', 0, NULL, NULL, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  summary = VALUES(summary),
  language = VALUES(language),
  tags = VALUES(tags),
  views = VALUES(views),
  updated_at = NOW(3);

INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-011', 1, 'user-chen', N'两遍哈希', N'## 题目理解\n\n两遍哈希表：第一遍建立映射，第二遍查找。\n\n## 方法\n\n```typescript\nfunction twoSumHash(nums: number[], target: number): number[] {\n    const map = new Map<number, number>();\n    \n    // 第一遍：建立映射\n    for (let i = 0; i < nums.length; i++) {\n        map.set(nums[i], i);\n    }\n    \n    // 第二遍：查找\n    for (let i = 0; i < nums.length; i++) {\n        const complement = target - nums[i];\n        if (map.has(complement) && map.get(complement) !== i) {\n            return [i, map.get(complement)!];\n        }\n    }\n    \n    return [];\n}\n```\n\n## 复杂度分析\n- 时间复杂度：O(n)\n- 空间复杂度：O(n)', N'两遍哈希', 'typescript', '["hash-map"]', 56, NOW(3), NOW(3), 1, NOW(3), 'user-chen', 0, NULL, NULL, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  summary = VALUES(summary),
  language = VALUES(language),
  tags = VALUES(tags),
  views = VALUES(views),
  updated_at = NOW(3);

COMMIT;
SET FOREIGN_KEY_CHECKS=1;
