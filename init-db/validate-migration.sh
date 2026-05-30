#!/bin/sh
# validate-migration.sh
# Git pre-commit hook for Flyway migration naming validation
# Usage: Copy to .git/hooks/pre-commit and make executable

MIGRATION_REGEX="^V[0-9]{14}__[a-zA-Z_]+\.sql$"

echo "Validating Flyway migration naming convention..."

CHECK_PASSED=0
FILES=$(git diff --cached --name-only --diff-filter=A | grep 'migrations/' | grep '\.sql$')

if [ -z "$FILES" ]; then
    echo "No migration files staged. Skipping validation."
    exit 0
fi

for FILE in $FILES; do
    BASENAME=$(basename "$FILE")
    if echo "$BASENAME" | grep -Eq "$MIGRATION_REGEX"; then
        echo "  ✓ $BASENAME"
    else
        echo "  ✗ ERROR: $BASENAME"
        echo "    Expected format: V{YYYYMMDDHHMMSS}__{Description}.sql"
        echo "    Example: V20260601120000__AddNewFeature.sql"
        CHECK_PASSED=1
    fi
done

if [ "$CHECK_PASSED" -eq 0 ]; then
    echo ""
    echo "All migrations validated successfully."
    exit 0
else
    echo ""
    echo "Validation FAILED. Please fix migration file names before committing."
    exit 1
fi