#!/usr/bin/env node
import { readFileSync, writeFileSync } from 'node:fs'
import { basename } from 'node:path'

const args = parseArgs(process.argv.slice(2))
const command = args._[0]

if (!command || args.help) {
  usage()
  process.exit(command ? 0 : 2)
}

if (command === 'migrate') {
  migrate(args)
} else if (command === 'export-template') {
  exportTemplate(args)
} else {
  throw new Error(`Unsupported command: ${command}`)
}

function migrate(options) {
  const source = required(options.source, '--source')
  const inputPath = required(options.input, '--input')
  const dryRun = Boolean(options['dry-run'])
  const input = JSON.parse(readFileSync(inputPath, 'utf8'))
  const mapped = source === 'one-api' ? mapOneApi(input) : source === 'sub2api' ? mapSub2Api(input) : null
  if (!mapped) {
    throw new Error(`Unsupported migration source: ${source}`)
  }
  const report = {
    source,
    input: basename(inputPath),
    dryRun,
    users: mapped.users.length,
    keys: mapped.keys.length,
    providers: mapped.providers.length,
    usageRecords: mapped.usageRecords.length,
    warnings: mapped.warnings,
    output: mapped,
  }
  const text = JSON.stringify(report, null, 2)
  if (options.output && !dryRun) {
    writeFileSync(options.output, text)
  }
  console.log(text)
}

function exportTemplate(options) {
  const output = {
    users: [],
    keys: [],
    providers: [],
    usageRecords: [],
  }
  const text = JSON.stringify(output, null, 2)
  if (options.output) {
    writeFileSync(options.output, text)
  }
  console.log(text)
}

function mapOneApi(input) {
  const warnings = []
  const users = (input.users ?? []).map((user) => ({
    externalId: `one-api:user:${user.id}`,
    email: user.email ?? user.username,
    displayName: user.display_name ?? user.username,
    balanceMicros: Number(user.quota ?? 0),
  }))
  const keys = (input.tokens ?? input.keys ?? []).map((token) => ({
    externalId: `one-api:key:${token.id}`,
    keyName: token.name ?? `one-api-key-${token.id}`,
    ownerExternalId: token.user_id == null ? null : `one-api:user:${token.user_id}`,
    maskedKey: token.key ? maskSecret(token.key) : null,
    allowedModels: normalizeList(token.models),
  }))
  const providers = (input.channels ?? []).map((channel) => ({
    externalId: `one-api:channel:${channel.id}`,
    providerType: mapProvider(channel.type ?? channel.provider),
    displayName: channel.name ?? `channel-${channel.id}`,
    baseUrl: channel.base_url ?? channel.baseUrl ?? null,
    supportedModels: normalizeList(channel.models),
  }))
  const usageRecords = (input.logs ?? []).map((log) => ({
    requestId: log.request_id ?? `one-api-log-${log.id}`,
    externalKeyId: log.token_id == null ? null : `one-api:key:${log.token_id}`,
    model: log.model_name ?? log.model,
    promptTokens: Number(log.prompt_tokens ?? 0),
    completionTokens: Number(log.completion_tokens ?? 0),
  }))
  if (providers.some((provider) => provider.providerType === 'OPENAI_COMPATIBLE')) {
    warnings.push('部分 One API channel 只能映射为 OPENAI_COMPATIBLE，需要人工确认 provider type。')
  }
  return { users, keys, providers, usageRecords, warnings }
}

function mapSub2Api(input) {
  const warnings = []
  const users = (input.accounts ?? input.users ?? []).map((account) => ({
    externalId: `sub2api:user:${account.id}`,
    email: account.email ?? account.name,
    displayName: account.name ?? account.email,
    balanceMicros: Number(account.balance_micros ?? account.balance ?? 0),
  }))
  const keys = (input.api_keys ?? input.keys ?? []).map((key) => ({
    externalId: `sub2api:key:${key.id}`,
    keyName: key.name ?? `sub2api-key-${key.id}`,
    ownerExternalId: key.account_id == null ? null : `sub2api:user:${key.account_id}`,
    maskedKey: key.secret ? maskSecret(key.secret) : null,
    allowedModels: normalizeList(key.models),
  }))
  const providers = (input.providers ?? input.channels ?? []).map((provider) => ({
    externalId: `sub2api:provider:${provider.id}`,
    providerType: mapProvider(provider.kind ?? provider.type),
    displayName: provider.name ?? `provider-${provider.id}`,
    baseUrl: provider.endpoint ?? provider.base_url ?? null,
    supportedModels: normalizeList(provider.models),
  }))
  const usageRecords = (input.usage ?? []).map((usage) => ({
    requestId: usage.request_id ?? `sub2api-usage-${usage.id}`,
    externalKeyId: usage.key_id == null ? null : `sub2api:key:${usage.key_id}`,
    model: usage.model,
    promptTokens: Number(usage.prompt_tokens ?? 0),
    completionTokens: Number(usage.completion_tokens ?? 0),
  }))
  if (providers.length === 0) {
    warnings.push('Sub2API 输入未包含 providers/channels，迁移后需要手工绑定上游凭证。')
  }
  return { users, keys, providers, usageRecords, warnings }
}

function mapProvider(value) {
  const normalized = String(value ?? '').toLowerCase()
  if (normalized.includes('gemini') || normalized.includes('google')) return 'GEMINI_DIRECT'
  if (normalized.includes('anthropic') || normalized.includes('claude')) return 'ANTHROPIC_DIRECT'
  if (normalized.includes('ollama')) return 'OLLAMA_DIRECT'
  if (normalized.includes('openai')) return 'OPENAI_DIRECT'
  return 'OPENAI_COMPATIBLE'
}

function normalizeList(value) {
  if (Array.isArray(value)) return value.filter(Boolean).map(String)
  if (typeof value === 'string') return value.split(',').map((item) => item.trim()).filter(Boolean)
  return []
}

function maskSecret(value) {
  const text = String(value)
  if (text.length <= 8) return '***'
  return `${text.slice(0, 4)}...${text.slice(-4)}`
}

function parseArgs(argv) {
  const parsed = { _: [] }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (!arg.startsWith('--')) {
      parsed._.push(arg)
      continue
    }
    const key = arg.slice(2)
    const next = argv[index + 1]
    if (!next || next.startsWith('--')) {
      parsed[key] = true
    } else {
      parsed[key] = next
      index += 1
    }
  }
  return parsed
}

function required(value, name) {
  if (!value) throw new Error(`${name} is required`)
  return value
}

function usage() {
  console.log(`Usage:
  node scripts/data-management.mjs migrate --source one-api --input docs/migrations/samples/one-api-export.sample.json --dry-run
  node scripts/data-management.mjs migrate --source sub2api --input docs/migrations/samples/sub2api-export.sample.json --dry-run
  node scripts/data-management.mjs export-template --output export-template.json`)
}
