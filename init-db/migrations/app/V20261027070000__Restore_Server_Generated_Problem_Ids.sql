-- Keep existing IDs/references and database-generated IDs for new problems.
-- MySQL requires removing the referencing constraint before changing this attribute.
-- Re-add the identical constraint with validation; never disable foreign_key_checks.
ALTER TABLE test_cases DROP FOREIGN KEY fk_test_cases_problem_id;
ALTER TABLE problems MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE test_cases ADD CONSTRAINT fk_test_cases_problem_id
    FOREIGN KEY (problem_id) REFERENCES problems (id);
