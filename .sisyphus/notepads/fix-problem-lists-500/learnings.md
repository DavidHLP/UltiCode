# fix-problem-lists-500 Learnings

## Date: 2026-05-07

## What Worked
1. Direct docker exec MySQL commands work when db-manager CLI fails
2. Verifying API endpoint with curl + python JSON parsing

## What Didn't Work
1. db-manager CLI couldn't verify - Flyway not configured in this database
2. Subagent introduced scope creep (modified unrelated ProblemMapper, ProblemServiceImpl files)

## How to Prevent Scope Creep
- Always verify git status after subagent completes
- Revert unrelated file changes immediately
- Monitor subagent file change summaries carefully

## Key Commands Used
```bash
# Apply migration directly
docker exec ulticode-mysql mysql -u root -p"CHANGE_ME_root_password" -e "USE ulticode; ALTER TABLE problem_lists ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER banner_order;"

# Verify column
docker exec ulticode-mysql mysql -u root -p"CHANGE_ME_root_password" -e "USE ulticode; DESCRIBE problem_lists;" | grep version

# Test API
curl -s -X POST http://localhost:9001/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -c /tmp/cookies.txt
curl -s "http://localhost:9001/admin/problem-lists?page=1&limit=10" -b /tmp/cookies.txt
```

## Root Cause
- ProblemList entity has @Version annotation for optimistic locking
- V28 migration adds version column but wasn't applied
- MyBatis-Plus auto-includes version in SELECT queries → SQL error → 500

## Resolution
Applied V28 migration directly via docker exec since Flyway wasn't configured
