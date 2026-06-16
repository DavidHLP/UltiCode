-- =====================================================================
-- Seed missing test_cases for problems 1/2/3/4/6/7
-- ---------------------------------------------------------------------
-- 背景:这 6 道已发布题目的 test_cases 表为空,判题 worker
-- JudgeWorkerProcessor.processJobWithTestCases 在 findActiveCasesForJudging
-- 返回空时,按 fail-closed 设计(ADR P0-1)直接写 System Error,
-- 导致任意语言提交都失败(用户观察到的 Java System Error 即此)。
-- 本迁移为它们补齐测试用例,使判题链路可用。
--
-- Part A:把 problem_examples(每题 2 条;inputs 列 schema 与 test_cases
--         完全一致 —— 代码原话 "identical schema across both tables")
--         无损转换为 public sample 用例(is_sample=1,is_hidden=0)。
-- Part B:每题补 2 条隐藏评测用例(is_sample=0,is_hidden=1),
--         防止"针对公开示例硬编码",提升评测严谨性。
--
-- eligible 条件 = is_sample XOR is_hidden(恰好一个为 true)。
-- 所有行 id 用确定性前缀 'tc-seed-':可追溯 / 幂等 / 回滚。
-- 纯增量 INSERT,不改 schema,不触碰现有数据。
--
-- 回滚:  DELETE FROM test_cases WHERE id LIKE 'tc-seed-%';
-- =====================================================================

-- ── Part A: problem_examples → test_cases (public sample, judging-eligible) ──
INSERT INTO test_cases
    (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs, explanation)
SELECT
    CONCAT('tc-seed-', pe.problem_id, '-s', pe.example_order),
    pe.problem_id,
    1,                                  -- is_sample = true
    0,                                  -- is_hidden = false  → eligible (XOR)
    pe.example_order,                   -- test_order 继承 example_order
    pe.input_text,
    pe.output_text,
    pe.inputs,
    pe.explanation
FROM problem_examples pe
WHERE NOT EXISTS (
    SELECT 1 FROM test_cases tc
    WHERE tc.id = CONCAT('tc-seed-', pe.problem_id, '-s', pe.example_order)
);

-- ── Part B: hidden judge cases (is_sample=0, is_hidden=1 → eligible) ──
-- test_order 接在 sample(1,2)之后,从 3 起。
-- output 对齐 D-form harness 序列化:normalJson 双向归一后字符串相等,
--   int → "3",数组/链表 → "[..]",空 → "[]",
--   浮点返回值序列化带 ".0",故 median 的 expected 必须含小数点。

-- problem 1: two-sum  (nums:int[], target:int → int[])
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-1-h1', 1, 0, 1, 3, 'nums = [3,3], target = 6', '[0,1]',
       '[{"name":"nums","value":[3,3]},{"name":"target","value":6}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-1-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-1-h2', 1, 0, 1, 4, 'nums = [2,5,5,11], target = 10', '[1,2]',
       '[{"name":"nums","value":[2,5,5,11]},{"name":"target","value":10}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-1-h2');

-- problem 2: add-two-numbers  (l1,l2 存为数组 → 构造为 ListNode;逆序表数)
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-2-h1', 2, 0, 1, 3, 'l1 = [9,9,9,9,9,9,9], l2 = [9,9,9,9]', '[8,9,9,9,0,0,0,1]',
       '[{"name":"l1","value":[9,9,9,9,9,9,9]},{"name":"l2","value":[9,9,9,9]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-2-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-2-h2', 2, 0, 1, 4, 'l1 = [1,8], l2 = [0]', '[1,8]',
       '[{"name":"l1","value":[1,8]},{"name":"l2","value":[0]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-2-h2');

-- problem 3: longest-substring-without-repeating-characters  (s:string → int)
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-3-h1', 3, 0, 1, 3, 's = "pwwkew"', '3',
       '[{"name":"s","value":"pwwkew"}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-3-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-3-h2', 3, 0, 1, 4, 's = " "', '1',
       '[{"name":"s","value":" "}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-3-h2');

-- problem 4: median-of-two-sorted-arrays  (double 返回 → expected 必含小数点)
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-4-h1', 4, 0, 1, 3, 'nums1 = [1,2], nums2 = [-1,-1]', '0.00000',
       '[{"name":"nums1","value":[1,2]},{"name":"nums2","value":[-1,-1]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-4-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-4-h2', 4, 0, 1, 4, 'nums1 = [], nums2 = [2]', '2.00000',
       '[{"name":"nums1","value":[]},{"name":"nums2","value":[2]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-4-h2');

-- problem 6: reverse-linked-list  (head 存为数组 → 构造为 ListNode)
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-6-h1', 6, 0, 1, 3, 'head = [1,2,3]', '[3,2,1]',
       '[{"name":"head","value":[1,2,3]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-6-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-6-h2', 6, 0, 1, 4, 'head = []', '[]',
       '[{"name":"head","value":[]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-6-h2');

-- problem 7: merge-k-sorted-lists  (lists 存为数组的数组 → 构造为 ListNode[])
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-7-h1', 7, 0, 1, 3, 'lists = [[1],[2]]', '[1,2]',
       '[{"name":"lists","value":[[1],[2]]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-7-h1');
INSERT INTO test_cases (id, problem_id, is_sample, is_hidden, test_order, input_text, output_text, inputs)
SELECT 'tc-seed-7-h2', 7, 0, 1, 4, 'lists = [[],[1]]', '[1]',
       '[{"name":"lists","value":[[],[1]]}]'
WHERE NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.id = 'tc-seed-7-h2');
