const baseUrl = process.env.X_AI_GATEWAY_BASE_URL ?? 'http://localhost:8080'
const apiKey = process.env.X_AI_GATEWAY_API_KEY

if (!apiKey) {
  throw new Error('X_AI_GATEWAY_API_KEY is required')
}

const body = {
  model: process.env.X_AI_GATEWAY_MODEL ?? 'gpt-4o-mini',
  messages: [
    {
      role: 'user',
      content: 'Return a compact JSON object that describes gateway chat parity.',
    },
  ],
  store: process.env.X_AI_GATEWAY_CHAT_STORE === '1',
  metadata: {
    example: 'chat-advanced-parameters',
    owner: process.env.X_AI_GATEWAY_EXAMPLE_OWNER ?? 'local-test',
  },
  response_format: {
    type: 'json_schema',
    json_schema: {
      name: 'GatewayChatParity',
      strict: true,
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          status: { type: 'string' },
          checked: {
            type: 'array',
            items: { type: 'string' },
          },
        },
        required: ['status', 'checked'],
      },
    },
  },
  tools: [
    {
      type: 'function',
      function: {
        name: 'record_gateway_check',
        description: 'Record a local gateway compatibility check result.',
        strict: true,
        parameters: {
          type: 'object',
          additionalProperties: false,
          properties: {
            status: { type: 'string' },
          },
          required: ['status'],
        },
      },
    },
  ],
  tool_choice: 'auto',
  parallel_tool_calls: true,
  service_tier: process.env.X_AI_GATEWAY_SERVICE_TIER ?? 'auto',
  stream_options: {
    include_usage: true,
  },
}

if (process.env.X_AI_GATEWAY_CHAT_WEB_SEARCH === '1') {
  body.web_search_options = {
    search_context_size: process.env.X_AI_GATEWAY_CHAT_SEARCH_CONTEXT ?? 'medium',
    user_location: {
      type: 'approximate',
      approximate: {
        country: process.env.X_AI_GATEWAY_CHAT_LOCATION_COUNTRY ?? 'US',
      },
    },
  }
}

if (process.env.X_AI_GATEWAY_CHAT_AUDIO === '1') {
  body.modalities = ['text', 'audio']
  body.audio = {
    voice: process.env.X_AI_GATEWAY_CHAT_AUDIO_VOICE ?? 'alloy',
    format: process.env.X_AI_GATEWAY_CHAT_AUDIO_FORMAT ?? 'mp3',
  }
}

const response = await fetch(`${baseUrl.replace(/\/$/, '')}/v1/chat/completions`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${apiKey}`,
    'Content-Type': 'application/json',
    'X-AI-Gateway-Client-Family': 'GENERIC_OPENAI',
  },
  body: JSON.stringify(body),
})

console.log(await response.text())
