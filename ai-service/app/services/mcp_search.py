from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import httpx

PRIVACY_LEVELS = ["ALL", "PUBLIC", "PRIVATE"]
SORT_FIELDS = ["uploadTime", "originalFilename", "fileSize", "width", "height"]
SORT_DIRECTIONS = ["ASC", "DESC"]
MAX_PAGE_SIZE = 20
DEFAULT_PAGE_SIZE = 6
MAX_DIMENSION = 20_000


@dataclass(slots=True)
class McpSearchConfig:
    backend_api_base_url: str
    backend_api_token: Optional[str] = None
    timeout_seconds: float = 12.0


class McpSearchExecutor:
    """Lightweight executor for the picture search MCP tool.

    This mirrors the behaviour of tools/mcp-image-search without depending on
    the MCP server runtime so it can be used in-process by the AI service.
    """

    def __init__(self, config: McpSearchConfig) -> None:
        self.config = config
        self.client = httpx.Client(timeout=config.timeout_seconds)
        self._available_tags: List[str] = []

    def close(self) -> None:  # pragma: no cover - small helper
        self.client.close()

    def get_search_options(self, *, refresh: bool = False) -> Dict[str, Any]:
        if refresh:
            self._available_tags = []
        tags = self._load_available_tags()
        return {
            "tags": tags,
            "privacyLevels": PRIVACY_LEVELS,
            "sortBy": SORT_FIELDS,
            "sortDirections": SORT_DIRECTIONS,
            "sizeRange": {"min": 1, "max": MAX_PAGE_SIZE},
            "numericFilters": {
                "width": {"min": 0, "max": MAX_DIMENSION},
                "height": {"min": 0, "max": MAX_DIMENSION},
            },
        }

    def search_images(self, *, filters: Dict[str, Any], auth_token: Optional[str] = None) -> Dict[str, Any]:
        if not isinstance(filters, dict):
            raise ValueError("filters must be an object")
        payload = self._normalize_filters(filters)
        page = self._search_backend(payload, auth_token)
        matches = page.get("content", [])
        summary = self._format_summary(payload, matches)
        interpretation = self._build_interpretation(payload)
        return {
            "summary": summary,
            "query": payload.get("keyword") or "",
            "requestedLimit": payload.get("size"),
            "onlyOwn": payload.get("onlyOwn"),
            "interpretation": interpretation,
            "searchPayload": payload,
            "page": page,
            "matches": matches,
        }

    def _normalize_filters(self, filters: Dict[str, Any]) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        payload["keyword"] = self._clean_string(filters.get("keyword"))

        privacy = self._clean_string(filters.get("privacyLevel"))
        if privacy and privacy.upper() in {"PUBLIC", "PRIVATE"}:
            payload["privacyLevel"] = privacy.upper()
        else:
            payload["privacyLevel"] = None

        payload["tags"] = self._normalize_incoming_tags(filters.get("tags"))
        payload["uploadedFrom"] = self._clean_string(filters.get("uploadedFrom"))
        payload["uploadedTo"] = self._clean_string(filters.get("uploadedTo"))
        payload["cameraMake"] = self._clean_string(filters.get("cameraMake"))
        payload["cameraModel"] = self._clean_string(filters.get("cameraModel"))

        for key in ("minWidth", "maxWidth", "minHeight", "maxHeight"):
            payload[key] = self._sanitize_dimension(filters.get(key))

        size = self._coerce_int(filters.get("size"))
        if size is None:
            size = DEFAULT_PAGE_SIZE
        payload["size"] = max(1, min(MAX_PAGE_SIZE, size))

        page = self._coerce_int(filters.get("page"))
        payload["page"] = page if page is not None and page >= 0 else 0

        only_own = self._coerce_bool(filters.get("onlyOwn"))
        payload["onlyOwn"] = only_own if only_own is not None else False

        sort_by = self._clean_string(filters.get("sortBy"))
        payload["sortBy"] = sort_by if sort_by in SORT_FIELDS else "uploadTime"

        sort_dir = self._clean_string(filters.get("sortDirection"))
        sort_dir = sort_dir.upper() if sort_dir else None
        payload["sortDirection"] = sort_dir if sort_dir in SORT_DIRECTIONS else "DESC"

        return payload

    def _build_interpretation(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        keyword = payload.get("keyword") or ""
        tags = payload.get("tags") or []
        return {
            "query": keyword,
            "keywords": [keyword] if keyword else [],
            "tags": tags,
            "filters": payload,
            "explanations": [
                {
                    "rule": "llm-direct-filters",
                    "reason": "Filters supplied directly by the copilot",
                }
            ],
            "confidence": 1.0,
        }

    def _search_backend(self, payload: Dict[str, Any], auth_token: Optional[str]) -> Dict[str, Any]:
        url = f"{self.config.backend_api_base_url}/api/images/search"
        headers: Dict[str, str] = {
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        auth_header = self._resolve_auth_header(auth_token)
        if auth_header:
            headers["Authorization"] = auth_header
        response = self.client.post(url, headers=headers, json=payload)
        response.raise_for_status()
        body = response.json()
        if body.get("code") != 200:
            raise RuntimeError(body.get("message") or "Backend returned non-success response")
        data = body.get("data")
        if not isinstance(data, dict):
            raise RuntimeError("Backend returned malformed page response")
        return data

    def _load_available_tags(self) -> List[str]:
        if self._available_tags:
            return self._available_tags
        url = f"{self.config.backend_api_base_url}/api/tags/available?limit=500"
        response = self.client.get(url)
        response.raise_for_status()
        body = response.json()
        data = body.get("data") or []
        tags = [item.get("tagName") for item in data if item.get("tagName")]
        self._available_tags = tags
        return tags

    def _normalize_incoming_tags(self, raw_tags: Any) -> List[str]:
        if not isinstance(raw_tags, list):
            return []
        available_map = {tag.lower(): tag for tag in self._load_available_tags()}
        normalized: List[str] = []
        for tag in raw_tags:
            if not isinstance(tag, str):
                continue
            cleaned = tag.strip()
            if not cleaned:
                continue
            resolved = available_map.get(cleaned.lower()) or cleaned
            if resolved not in normalized:
                normalized.append(resolved)
        return normalized

    @staticmethod
    def _clean_string(value: Any) -> Optional[str]:
        if value is None:
            return None
        if isinstance(value, str):
            stripped = value.strip()
            return stripped or None
        coerced = str(value).strip()
        return coerced or None

    @staticmethod
    def _coerce_int(value: Any) -> Optional[int]:
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _coerce_bool(value: Any) -> Optional[bool]:
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            lowered = value.strip().lower()
            if lowered in {"true", "1", "yes", "y"}:
                return True
            if lowered in {"false", "0", "no", "n"}:
                return False
        return None

    def _sanitize_dimension(self, value: Any) -> Optional[int]:
        number = self._coerce_int(value)
        if number is None or number <= 0:
            return None
        return min(number, MAX_DIMENSION)

    @staticmethod
    def _format_summary(filters: Dict[str, Any], matches: List[Dict[str, Any]]) -> str:
        tags = ", ".join(filters.get("tags") or []) or "n/a"
        keyword = filters.get("keyword") or "n/a"
        privacy = filters.get("privacyLevel") or "n/a"
        only_own = "是" if filters.get("onlyOwn") else "否"
        lines = [
            f"关键词: {keyword}",
            f"标签: {tags}",
            f"隐私: {privacy} · 仅看自己: {only_own}",
            f"分页: page {filters.get('page')}, size {filters.get('size')}, 排序: {filters.get('sortBy')} {filters.get('sortDirection')}",
            f"结果: {len(matches)}",
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

    def _resolve_auth_header(self, auth_token: Optional[str]) -> Optional[str]:
        token = auth_token or self.config.backend_api_token
        if not token:
            return None
        token = token.strip()
        if not token:
            return None
        return token if token.lower().startswith("bearer ") else f"Bearer {token}"

    # No auth headers needed; backend endpoints are open in this environment.


class StubMcpSearchExecutor(McpSearchExecutor):
    """Test double that avoids HTTP calls."""

    def __init__(self, result: Dict[str, Any]):
        # Do not call parent constructor (no network)
        self.config = None  # type: ignore[assignment]
        self.client = None  # type: ignore[assignment]
        self._result = result

    def search_images(
        self,
        *,
        filters: Dict[str, Any],
        auth_token: Optional[str] = None,
    ) -> Dict[str, Any]:
        result = json.loads(json.dumps(self._result))
        result.setdefault("searchPayload", {}).update(filters)
        result.setdefault("requestedLimit", filters.get("size"))
        result.setdefault("onlyOwn", filters.get("onlyOwn"))
        return result