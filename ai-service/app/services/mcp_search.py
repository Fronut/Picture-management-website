from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import httpx


@dataclass(slots=True)
class McpSearchConfig:
    backend_api_base_url: str
    backend_api_token: str
    ai_service_base_url: str
    timeout_seconds: float = 12.0


class McpSearchExecutor:
    """Lightweight executor for the picture search MCP tool.

    This mirrors the behaviour of tools/mcp-image-search without depending on
    the MCP server runtime so it can be used in-process by the AI service.
    """

    def __init__(self, config: McpSearchConfig) -> None:
        if not config.backend_api_token:
            raise ValueError("BACKEND_API_TOKEN is required for MCP search")
        self.config = config
        self.client = httpx.Client(timeout=config.timeout_seconds)

    def close(self) -> None:  # pragma: no cover - small helper
        self.client.close()

    def search_images(self, query: str, *, limit: int = 5, only_own: Optional[bool] = None) -> Dict[str, Any]:
        query = (query or "").strip()
        if not query:
            raise ValueError("query is required")
        limit = max(1, min(20, int(limit or 5)))
        interpretation = self._interpret_query(query, limit)
        search_payload = self._build_search_payload(query, interpretation, limit, only_own)
        page = self._search_backend(search_payload)
        matches = page.get("content", [])
        summary = self._format_summary(query, interpretation, matches, limit)
        return {
            "summary": summary,
            "query": query,
            "requestedLimit": limit,
            "onlyOwn": search_payload.get("onlyOwn", False),
            "interpretation": interpretation,
            "searchPayload": search_payload,
            "page": page,
            "matches": matches,
        }

    def _interpret_query(self, query: str, limit: int) -> Dict[str, Any]:
        url = f"{self.config.ai_service_base_url}/ai/v1/search/interpret"
        response = self.client.post(url, json={"query": query, "limit": limit})
        response.raise_for_status()
        payload = response.json()
        if payload.get("status") != "ok":
            raise RuntimeError(payload.get("message") or "AI service returned error response")
        data = payload.get("data")
        if not isinstance(data, dict):
            raise RuntimeError("AI service returned malformed payload")
        return data

    def _search_backend(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        url = f"{self.config.backend_api_base_url}/api/images/search"
        headers = {
            "Authorization": f"Bearer {self.config.backend_api_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        response = self.client.post(url, headers=headers, json=payload)
        response.raise_for_status()
        body = response.json()
        if body.get("code") != 200:
            raise RuntimeError(body.get("message") or "Backend returned non-success response")
        data = body.get("data")
        if not isinstance(data, dict):
            raise RuntimeError("Backend returned malformed page response")
        return data

    @staticmethod
    def _build_search_payload(
        query: str,
        interpretation: Dict[str, Any],
        limit: int,
        only_own_override: Optional[bool],
    ) -> Dict[str, Any]:
        filters = interpretation.get("filters") or {}
        payload: Dict[str, Any] = {
            "keyword": filters.get("keyword") or query,
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
        return payload

    @staticmethod
    def _format_summary(
        query: str,
        interpretation: Dict[str, Any],
        matches: List[Dict[str, Any]],
        limit: int,
    ) -> str:
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
            thumb = McpSearchExecutor._prefer_thumbnail(image.get("thumbnails"))
            if thumb:
                lines.append(f"   thumbnail: {thumb}")
        if not matches:
            lines.append("No images matched the inferred filters.")
        return "\n".join(lines)

    @staticmethod
    def _prefer_thumbnail(thumbnails: Optional[List[Dict[str, Any]]]) -> Optional[str]:
        if not thumbnails:
            return None
        for entry in thumbnails:
            path = entry.get("filePath")
            if path:
                return str(path)
        return None


class StubMcpSearchExecutor(McpSearchExecutor):
    """Test double that avoids HTTP calls."""

    def __init__(self, result: Dict[str, Any]):
        # Do not call parent constructor (no network)
        self.config = None  # type: ignore[assignment]
        self.client = None  # type: ignore[assignment]
        self._result = result

    def search_images(self, query: str, *, limit: int = 5, only_own: Optional[bool] = None) -> Dict[str, Any]:
        result = json.loads(json.dumps(self._result))
        result["query"] = query
        result["requestedLimit"] = limit
        if only_own is not None:
            result["onlyOwn"] = only_own
        return result
*** End Patch