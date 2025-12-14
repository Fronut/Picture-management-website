from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from openai import OpenAI

from .mcp_search import (
    MAX_PAGE_SIZE,
    McpSearchExecutor,
    PRIVACY_LEVELS,
    SORT_DIRECTIONS,
    SORT_FIELDS,
)

LIST_OPTIONS_TOOL_NAME = "list_search_options"
SEARCH_TOOL_NAME = "search_images"

SEARCH_FILTER_PROPERTIES: Dict[str, Any] = {
    "keyword": {
        "type": "string",
        "description": "Filename/description keyword filter. This does NOT understand image content.",
    },
    "privacyLevel": {
        "type": "string",
        "enum": PRIVACY_LEVELS,
        "description": "Use ALL (default), PUBLIC, or PRIVATE.",
    },
    "tags": {
        "type": "array",
        "items": {"type": "string"},
        "description": "Image tags. Call list_search_options to inspect canonical tags and reuse them verbatim.",
    },
    "uploadedFrom": {
        "type": "string",
        "description": "ISO timestamp (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS) inclusive lower bound.",
    },
    "uploadedTo": {
        "type": "string",
        "description": "ISO timestamp inclusive upper bound.",
    },
    "cameraMake": {"type": "string"},
    "cameraModel": {"type": "string"},
    "minWidth": {"type": "integer", "minimum": 1, "maximum": 20000},
    "maxWidth": {"type": "integer", "minimum": 1, "maximum": 20000},
    "minHeight": {"type": "integer", "minimum": 1, "maximum": 20000},
    "maxHeight": {"type": "integer", "minimum": 1, "maximum": 20000},
    "onlyOwn": {
        "type": "boolean",
        "description": "True to restrict results to the authenticated user's uploads.",
    },
    "page": {"type": "integer", "minimum": 0},
    "size": {"type": "integer", "minimum": 1, "maximum": MAX_PAGE_SIZE},
    "sortBy": {"type": "string", "enum": SORT_FIELDS},
    "sortDirection": {"type": "string", "enum": SORT_DIRECTIONS},
}

DEEPSEEK_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": LIST_OPTIONS_TOOL_NAME,
            "description": "Fetch all available search filters, especially canonical tag names. Call this before constructing filters when uncertain.",
            "parameters": {
                "type": "object",
                "properties": {
                    "refresh": {
                        "type": "boolean",
                        "description": "Set true to refresh the cached tag list if it might be stale.",
                    }
                },
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": SEARCH_TOOL_NAME,
            "description": (
                "Execute an image search by providing explicit filters. "
                "Semantic intent must be encoded via tags; keyword only matches filenames/descriptions."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "filters": {
                        "type": "object",
                        "description": "Full search payload matching the backend schema.",
                        "properties": SEARCH_FILTER_PROPERTIES,
                        "additionalProperties": False,
                    }
                },
                "required": ["filters"],
                "additionalProperties": False,
            },
        },
    },
]


@dataclass(slots=True)
class DeepseekConfig:
    api_key: str
    base_url: str
    model: str
    timeout_seconds: float = 15.0


class DeepseekClient:
    def __init__(self, config: DeepseekConfig) -> None:
        if not config.api_key:
            raise ValueError("DEEPSEEK_API_KEY is required")
        self.config = config
        self._client = OpenAI(api_key=config.api_key, base_url=config.base_url)

    def chat(self, messages: List[Dict[str, Any]], tools: Optional[List[Dict[str, Any]]] = None, tool_choice: str | Dict[str, Any] | None = "auto"):
        return self._client.chat.completions.create(
            model=self.config.model,
            messages=messages,
            tools=tools,
            tool_choice=tool_choice,
            temperature=0.2,
            timeout=self.config.timeout_seconds,
        )


