# MCP Image Search Server

A lightweight [Model Context Protocol](https://modelcontextprotocol.io/) server that
exposes the Picture Management backend as a conversational tool. The server bridges
LLM clients (e.g., Claude Desktop) with the existing `/ai/v1/search/interpret` and
`/api/images/search` endpoints, so models can search the photo library using natural
language.

## Features

- Registers a `search_images` MCP tool that accepts a natural-language `query`, an optional
  `limit`, and the `onlyOwn` switch.
- Uses the AI microservice to convert the query into structured `ImageSearchRequest`
  filters, then calls the Spring Boot backend with the caller's JWT.
- Returns both a human-readable summary and structured JSON describing the matches,
  making it easy for models to cite thumbnails, tags, and other metadata.

## Prerequisites

- Python 3.11+
- A running backend (`/api/images/search`) and AI microservice (`/ai/v1/search/interpret`).
- A valid JWT token that can access the image search API (obtain via `/api/auth/login`).

## Installation

```powershell
cd tools/mcp-image-search
python -m venv .venv
.\.venv\Scripts\activate
pip install -e .
```

## Configuration

You can pass flags or set environment variables (handy when launching from
`claude_desktop_config.json`). Supported options:

| CLI Flag           | Environment Variable   | Description                                                            |
| ------------------ | ---------------------- | ---------------------------------------------------------------------- |
| `--api-base-url`   | `PICTURE_API_BASE_URL` | Base URL of the Spring Boot backend (default `http://localhost:8080`). |
| `--ai-service-url` | `PICTURE_AI_BASE_URL`  | Base URL of the Flask AI service (default `http://localhost:5000`).    |
| `--api-token`      | `PICTURE_API_TOKEN`    | JWT used for backend authentication (required).                        |
| `--timeout`        | `PICTURE_API_TIMEOUT`  | HTTP timeout in seconds (default `12`).                                |
| `--debug`          | `PICTURE_MCP_DEBUG`    | Enable verbose logging (`true`/`false`).                               |

Example manual launch:

```powershell
PICTURE_API_TOKEN="<your JWT>" \
PICTURE_API_BASE_URL="http://localhost:8080" \
PICTURE_AI_BASE_URL="http://localhost:5000" \
python picture_mcp_server.py --debug
```

## Claude Desktop integration

Add an entry similar to the following inside `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "picture-images": {
      "command": "python",
      "args": ["tools/mcp-image-search/picture_mcp_server.py"],
      "env": {
        "PICTURE_API_TOKEN": "<jwt>",
        "PICTURE_API_BASE_URL": "http://localhost:8080",
        "PICTURE_AI_BASE_URL": "http://localhost:5000"
      }
    }
  }
}
```

Once enabled, the `search_images` tool becomes available in Claude's tool palette.
The model can then ask follow-up questions, refine filters, or request thumbnails
based on the structured response provided by the server.
