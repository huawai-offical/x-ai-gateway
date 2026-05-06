param(
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "deploy/docker-compose.yml"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "缺少 $EnvFile，请先从 deploy/.env.example 复制并填写生产配置。"
}

docker compose --env-file $EnvFile -f $ComposeFile pull
docker compose --env-file $EnvFile -f $ComposeFile up -d --build
docker compose --env-file $EnvFile -f $ComposeFile ps
