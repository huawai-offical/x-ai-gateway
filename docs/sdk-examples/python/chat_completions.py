import json
import os
import urllib.request


base_url = os.environ.get("X_AI_GATEWAY_BASE_URL", "http://localhost:8080")
api_key = os.environ["X_AI_GATEWAY_API_KEY"]
payload = {
    "model": os.environ.get("X_AI_GATEWAY_MODEL", "gpt-4o-mini"),
    "messages": [{"role": "user", "content": "ping"}],
}

request = urllib.request.Request(
    f"{base_url.rstrip('/')}/v1/chat/completions",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "X-AI-Gateway-Client-Family": "GENERIC_OPENAI",
    },
    method="POST",
)

with urllib.request.urlopen(request, timeout=30) as response:
    print(response.read().decode("utf-8"))
