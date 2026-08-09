# Load .env file and start backend
$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"')
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
$servicesDir = Join-Path -Path $PSScriptRoot -ChildPath '..\services'
Set-Location -LiteralPath $servicesDir
& mvn spring-boot:run
