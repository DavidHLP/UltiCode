-- ============================================================
-- Fix: Submission test_details JSON 格式
-- ------------------------------------------------------------
-- V20260603_120600 写入了 {"total":..,"passed":..,"failed":..} 对象,
-- 但后端 TestCaseDetail 字段类型为 List<TestCaseDetail>, 必须为数组。
-- 此脚本将所有种子的 test_details 替换为合规 JSON 数组。
--
-- 仅修复 notes 以 'Seed submission' 开头的种子行, 不影响真实提交。
-- ============================================================

UPDATE `submissions`
SET `test_details` = JSON_ARRAY(
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
    'status', CASE `status`
                WHEN 'Accepted'              THEN 'Accepted'
                WHEN 'Wrong Answer'          THEN 'Wrong Answer'
                WHEN 'Time Limit Exceeded'   THEN 'Time Limit Exceeded'
                WHEN 'Memory Limit Exceeded' THEN 'Memory Limit Exceeded'
                WHEN 'Output Limit Exceeded' THEN 'Accepted'
                WHEN 'Runtime Error'         THEN 'Runtime Error'
                WHEN 'Compile Error'         THEN 'Compile Error'
                WHEN 'Presentation Error'    THEN 'Wrong Answer'
                WHEN 'System Error'          THEN 'System Error'
                ELSE 'Accepted'
              END,
    'time',   CASE `status`
                WHEN 'Time Limit Exceeded' THEN 2000
                WHEN 'Runtime Error'       THEN 22
                WHEN 'Wrong Answer'        THEN 65
                WHEN 'Presentation Error'  THEN 75
                WHEN 'Memory Limit Exceeded' THEN 120
                ELSE 50
              END,
    'memory', CASE `status`
                WHEN 'Memory Limit Exceeded' THEN 400.0
                WHEN 'Wrong Answer'          THEN 16.5
                WHEN 'Runtime Error'         THEN 14.5
                ELSE 15.5
              END,
    'detail',
      CASE `status`
        WHEN 'Accepted'              THEN 'all 10/10 cases passed'
        WHEN 'Wrong Answer'          THEN 'expected [1,2] but got [2,1]'
        WHEN 'Time Limit Exceeded'   THEN 'execution exceeded 2000ms time limit'
        WHEN 'Memory Limit Exceeded' THEN 'memory usage 400MB > 256MB limit'
        WHEN 'Output Limit Exceeded' THEN 'all 10/10 cases passed (but stdout too large)'
        WHEN 'Runtime Error'         THEN 'java.lang.ArrayIndexOutOfBoundsException at line 14'
        WHEN 'Compile Error'         THEN 'compilation failed: cannot find symbol'
        WHEN 'Presentation Error'    THEN 'expected 1 line, got 2 lines (extra newline)'
        WHEN 'System Error'          THEN 'internal judge service unavailable'
        ELSE 'all 10/10 cases passed'
      END,
    'output',         CASE WHEN `status` IN ('Wrong Answer','Presentation Error') THEN '[2,1]' ELSE '[1,2]' END,
    'expectedOutput', '[1,2]',
    'inputs',         JSON_ARRAY(
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
)
WHERE `notes` LIKE 'Seed submission%';
