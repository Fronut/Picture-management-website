from __future__ import annotations

import argparse
import json
import logging
import os
import re
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Tuple

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
    api_token: str
    timeout: float


CONFIG: ConnectorConfig | None = None

server = Server(
    name="picture-mcp-image-search",
    version="0.1.0",
    instructions=(
        "Use existing tags to find photos. Filenames/descriptions may be unrelated, so prefer listing available tags "
        "and searching by them. Query text is optional and may not match content."
    ),
    website_url="https://github.com/Fronut/Picture-management-website",
)


SEARCH_FILTER_OPTIONS = {
    "privacyLevel": ["PUBLIC", "PRIVATE"],
    "sortBy": ["uploadTime", "originalFilename", "fileSize", "width", "height"],
    "sortDirection": ["ASC", "DESC"],
}

AVAILABLE_TAGS: List[str] = []

SEARCH_TOOL = types.Tool(
    name="search_images",
    description=(
        "Search the Picture Management library. Prefer explicit tags over filenames/descriptions. "
        "Query text is optional and may not match content; tags are the primary filter."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": (
                    "Optional free-text hint. Filenames may be unrelated to content, so prefer tags from the library."
                ),
            },
            "limit": {
                "type": "integer",
                "minimum": 1,
                "maximum": 20,
                "default": 5,
                "description": "Maximum number of images to return (maps to backend size, capped at 20).",
            },
            "page": {
                "type": "integer",
                "minimum": 0,
                "default": 0,
                "description": "Zero-based page index (default 0).",
            },
            "onlyOwn": {
                "type": "boolean",
                "description": "If true, restricts matches to the authenticated user's uploads only.",
            },
            "privacyLevel": {
                "type": "string",
                "enum": SEARCH_FILTER_OPTIONS["privacyLevel"],
                "description": "Image visibility filter (PUBLIC or PRIVATE).",
            },
            "tags": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Tag names to match (array of strings).",
            },
            "uploadedFrom": {
                "type": "string",
                "description": "ISO-8601 datetime lower bound (e.g. 2024-01-01T00:00:00).",
            },
            "uploadedTo": {
                "type": "string",
                "description": "ISO-8601 datetime upper bound (e.g. 2024-12-31T23:59:59).",
            },
            "cameraMake": {"type": "string", "description": "Camera make to match (e.g. Canon, Nikon)."},
            "cameraModel": {"type": "string", "description": "Camera model to match (e.g. EOS R6)."},
            "minWidth": {"type": "integer", "minimum": 1, "description": "Minimum width in pixels."},
            "minHeight": {"type": "integer", "minimum": 1, "description": "Minimum height in pixels."},
            "maxWidth": {"type": "integer", "minimum": 1, "description": "Maximum width in pixels."},
            "maxHeight": {"type": "integer", "minimum": 1, "description": "Maximum height in pixels."},
            "sortBy": {
                "type": "string",
                "enum": SEARCH_FILTER_OPTIONS["sortBy"],
                "description": "Sort field (uploadTime|originalFilename|fileSize|width|height).",
            },
            "sortDirection": {
                "type": "string",
                "enum": SEARCH_FILTER_OPTIONS["sortDirection"],
                "description": "Sort direction (ASC or DESC).",
            },
        },
        "required": [],
        "additionalProperties": False,
    },
)

AVAILABLE_TAGS_TOOL = types.Tool(
    name="list_available_tags",
    description=(
        "Return tags that already exist on images. Use these tags for searching; avoid relying on free-text queries."
    ),
    inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
)

PARAMETERS_TOOL = types.Tool(
    name="describe_search_parameters",
    description="Return the available search filters, meanings, and allowed values for image search.",
    inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
)


@server.list_tools()
async def _list_tools(_: types.ListToolsRequest | None = None) -> types.ListToolsResult:
    return types.ListToolsResult(tools=[SEARCH_TOOL, PARAMETERS_TOOL, AVAILABLE_TAGS_TOOL])


