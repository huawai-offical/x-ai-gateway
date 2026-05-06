param(
    [Parameter(Mandatory = $true)]
    [string]$BackupId,
    [string]$PreviousImage = "x-ai-gateway:previous",
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "deploy/docker-compose.yml",
    [switch]$Confirm
)

$ErrorActionPreference = "Stop"

if (-not $Confirm) {
    throw "回滚需要显式传入 -Confirm。"
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "缺少 $EnvFile，禁止回滚。"
}

$env:X_AI_GATEWAY_IMAGE = $PreviousImage
docker compose --env-file $EnvFile -f $ComposeFile up -d gateway
docker compose --env-file $EnvFile -f $ComposeFile logs --tail 200 gateway
Write-Host "请在管理端使用 backupId=$BackupId 执行数据库/配置快照恢复校验。"
