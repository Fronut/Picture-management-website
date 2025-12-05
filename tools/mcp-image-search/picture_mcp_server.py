from __future__ import annotations

import argparse
import json
import logging
import os
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List

import anyio
import httpx
from dotenv import load_dotenv

import mcp.types as types
from mcp.server import NotificationOptions, Server
from mcp.server.stdio import stdio_server

load_dotenv()

logger = logging.getLogger("picture_mcp_server")


def _env_bool(key: str, default: bool) -> bool:
    raw = os.getenv(key)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(slots=True)
class ConnectorConfig:
    api_base_url: str
    ai_base_url: str
    api_token: str
    timeout: float


CONFIG: ConnectorConfig | None = None

server = Server(
    name="picture-mcp-image-search",
    version="0.1.0",
    instructions="Use the search_images tool to describe a desired photo and retrieve matches from the Picture Management backend.",
    website_url="https://github.com/Fronut/Picture-management-website",
)


SEARCH_TOOL = types.Tool(
    name="search_images",
    description=(
        "Search the Picture Management library using natural language. "
        "The tool calls the AI intent interpreter and backend /api/images/search endpoint "
        "and returns the top matches with metadata."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Natural-language description of the desired photo (e.g. 'sunset beach in portrait mode').",
            },
            "limit": {
                "type": "integer",
                "minimum": 1,
                "maximum": 20,
                "default": 5,
                "description": "Maximum number of images to return (capped at 20).",
            },
            "onlyOwn": {
                "type": "boolean",
                "description": "If true, restricts matches to the authenticated user's uploads only.",
            },
        },
        "required": ["query"],
        "additionalProperties": False,
    },
)


@server.list_tools()
async def _list_tools(_: types.ListToolsRequest | None = None) -> types.ListToolsResult:
    return types.ListToolsResult(tools=[SEARCH_TOOL])


@server.call_tool()
async def _call_tool(name: str, arguments: dict | None) -> tuple[List[types.ContentBlock], Dict[str, Any]]:
    if name != SEARCH_TOOL.name:
        raise ValueError(f"Unknown tool '{name}'")
    payload = arguments or {}
    result = await execute_search(payload)
    summary_block = types.TextContent(type="text", text=result["summary"])
    diagnostic_block = types.TextContent(
        type="text",
        text=json.dumps(
            {
                "query": result["query"],
                "requestedLimit": result["requestedLimit"],
                "onlyOwn": result["onlyOwn"],
                "aiTags": result["interpretation"].get("tags", []),
                "aiKeywords": result["interpretation"].get("keywords", []),
                "matchCount": len(result["matches"]),
            },
            ensure_ascii=False,
            indent=2,
        ),
    )
    return [summary_block, diagnostic_block], {
        "summary": result["summary"],
        "query": result["query"],
        "requestedLimit": result["requestedLimit"],
        "onlyOwn": result["onlyOwn"],
        "interpretation": result["interpretation"],
        "searchPayload": result["searchPayload"],
        "page": result["page"],
        "matches": result["matches"],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="MCP server bridge for Picture Management image search")
    parser.add_argument(
        "--api-base-url",
        default=os.getenv("PICTURE_API_BASE_URL", "http://localhost:8080"),
        help="Base URL of the Spring Boot backend (default: http://localhost:8080)",
    )
    parser.add_argument(
        "--ai-service-url",
        default=os.getenv("PICTURE_AI_BASE_URL", "http://localhost:5000"),
        help="Base URL of the ai-service Flask app (default: http://localhost:5000)",
    )
    parser.add_argument(
        "--api-token",
        default=os.getenv("PICTURE_API_TOKEN"),
        help="JWT token used to authenticate against the backend. Required.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=float(os.getenv("PICTURE_API_TIMEOUT", "12")),
        help="HTTP timeout in seconds (default: 12)",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        default=_env_bool("PICTURE_MCP_DEBUG", False),
        help="Enable verbose logging",
    )
    return parser.parse_args()