class DeepseekSearchOrchestrator:
    def __init__(self, client: DeepseekClient, mcp_executor: McpSearchExecutor) -> None:
        self.client = client
        self.mcp_executor = mcp_executor
        self.system_prompt = (
            "You are an image search copilot for a photo management site. "
            "Rely on the provided tools only: list_search_options to learn canonical tags/filters, "
            "and search_images to execute the query with an explicit filters object. "
            "Tags carry semantic meaning; keyword matches filenames/descriptions only. "
            "Never invent unsupported fields, never guess results, and always answer in concise Chinese."
        )

    def run_chat_search(
        self,
        *,
        query: str,
        limit: int = 6,
        only_own: Optional[bool] = None,
        history: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        if not query.strip():
            raise ValueError("query is required")
        messages: List[Dict[str, Any]] = [{"role": "system", "content": self.system_prompt}]
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": query})

        first = self.client.chat(messages=messages, tools=DEEPSEEK_TOOLS, tool_choice="auto")
        choice = first.choices[0].message
        tool_calls = choice.tool_calls or []
        tool_call_records = [self._serialize_tool_call(call) for call in tool_calls]
        if not tool_calls:
            raise ValueError("Deepseek 未调用任何工具，无法完成搜索。请重试。")

        messages.append({"role": "assistant", "content": choice.content or "", "tool_calls": tool_calls})

        search_results: List[Dict[str, Any]] = []
        for call in tool_calls:
            args = self._safe_parse_args(call.function.arguments)
            result = self._execute_tool_call(
                name=getattr(call.function, "name", None),
                args=args,
                limit=limit,
                only_own=only_own,
            )
            if getattr(call.function, "name", None) == SEARCH_TOOL_NAME:
                search_results.append(result)
            messages.append(
                {
                    "role": "tool",
                    "tool_call_id": call.id,
                    "name": call.function.name,
                    "content": json.dumps(result, ensure_ascii=False),
                }
            )

        if not search_results:
            raise ValueError("Deepseek 没有执行 search_images 调用，无法返回结果。")

        follow_up = self.client.chat(messages=messages, tools=DEEPSEEK_TOOLS, tool_choice="none")
        final_message = follow_up.choices[0].message
        primary_result = search_results[-1]
        return {
            "message": final_message.content or "",
            "toolCalls": tool_call_records,
            "results": search_results,
            "primaryResult": primary_result,
        }

    def _execute_tool_call(
        self,
        *,
        name: Optional[str],
        args: Dict[str, Any],
        limit: int,
        only_own: Optional[bool],
    ) -> Dict[str, Any]:
        if name == LIST_OPTIONS_TOOL_NAME:
            refresh_flag = self._parse_bool(args.get("refresh")) or False
            return self.mcp_executor.get_search_options(refresh=refresh_flag)
        if name == SEARCH_TOOL_NAME:
            filters = self._resolve_filters(args.get("filters"), limit=limit, only_own=only_own)
            return self.mcp_executor.search_images(filters=filters)
        raise ValueError(f"Unsupported tool call: {name}")

    @staticmethod
    def _resolve_filters(filters: Any, *, limit: int, only_own: Optional[bool]) -> Dict[str, Any]:
        resolved: Dict[str, Any] = {}
        if isinstance(filters, dict):
            resolved.update(filters)
        if "size" not in resolved and limit:
            resolved["size"] = limit
        if "page" not in resolved:
            resolved["page"] = 0
        if only_own is not None and "onlyOwn" not in resolved:
            resolved["onlyOwn"] = only_own
        return resolved

    @staticmethod
    def _parse_bool(value: Any) -> Optional[bool]:
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            lowered = value.strip().lower()
            if lowered in {"true", "1", "yes", "y"}:
                return True
            if lowered in {"false", "0", "no", "n"}:
                return False
        return None

    @staticmethod
    def _serialize_tool_call(call: Any) -> Dict[str, Any]:
        try:
            return {
                "id": getattr(call, "id", None),
                "type": getattr(call, "type", None),
                "function": {
                    "name": getattr(call.function, "name", None),
                    "arguments": getattr(call.function, "arguments", None),
                },
            }
        except Exception:
            return {}

    @staticmethod
    def _safe_parse_args(raw: str | None) -> Dict[str, Any]:
        if not raw:
            return {}
        try:
            parsed = json.loads(raw)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            pass
        return {}


class StubDeepseekOrchestrator(DeepseekSearchOrchestrator):
    """Test double that returns canned responses without network calls."""

    def __init__(self, canned_result: Dict[str, Any]):
        self.canned_result = canned_result

    def run_chat_search(
        self,
        *,
        query: str,
        limit: int = 6,
        only_own: Optional[bool] = None,
        history: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        result = json.loads(json.dumps(self.canned_result))
        result["query"] = query
        result["limit"] = limit
        result["onlyOwn"] = only_own
        return {
            "message": result.get("summary") or "done",
            "toolCalls": [
                {
                    "id": "stub-call",
                    "type": "function",
                    "function": {"name": "search_images", "arguments": json.dumps({"query": query, "limit": limit})},
                }
            ],
            "results": [result],
            "primaryResult": result,
        }