@server.call_tool()
async def _call_tool(name: str, arguments: dict | None) -> tuple[List[types.ContentBlock], Dict[str, Any]]:
    payload = arguments or {}

    if name == PARAMETERS_TOOL.name:
        tags = await fetch_available_tags()
        guide = describe_parameters(tags)
        guide_block = types.TextContent(type="text", text=guide)
        return [guide_block], {"description": guide, "options": {**SEARCH_FILTER_OPTIONS, "tags": tags}}

    if name == AVAILABLE_TAGS_TOOL.name:
        tags = await fetch_available_tags()
        preview = ", ".join(tags[:50]) if tags else "(no tags available yet)"
        text = f"Available tags ({len(tags)} total). Sample: {preview}"
        return [types.TextContent(type="text", text=text)], {"tags": tags, "count": len(tags)}

    if name != SEARCH_TOOL.name:
        raise ValueError(f"Unknown tool '{name}'")

    result = await execute_search(payload)
    summary_block = types.TextContent(type="text", text=result["summary"])
    diagnostic_block = types.TextContent(
        type="text",
        text=json.dumps(
            {
                "query": result["query"],
                "requestedLimit": result["requestedLimit"],
                "onlyOwn": result["onlyOwn"],
                "filters": result["filters"],
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
        "filters": result["filters"],
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
    if not api_base:
        raise SystemExit("--api-base-url cannot be empty")
    global CONFIG
    CONFIG = ConnectorConfig(
        api_base_url=api_base,
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


def _coerce_optional_int(value: Any, minimum: int | None = None, maximum: int | None = None) -> int | None:
    try:
        if value is None:
            return None
        parsed = int(value)
        if minimum is not None:
            parsed = max(minimum, parsed)
        if maximum is not None:
            parsed = min(maximum, parsed)
        return parsed
    except (TypeError, ValueError):
        return None


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


def _coerce_tags(value: Any) -> List[str] | None:
    if value is None:
        return None
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        return [part.strip() for part in value.split(",") if part.strip()]
    return None


def _coerce_enum(value: Any, options: List[str]) -> str | None:
    if value is None:
        return None
    candidate = str(value).strip().upper()
    return candidate if candidate in options else None


def _dedup_preserve_order(items: Iterable[str]) -> List[str]:
    seen = set()
    result: List[str] = []
    for item in items:
        key = item.lower()
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result


def _match_query_tags(query: str, available_tags: List[str]) -> List[str]:
    if not query or not available_tags:
        return []
    available_map = {tag.lower(): tag for tag in available_tags}
    tokens = [token for token in re.split(r"[^A-Za-z0-9:_]+", query) if token]
    matches = []
    for token in tokens:
        resolved = available_map.get(token.lower())
        if resolved:
            matches.append(resolved)
    return _dedup_preserve_order(matches)


def _interpret_request(
    arguments: Dict[str, Any],
    available_tags: List[str],
    limit: int,
    only_own_override: bool | None,
) -> Dict[str, Any]:
    query = (arguments.get("query") or "").strip()
    explicit_tags = _coerce_tags(arguments.get("tags")) or []
    matched_tags = _match_query_tags(query, available_tags)
    tags = _dedup_preserve_order([tag for tag in explicit_tags + matched_tags if tag])
    only_own = only_own_override if only_own_override is not None else _coerce_bool(arguments.get("onlyOwn"))

    filters = {
        "keyword": query or None,
        "tags": tags,
        "onlyOwn": only_own if only_own is not None else False,
        "page": _coerce_optional_int(arguments.get("page"), minimum=0) or 0,
        "size": limit,
        "sortBy": _coerce_enum(arguments.get("sortBy"), SEARCH_FILTER_OPTIONS["sortBy"]) or "uploadTime",
        "sortDirection": _coerce_enum(arguments.get("sortDirection"), SEARCH_FILTER_OPTIONS["sortDirection"]) or "DESC",
    }

    reasons = []
    if explicit_tags:
        reasons.append("explicit-tags")
    if matched_tags:
        reasons.append("query-matched-to-available-tags")
    if query:
        reasons.append("query-provided")
    if not reasons:
        reasons.append("minimal-filters")

    return {
        "query": query,
        "keywords": [query] if query else [],
        "tags": tags,
        "filters": filters,
        "explanations": [{"rule": reason, "reason": "applied"} for reason in reasons],
        "confidence": 1.0,
    }


def _prefer_thumbnail(thumbnails: Iterable[Dict[str, Any]] | None) -> str | None:
    if not thumbnails:
        return None
    for entry in thumbnails:
        path = entry.get("filePath")
        if path:
            return str(path)
    return None


def _format_summary(query: str, interpretation: Dict[str, Any], matches: List[Dict[str, Any]], limit: int, filters: Dict[str, Any]) -> str:
    tags = ", ".join(interpretation.get("tags") or []) or "n/a"
    keywords = ", ".join(interpretation.get("keywords") or []) or "n/a"
    filter_lines = [
        f"Privacy: {filters.get('privacyLevel') or 'any'}",
        f"Tags: {', '.join(filters.get('tags') or []) or 'any'}",
        f"Time: {filters.get('uploadedFrom') or '-'} to {filters.get('uploadedTo') or '-'}",
        f"Camera: {filters.get('cameraMake') or '-'} / {filters.get('cameraModel') or '-'}",
        f"Size: min {filters.get('minWidth') or '-'}x{filters.get('minHeight') or '-'}; max {filters.get('maxWidth') or '-'}x{filters.get('maxHeight') or '-'}",
        f"Sort: {filters.get('sortBy') or 'uploadTime'} {filters.get('sortDirection') or 'DESC'}",
        f"OnlyOwn: {filters.get('onlyOwn')}",
    ]
    query_line = query or "(none; tag-first search)"
    lines = [
        f"Query: {query_line}",
        f"AI keywords: {keywords}",
        f"AI tags: {tags}",
        "Filters:",
        *[f"  - {item}" for item in filter_lines],
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
        "page": _coerce_optional_int(arguments.get("page"), minimum=0) or 0,
        "size": limit,
        "sortBy": filters.get("sortBy") or "uploadTime",
        "sortDirection": filters.get("sortDirection") or "DESC",
    }
    if only_own_override is not None:
        payload["onlyOwn"] = only_own_override

    if arguments.get("query") and not payload["keyword"]:
        payload["keyword"] = arguments["query"].strip()

    privacy = _coerce_enum(arguments.get("privacyLevel"), SEARCH_FILTER_OPTIONS["privacyLevel"])
    if privacy:
        payload["privacyLevel"] = privacy

    tags = _coerce_tags(arguments.get("tags"))
    if tags is not None:
        payload["tags"] = tags

    for date_key in ("uploadedFrom", "uploadedTo"):
        if arguments.get(date_key):
            payload[date_key] = str(arguments[date_key]).strip()

    for camera_key in ("cameraMake", "cameraModel"):
        if arguments.get(camera_key):
            payload[camera_key] = str(arguments[camera_key]).strip()

    payload["minWidth"] = _coerce_optional_int(arguments.get("minWidth"), minimum=1) or payload.get("minWidth")
    payload["minHeight"] = _coerce_optional_int(arguments.get("minHeight"), minimum=1) or payload.get("minHeight")
    payload["maxWidth"] = _coerce_optional_int(arguments.get("maxWidth"), minimum=1) or payload.get("maxWidth")
    payload["maxHeight"] = _coerce_optional_int(arguments.get("maxHeight"), minimum=1) or payload.get("maxHeight")

    sort_by = _coerce_enum(arguments.get("sortBy"), SEARCH_FILTER_OPTIONS["sortBy"])
    if sort_by:
        payload["sortBy"] = sort_by

    sort_dir = _coerce_enum(arguments.get("sortDirection"), SEARCH_FILTER_OPTIONS["sortDirection"])
    if sort_dir:
        payload["sortDirection"] = sort_dir

    return payload


def require_non_empty(value: str, label: str) -> str:
    if not value:
        raise ValueError(f"{label} cannot be empty")
    return value


async def execute_search(arguments: Dict[str, Any]) -> Dict[str, Any]:
    config = require_config()
    query = (arguments.get("query") or "").strip()
    explicit_tags = _coerce_tags(arguments.get("tags")) or []
    if not query and not explicit_tags:
        raise ValueError("Provide at least one tag or a query. Prefer tags because filenames may be unrelated.")
    limit = _coerce_int(arguments.get("limit"), default=5, minimum=1, maximum=20)
    only_own = _coerce_bool(arguments.get("onlyOwn"))
    available_tags = await fetch_available_tags()

    async with httpx.AsyncClient(timeout=config.timeout) as client:
        interpretation = _interpret_request(arguments, available_tags, limit, only_own)
        payload = _build_search_payload(arguments, interpretation, limit, only_own)
        page = await _search_backend(client, config, payload)

    matches = page.get("content", [])
    summary = _format_summary(query, interpretation, matches, limit, payload)
    return {
        "summary": summary,
        "query": query,
        "requestedLimit": limit,
        "onlyOwn": payload["onlyOwn"],
        "interpretation": interpretation,
        "searchPayload": payload,
        "page": page,
        "matches": matches,
        "filters": payload,
    }


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
        "Starting MCP server for %s (backend=%s)",
        server.name,
        CONFIG.api_base_url if CONFIG else "?",
    )
    try:
        anyio.run(_run_server)
    except KeyboardInterrupt:  # pragma: no cover
        logger.info("Server interrupted, shutting down")


if __name__ == "__main__":
    main()

async def fetch_available_tags() -> List[str]:
    global AVAILABLE_TAGS
    if AVAILABLE_TAGS:
        return AVAILABLE_TAGS
    config = require_config()
    url = f"{config.api_base_url}/api/tags/available?limit=500"
    headers = {"Authorization": f"Bearer {config.api_token}"}
    async with httpx.AsyncClient(timeout=config.timeout) as client:
        response = await client.get(url, headers=headers)
        response.raise_for_status()
        body = response.json()
        data = body.get("data") or []
        AVAILABLE_TAGS = [item.get("tagName") for item in data if item.get("tagName")]
    return AVAILABLE_TAGS


def describe_parameters(available_tags: List[str]) -> str:
    top_tags_preview = ", ".join(available_tags[:15]) if available_tags else "(none yet)"
    lines = [
        "Available filters and options (prefer tags; filenames may not describe content):",
        "- tags: array of tag strings (e.g. ['cat','sunset']); available sample: " + top_tags_preview,
        "- privacyLevel: PUBLIC | PRIVATE",
        "- uploadedFrom / uploadedTo: ISO datetime (e.g. 2024-01-01T00:00:00)",
        "- cameraMake / cameraModel: strings (e.g. Canon / EOS R6)",
        "- minWidth / minHeight / maxWidth / maxHeight: integers in pixels (min 1)",
        "- onlyOwn: boolean",
        "- page: integer, zero-based",
        "- limit: 1-20 (maps to size)",
        "- sortBy: uploadTime | originalFilename | fileSize | width | height",
        "- sortDirection: ASC | DESC",
        "Guidance: prefer tags over query text; filenames/description may be unrelated to content.",
        "Use list_available_tags to refresh the tag list before searching.",
    ]
    return "\n".join(lines)
