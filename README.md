# Picture-management-website

Zhejiang University 2025 B/S Course Project

## AI Service Configuration

- The backend expects the AI microservice to be reachable via the `AI_SERVICE_URL` environment variable (defaults to `http://ai-service:5000`, which is wired up when running with Docker Compose).
- When running the Spring Boot backend locally, export `AI_SERVICE_URL` to point to your running `ai-service` instance, e.g. `http://localhost:5000`, to enable the new AI-assisted endpoints.
- The AI microservice now combines a CLIP-based zero-shot vision model with heuristics to produce multi-type tags (scene, subject, mood, etc.). Tune it via `ENABLE_VISION_MODEL`, `VISION_MODEL_ID`, and related env vars inside `ai-service`.

## Conversational MCP Access

- The `tools/mcp-image-search` folder contains a standalone Model Context Protocol server that exposes the photo search endpoints to LLM clients (e.g., Claude Desktop).
- Configure it with `PICTURE_API_BASE_URL`, `PICTURE_AI_BASE_URL`, and `PICTURE_API_TOKEN`, then register the `search_images` tool inside your MCP-compatible client to let models retrieve photos through natural dialogue.
