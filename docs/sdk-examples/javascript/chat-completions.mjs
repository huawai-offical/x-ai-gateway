const baseUrl = process.env.X_AI_GATEWAY_BASE_URL ?? 'http://localhost:8080'
const apiKey = process.env.X_AI_GATEWAY_API_KEY

if (!apiKey) {
  throw new Error('X_AI_GATEWAY_API_KEY is required')
}

const response = await fetch(`${baseUrl.replace(/\/$/, '')}/v1/chat/completions`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${apiKey}`,
    'Content-Type': 'application/json',
    'X-AI-Gateway-Client-Family': 'GENERIC_OPENAI',
  },
  body: JSON.stringify({
    model: process.env.X_AI_GATEWAY_MODEL ?? 'gpt-4o-mini',
    messages: [{ role: 'user', content: 'ping' }],
  }),
})

console.log(await response.text())
