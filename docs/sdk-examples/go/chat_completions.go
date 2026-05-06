package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
)

func main() {
	baseURL := getenv("X_AI_GATEWAY_BASE_URL", "http://localhost:8080")
	apiKey := os.Getenv("X_AI_GATEWAY_API_KEY")
	if apiKey == "" {
		panic("X_AI_GATEWAY_API_KEY is required")
	}

	payload := map[string]any{
		"model": getenv("X_AI_GATEWAY_MODEL", "gpt-4o-mini"),
		"messages": []map[string]string{
			{"role": "user", "content": "ping"},
		},
	}
	body, _ := json.Marshal(payload)
	req, err := http.NewRequest("POST", strings.TrimRight(baseURL, "/")+"/v1/chat/completions", bytes.NewReader(body))
	if err != nil {
		panic(err)
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-AI-Gateway-Client-Family", "GENERIC_OPENAI")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(resp.Body)
	fmt.Println(string(respBody))
}

func getenv(key string, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
