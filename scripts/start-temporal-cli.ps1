# Phuong an 2: Temporal CLI dev server (khong can Docker)
# Yeu cau: da cai Temporal CLI (lenh `temporal` co trong PATH)
#   .\scripts\start-temporal-cli.ps1
# Doc TEMPORAL_PORT / TEMPORAL_CLI_UI_PORT / TEMPORAL_NAMESPACE tu .env neu co.

$envFile = Join-Path $PSScriptRoot "..\.env"
$vars = @{}
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object {
        $key, $value = $_ -split '=', 2
        $vars[$key.Trim()] = $value.Trim()
    }
}

function Get-Setting($name, $default) {
    $fromEnv = [Environment]::GetEnvironmentVariable($name)
    if ($fromEnv) { return $fromEnv }
    if ($vars.ContainsKey($name) -and $vars[$name]) { return $vars[$name] }
    return $default
}

$port = Get-Setting "TEMPORAL_PORT" "7233"
$uiPort = Get-Setting "TEMPORAL_CLI_UI_PORT" "8233"
$namespace = Get-Setting "TEMPORAL_NAMESPACE" "default"

if (-not (Get-Command temporal -ErrorAction SilentlyContinue)) {
    Write-Error "Khong tim thay Temporal CLI. Xem huong dan cai dat trong README.md"
    exit 1
}

Write-Host "Temporal dev server: localhost:$port | UI: http://localhost:$uiPort | namespace: $namespace"
temporal server start-dev --port $port --ui-port $uiPort --namespace $namespace --db-filename temporal-dev.db
