-- Code-review fix: the per-user problem submission list query
-- (SubmissionMapper.findByProblemId in backend-submission) filters on
-- (problem_id, user_id) and orders by created_at DESC, id DESC (the id is
-- the unique tie-breaker for DATETIME(3) timestamp ties). The existing
-- equality-only submissions_problem_id_user_id_idx index forces MySQL to
-- sort all matching rows before applying LIMIT, which amplifies on deep
-- pages. This composite index serves both the equality predicates and the
-- full ordering.
ALTER TABLE `submissions`
  ADD INDEX `submissions_problem_id_user_created_idx` (`problem_id`, `user_id`, `created_at`, `id`);
