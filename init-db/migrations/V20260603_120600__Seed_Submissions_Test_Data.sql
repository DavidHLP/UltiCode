-- ============================================================
-- Seed Test Data: Submissions
-- ------------------------------------------------------------
-- 目标:
--   为每个 (user, problem) 组合至少生成 1 条 submission,
--   且 11 种 status 全部覆盖 (前 11 条分配不同状态, 其余 Accepted)。
--
-- 维护指南:
--   status 取值必须与后端
--   com.ulticode.modules.submission.service.impl.SubmissionServiceImpl#getStatuses()
--   中 setKey(...) 的字符串保持一致 (后端硬编码, 不查表)。
--
-- 幂等: 通过 @seed_count 控制, 仅当 submissions 表为空时执行种子插入。
--
-- 说明:
--   submission_statuses 表 (schema 已建) 当前未被后端/前端任何代码读取,
--   且 code 列宽 varchar(10) 不足以容纳后端使用的标准 code
--   (如 WRONG_ANSWER=12、TIME_LIMIT_EXCEEDED=19)。故此处不写入, 留空表。
-- ============================================================

-- 0. 幂等判断
SET @seed_count := (SELECT COUNT(*) FROM submissions);

-- ------------------------------------------------------------
-- 1. 派生表: 列出 (user, problem) 全部配对 + 用 ROW_NUMBER 标号
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `_seed_pairs`;
CREATE TEMPORARY TABLE `_seed_pairs` (
  `user_id`   VARCHAR(40)  NOT NULL,
  `username`  VARCHAR(60)  NOT NULL,
  `role`      VARCHAR(20)  NOT NULL,
  `problem_id` BIGINT      NOT NULL,
  `rn`        INT          NOT NULL,
  PRIMARY KEY (`user_id`, `problem_id`)
) ENGINE=Memory;

INSERT INTO `_seed_pairs` (`user_id`, `username`, `role`, `problem_id`, `rn`)
SELECT
  u.id, u.username, u.role, p.id,
  ROW_NUMBER() OVER (ORDER BY u.role, u.username, p.id)
FROM `users` u
CROSS JOIN `problems` p
WHERE u.is_deleted = 0 AND p.is_published = 1;

-- ------------------------------------------------------------
-- 3. submissions 主种子:
--    - 前 11 条覆盖 11 种 status
--    - 其余统一 Accepted (代表"该用户已顺利通过该题")
-- ------------------------------------------------------------
INSERT INTO `submissions`
  (`id`, `problem_id`, `user_id`, `language`, `code`, `status`, `runtime`, `memory`,
   `notes`, `created_at`, `runtime_percentile`, `memory_percentile`,
   `test_details`, `runtimeDistBinsMs`, `memoryDistBinsMb`, `retry_count`)
