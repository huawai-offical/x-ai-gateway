param(
    [Parameter(Mandatory = $true)]
    [string]$TargetVersion,
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "deploy/docker-compose.yml",
    [switch]$Confirm
)

$ErrorActionPreference = "Stop"

if (-not $Confirm) {
    throw "升级需要显式传入 -Confirm。"
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "缺少 $EnvFile，禁止升级。"
}

$env:X_AI_GATEWAY_IMAGE = "x-ai-gateway:$TargetVersion"
docker compose --env-file $EnvFile -f $ComposeFile build gateway
docker compose --env-file $EnvFile -f $ComposeFile up -d gateway
docker compose --env-file $EnvFile -f $ComposeFile ps gateway