def configure_runtime(args: argparse.Namespace) -> None:
    token = (args.api_token or "").strip()
    if not token:
        raise SystemExit("PICTURE_API_TOKEN (or --api-token) is required for authenticated search calls.")
    api_base = _normalize_base(args.api_base_url)
    ai_base = _normalize_base(args.ai_service_url)
    if not api_base:
        raise SystemExit("--api-base-url cannot be empty")
    if not ai_base:
        raise SystemExit("--ai-service-url cannot be empty")
    global CONFIG
    CONFIG = ConnectorConfig(
        api_base_url=api_base,
        ai_base_url=ai_base,
        api_token=token,
        timeout=max(3.0, float(args.timeout or 12)),
    )
    level = logging.DEBUG if args.debug else logging.INFO
    logging.basicConfig(level=level, format="[%(levelname)s] %(message)s")


def _normalize_base(url: str) -> str:
    value = (url or "").strip()
    return value[:-1] if value.endswith("/") else value


def require_config() -> ConnectorConfig:
    if CONFIG is None:
        raise RuntimeError("Server configuration not initialized. Call configure_runtime first.")
    return CONFIG


def _coerce_int(value: Any, default: int, minimum: int, maximum: int) -> int:
    try:
        if value is None:
            return default
        parsed = int(value)
        return max(minimum, min(maximum, parsed))
    except (TypeError, ValueError):
        return default


def _coerce_bool(value: Any) -> bool | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        lowered = value.strip().lower()
        if lowered in {"1", "true", "yes", "on"}:
            return True
        if lowered in {"0", "false", "no", "off"}:
            return False
    return None


def _prefer_thumbnail(thumbnails: Iterable[Dict[str, Any]] | None) -> str | None:
    if not thumbnails:
        return None
    for entry in thumbnails:
        path = entry.get("filePath")
        if path:
            return str(path)
    return None


def _format_summary(query: str, interpretation: Dict[str, Any], matches: List[Dict[str, Any]], limit: int) -> str:
    tags = ", ".join(interpretation.get("tags") or []) or "n/a"
    keywords = ", ".join(interpretation.get("keywords") or []) or "n/a"
    lines = [
        f"Query: {query}",
        f"AI keywords: {keywords}",
        f"AI tags: {tags}",
        f"Results: {len(matches)} of requested {limit}",
    ]
    for idx, image in enumerate(matches, start=1):
        desc = image.get("description") or image.get("originalFilename") or f"image {image.get('id')}"
        width = image.get("width") or "?"
        height = image.get("height") or "?"
        privacy = image.get("privacyLevel") or "UNKNOWN"
        lines.append(f"{idx}. #{image.get('id')} · {desc} · {width}x{height} · {privacy}")
        if image.get("tags"):
            lines.append(f"   tags: {', '.join(image['tags'])}")
        thumb = _prefer_thumbnail(image.get("thumbnails"))
        if thumb:
            lines.append(f"   thumbnail: {thumb}")
    if not matches:
        lines.append("No images matched the inferred filters.")
    return "\n".join(lines)


def _build_search_payload(
    arguments: Dict[str, Any],
    interpretation: Dict[str, Any],
    limit: int,
    only_own_override: bool | None,
) -> Dict[str, Any]:
    filters = interpretation.get("filters") or {}
    payload = {
        "keyword": filters.get("keyword"),
        "privacyLevel": filters.get("privacyLevel"),
        "tags": filters.get("tags") or [],
        "uploadedFrom": filters.get("uploadedFrom"),
        "uploadedTo": filters.get("uploadedTo"),
        "cameraMake": filters.get("cameraMake"),
        "cameraModel": filters.get("cameraModel"),
        "minWidth": filters.get("minWidth"),
        "minHeight": filters.get("minHeight"),
        "maxWidth": filters.get("maxWidth"),
        "maxHeight": filters.get("maxHeight"),
        "onlyOwn": filters.get("onlyOwn", False),
        "page": 0,
        "size": limit,
        "sortBy": filters.get("sortBy") or "uploadTime",
        "sortDirection": filters.get("sortDirection") or "DESC",
    }
    if only_own_override is not None:
        payload["onlyOwn"] = only_own_override
    if arguments.get("query") and not payload["keyword"]:
        payload["keyword"] = arguments["query"].strip()
    return payload