SELECT
  UUID()                                                       AS id,
  sp.problem_id,
  sp.user_id,
  ELT(1 + (sp.problem_id + LENGTH(sp.username)) MOD 4, 'java', 'python', 'javascript', 'cpp') AS language,
  CONCAT(
    '-- seed submission for problem=', sp.problem_id, ' user=', sp.username, '\n',
    CASE (sp.problem_id + LENGTH(sp.username)) MOD 4
      WHEN 0 THEN 'class Solution { public int[] solve() { return new int[]{0, 1}; } }'
      WHEN 1 THEN 'class Solution:\n    def solve(self):\n        return [0, 1]'
      WHEN 2 THEN 'const solve = () => [0, 1];'
      ELSE       'class Solution { public: std::vector<int> solve() { return {0, 1}; } };'
    END
  )                                                            AS code,
  CASE sp.rn
    WHEN  1 THEN 'Pending'
    WHEN  2 THEN 'Judging'
    WHEN  3 THEN 'Accepted'
    WHEN  4 THEN 'Wrong Answer'
    WHEN  5 THEN 'Time Limit Exceeded'
    WHEN  6 THEN 'Memory Limit Exceeded'
    WHEN  7 THEN 'Output Limit Exceeded'
    WHEN  8 THEN 'Runtime Error'
    WHEN  9 THEN 'Compile Error'
    WHEN 10 THEN 'Presentation Error'
    WHEN 11 THEN 'System Error'
    ELSE 'Accepted'
  END                                                          AS status,
  CASE sp.rn
    WHEN  1 THEN 0
    WHEN  2 THEN 0
    WHEN  3 THEN 50  + ((sp.problem_id + LENGTH(sp.username)) MOD 80)   -- 50-130 ms
    WHEN  4 THEN 60  + ((sp.problem_id + LENGTH(sp.username)) MOD 120)  -- 60-180 ms
    WHEN  5 THEN 2000                                                   -- TLE
    WHEN  6 THEN 120 + ((sp.problem_id + LENGTH(sp.username)) MOD 60)   -- 120-180 ms
    WHEN  7 THEN 80  + ((sp.problem_id + LENGTH(sp.username)) MOD 40)   -- 80-120 ms
    WHEN  8 THEN 20  + ((sp.problem_id + LENGTH(sp.username)) MOD 30)   -- 20-50 ms
    WHEN  9 THEN 0                                                      -- Compile Error
    WHEN 10 THEN 70  + ((sp.problem_id + LENGTH(sp.username)) MOD 40)   -- 70-110 ms
    WHEN 11 THEN 0                                                      -- System Error
    ELSE 40 + ((sp.problem_id + LENGTH(sp.username)) MOD 100)
  END                                                          AS runtime,
  CASE sp.rn
    WHEN  1 THEN NULL
    WHEN  2 THEN NULL
    WHEN  3 THEN 15.5 + ((sp.problem_id + LENGTH(sp.username)) MOD 10)
    WHEN  4 THEN 16.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 9)
    WHEN  5 THEN 18.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 8)
    WHEN  6 THEN 400.0                                                  -- MLE
    WHEN  7 THEN 20.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 5)
    WHEN  8 THEN 14.5 + ((sp.problem_id + LENGTH(sp.username)) MOD 5)
    WHEN  9 THEN 0.0
    WHEN 10 THEN 18.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 4)
    WHEN 11 THEN 0.0
    ELSE 16.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 8)
  END                                                          AS memory,
  CONCAT('Seed submission (status variant #', sp.rn, ')')     AS notes,
  NOW(3) - INTERVAL ((sp.rn * 3) + (sp.problem_id * 2) + (LENGTH(sp.username) MOD 5)) HOUR AS created_at,
  CASE sp.rn
    WHEN  3 THEN 65.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 30)
    WHEN  4 THEN 30.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 25)
    WHEN  5 THEN 5.0
    WHEN  6 THEN 10.0
    WHEN  8 THEN 20.0
    WHEN 10 THEN 35.0
    ELSE NULL
  END                                                          AS runtime_percentile,
  CASE sp.rn
    WHEN  3 THEN 60.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 30)
    WHEN  4 THEN 35.0 + ((sp.problem_id + LENGTH(sp.username)) MOD 25)
    WHEN  6 THEN 5.0
    WHEN  8 THEN 25.0
    ELSE NULL
  END                                                          AS memory_percentile,
  JSON_ARRAY(
    JSON_OBJECT('status','Accepted','time', 45,'memory',15.2,
                'detail','all assertions passed','output','[1,2]','expectedOutput','[1,2]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 48,'memory',15.4,
                'detail','all assertions passed','output','[3,4]','expectedOutput','[3,4]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 52,'memory',15.5,
                'detail','all assertions passed','output','[5,6]','expectedOutput','[5,6]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 55,'memory',15.6,
                'detail','all assertions passed','output','[7,8]','expectedOutput','[7,8]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 60,'memory',15.7,
                'detail','all assertions passed','output','[9,10]','expectedOutput','[9,10]','inputs', JSON_ARRAY()),
    JSON_OBJECT(
      'status', CASE WHEN sp.rn IN (3,6,7) THEN 'Accepted'
                     WHEN sp.rn = 4  THEN 'Wrong Answer'
                     WHEN sp.rn = 5  THEN 'Time Limit Exceeded'
                     WHEN sp.rn = 8  THEN 'Runtime Error'
                     WHEN sp.rn = 10 THEN 'Wrong Answer'
                     ELSE 'Accepted' END,
      'time',   CASE WHEN sp.rn = 5  THEN 2000
                     WHEN sp.rn = 8  THEN 22
                     WHEN sp.rn = 4  THEN 65
                     WHEN sp.rn = 10 THEN 75
                     ELSE 50 END,
      'memory', CASE WHEN sp.rn = 6  THEN 400.0
                     WHEN sp.rn = 4  THEN 16.5
                     WHEN sp.rn = 8  THEN 14.5
                     ELSE 15.5 END,
      'detail',
        CASE sp.rn
          WHEN 3  THEN 'all 10/10 cases passed'
          WHEN 4  THEN 'expected [1,2] but got [2,1]'
          WHEN 5  THEN 'execution exceeded 2000ms time limit'
          WHEN 6  THEN 'memory usage 400MB > 256MB limit'
          WHEN 7  THEN 'all 10/10 cases passed (but too much stdout)'
          WHEN 8  THEN 'java.lang.ArrayIndexOutOfBoundsException at line 14'
          WHEN 9  THEN 'compilation failed: cannot find symbol'
          WHEN 10 THEN 'expected 1 line, got 2 lines (extra newline)'
          WHEN 11 THEN 'internal judge service unavailable'
          ELSE 'all 10/10 cases passed'
        END,
      'output',     CASE WHEN sp.rn IN (4,10) THEN '[2,1]' ELSE '[1,2]' END,
      'expectedOutput', '[1,2]',
      'inputs',     JSON_ARRAY(
                      JSON_OBJECT('id','in1','label','nums','name','nums','value','[2,7,11,15]'),
                      JSON_OBJECT('id','in2','label','target','name','target','value','9')
                    )
    ),
    JSON_OBJECT('status','Accepted','time', 50,'memory',15.5,
                'detail','all assertions passed','output','[0,1]','expectedOutput','[0,1]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 50,'memory',15.5,
                'detail','all assertions passed','output','[0,1]','expectedOutput','[0,1]','inputs', JSON_ARRAY()),
    JSON_OBJECT('status','Accepted','time', 50,'memory',15.5,
                'detail','all assertions passed','output','[0,1]','expectedOutput','[0,1]','inputs', JSON_ARRAY())
  )                                                            AS test_details,
  JSON_ARRAY(20, 40, 80, 160, 320, 640, 1280, 2560)            AS runtimeDistBinsMs,
  JSON_ARRAY(8, 16, 32, 64, 128, 256, 512)                     AS memoryDistBinsMb,
  0                                                            AS retry_count
FROM `_seed_pairs` sp
WHERE @seed_count = 0;

DROP TEMPORARY TABLE IF EXISTS `_seed_pairs`;

-- ============================================================
-- Verify:
--   -- 状态分布 (应见 11 种)
--   SELECT status, COUNT(*) c FROM submissions GROUP BY status ORDER BY c DESC;
--
--   -- 每用户每题至少一条
--   SELECT u.username, COUNT(DISTINCT s.problem_id) AS probs, COUNT(*) AS subs
--   FROM submissions s
--   JOIN users u ON u.id = s.user_id
--   WHERE u.is_deleted = 0
--   GROUP BY u.username
--   ORDER BY u.username;
-- ============================================================
