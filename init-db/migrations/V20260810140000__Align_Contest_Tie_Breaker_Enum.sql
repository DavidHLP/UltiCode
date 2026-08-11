-- Keep the database enum aligned with ContestTieBreaker and the public API contract.
ALTER TABLE contests
    MODIFY COLUMN tie_breaker enum('LAST_SOLVE_TIME','TOTAL_TIME','TOTAL_ATTEMPTS','NONE')
    NOT NULL DEFAULT 'LAST_SOLVE_TIME';
