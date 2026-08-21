# Deprecated compatibility alias. DevStack is the only supported development launcher.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$devStack = Join-Path $repoRoot "scripts/dev/up.sh"

& bash $devStack --no-frontend @args
exit $LASTEXITCODE
