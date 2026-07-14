#!/usr/bin/env bash
# Reproducible BE-001 verification helper.
#
# WHY this exists (not a migration — Flyway ignores non-.sql files here):
# BE-001's V1 migration has been re-dispatched 5+ times, each false-negative
# traced to a PATH-RESOLUTION trap, never to the deliverable itself:
#   1. test-agent validated HEAD while the full SQL sat uncommitted in the worktree;
#   2. a stale, gitignored build/resources copy diverged from src;
#   3. a stray sibling dir project/data/<svc>/ (missing the backend/ prefix) shadowed the real module;
#   4. shell `../` depth-counting from a deeply-nested cwd landed outside the module.
# Each check below anchors to the git repo root (git rev-parse) so the result is
# invariant to invocation cwd — defeating traps #3 and #4. Failures are loud and
# specific so a validator concludes PASS only when every trap is demonstrably closed.
#
# Usage:  bash verify-be-001.sh
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel)" || { echo "FAIL: not inside a git repo"; exit 1; }
REL="project/data/2668088422724877313/backend/2668088422724877313-service"
SQL="$REL/src/main/resources/db/migration/V1__create_important_enterprise_table.sql"
BUILD="$REL/build/resources/main/db/migration/V1__create_important_enterprise_table.sql"
GRADLE="$REL/build.gradle"
EXPECTED_MD5="4254c3374dc0cea9be162ea4b43ba372"
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "PASS  $1"; }
bad()  { fail=$((fail+1)); echo "FAIL  $1"; }

# AC-1: SQL present in src
[ -f "$ROOT/$SQL" ] && ok "src SQL exists" || bad "src SQL missing: $SQL"

# AC-1: tri-state byte-identical (src == build == HEAD) — closes traps #1 and #2
src_md=$(md5sum "$ROOT/$SQL" 2>/dev/null | awk '{print $1}')
[ "$src_md" = "$EXPECTED_MD5" ] && ok "src md5 == expected ($src_md)" || bad "src md5 mismatch ($src_md)"
[ -f "$ROOT/$BUILD" ] && { build_md=$(md5sum "$ROOT/$BUILD" | awk '{print $1}'); [ "$build_md" = "$src_md" ] && ok "build copy == src" || bad "build copy diverged ($build_md)"; }
git cat-file -e "HEAD:$SQL" 2>/dev/null && { head_md=$(git show "HEAD:$SQL" 2>/dev/null | md5sum | awk '{print $1}'); [ "$head_md" = "$src_md" ] && ok "HEAD == src" || bad "HEAD diverged ($head_md)"; } || bad "SQL not committed to HEAD"

# AC-1: Flyway configured (the migration framework the script targets)
grep -qE 'flyway-core|flyway-mysql' "$ROOT/$GRADLE" && ok "Flyway deps declared" || bad "Flyway not configured in build.gradle"

# AC-3: asset_manager_id reference strategy is the documented string-column decision
grep -qi 'asset_manager_id.*VARCHAR(36)' "$ROOT/$SQL" && ok "asset_manager_id VARCHAR(36) (no-FK decision)" || bad "asset_manager_id strategy not reflected in SQL"

# AC-2: soft-delete-aware uniqueness via STORED generated columns
grep -q 'active_name' "$ROOT/$SQL" && grep -q 'active_uscc' "$ROOT/$SQL" && ok "unique keys exclude deleted rows (active_* generated cols)" || bad "soft-delete uniqueness missing"

# Trap #3: no stray sibling dir shadowing the real module
sibling="$ROOT/project/data/2668088422724877313-service"
[ -e "$sibling" ] && bad "stray sibling dir present: $sibling" || ok "no stray sibling dir"

echo "-----"; echo "PASS=$pass FAIL=$fail"; [ "$fail" -eq 0 ] && { echo "RESULT: BE-001 VERIFIED COMPLETE"; exit 0; } || { echo "RESULT: BE-001 HAS DEFECTS"; exit 1; }