def _fallback_interpretation(query: str) -> Dict[str, Any]:
    return {
        "query": query,
        "keywords": [query],
        "tags": [],
        "filters": {"keyword": query, "tags": []},
        "explanations": [{"rule": "fallback", "reason": "AI service unavailable"}],
        "confidence": 0.0,
    }


def require_non_empty(value: str, label: str) -> str:
    if not value:
        raise ValueError(f"{label} cannot be empty")
    return value


async def execute_search(arguments: Dict[str, Any]) -> Dict[str, Any]:
    config = require_config()
    query = (arguments.get("query") or "").strip()
    if not query:
        raise ValueError("search_images.query is required")
    limit = _coerce_int(arguments.get("limit"), default=5, minimum=1, maximum=20)
    only_own = _coerce_bool(arguments.get("onlyOwn"))

    async with httpx.AsyncClient(timeout=config.timeout) as client:
        interpretation: Dict[str, Any]
        try:
            interpretation = await _interpret_query(client, config.ai_base_url, query, limit)
        except Exception as exc:  # pragma: no cover - informational fallback
            logger.warning("Falling back to keyword-only filters: %s", exc)
            interpretation = _fallback_interpretation(query)
        payload = _build_search_payload(arguments, interpretation, limit, only_own)
        page = await _search_backend(client, config, payload)

    matches = page.get("content", [])
    summary = _format_summary(query, interpretation, matches, limit)
    return {
        "summary": summary,
        "query": query,
        "requestedLimit": limit,
        "onlyOwn": payload["onlyOwn"],
        "interpretation": interpretation,
        "searchPayload": payload,
        "page": page,
        "matches": matches,
    }


async def _interpret_query(client: httpx.AsyncClient, ai_base_url: str, query: str, limit: int) -> Dict[str, Any]:
    url = f"{ai_base_url}/ai/v1/search/interpret"
    response = await client.post(url, json={"query": query, "limit": limit})
    response.raise_for_status()
    payload = response.json()
    if payload.get("status") != "ok":
        raise RuntimeError(payload.get("message") or "AI service returned error response")
    data = payload.get("data")
    if not isinstance(data, dict):
        raise RuntimeError("AI service returned malformed payload")
    return data


async def _search_backend(client: httpx.AsyncClient, config: ConnectorConfig, payload: Dict[str, Any]) -> Dict[str, Any]:
    url = f"{config.api_base_url}/api/images/search"
    headers = {
        "Authorization": f"Bearer {config.api_token}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    response = await client.post(url, headers=headers, json=payload)
    response.raise_for_status()
    body = response.json()
    if body.get("code") != 200:
        raise RuntimeError(body.get("message") or "Backend returned non-success response")
    data = body.get("data")
    if not isinstance(data, dict):
        raise RuntimeError("Backend returned malformed page response")
    return data


async def _run_server() -> None:
    init_options = server.create_initialization_options(NotificationOptions(), {})
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, init_options)


def main() -> None:
    args = parse_args()
    configure_runtime(args)
    logger.info(
        "Starting MCP server for %s (backend=%s, ai=%s)",
        server.name,
        CONFIG.api_base_url if CONFIG else "?",
        CONFIG.ai_base_url if CONFIG else "?",
    )
    try:
        anyio.run(_run_server)
    except KeyboardInterrupt:  # pragma: no cover
        logger.info("Server interrupted, shutting down")


if __name__ == "__main__":
    main()
