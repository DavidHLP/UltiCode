-- Contest adjudication mappers persist audit timestamps; align the legacy contest tables.
ALTER TABLE contest_problem_results
    ADD COLUMN created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE contest_submissions
    ADD COLUMN created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

ALTER TABLE first_solve_records
    ADD COLUMN created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
