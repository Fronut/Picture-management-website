from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from openai import OpenAI

from .mcp_search import McpSearchExecutor


DEEPSEEK_SEARCH_TOOL = [
    {
        "type": "function",
        "function": {
            "name": "search_images",
            "description": (
                "Search the Picture Management library using natural language. "
                "Call this tool whenever the user asks to find photos or images."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Natural-language description of the desired photo",
                    },
                    "limit": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 20,
                        "default": 6,
                        "description": "Maximum number of images to return",
                    },
                    "onlyOwn": {
                        "type": "boolean",
                        "description": "Restrict results to the authenticated user's uploads",
                    },
                },
                "required": ["query"],
                "additionalProperties": False,
            },
        },
    }
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
            "Use the search_images tool to fetch results instead of guessing. "
            "Summarize findings in Chinese concisely."
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

        first = self.client.chat(messages=messages, tools=DEEPSEEK_SEARCH_TOOL, tool_choice="auto")
        choice = first.choices[0].message
        tool_calls = choice.tool_calls or []
        tool_call_records = [self._serialize_tool_call(call) for call in tool_calls]
        tool_results: List[Dict[str, Any]] = []
        if not tool_calls:
            # Model didn't call the tool; fall back to executing once with defaults
            call_payload = {"query": query, "limit": limit, "onlyOwn": only_own}
            tool_results.append(self._execute_tool(call_payload))
            messages.append({"role": "assistant", "content": choice.content or ""})
        else:
            messages.append({"role": "assistant", "content": choice.content or "", "tool_calls": tool_calls})
            for call in tool_calls:
                args = self._safe_parse_args(call.function.arguments)
                if "query" not in args or not args["query"]:
                    args["query"] = query
                if "limit" not in args:
                    args["limit"] = limit
                if only_own is not None and "onlyOwn" not in args:
                    args["onlyOwn"] = only_own
                result = self._execute_tool(args)
                tool_results.append(result)
                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": call.id,
                        "name": call.function.name,
                        "content": json.dumps(result, ensure_ascii=False),
                    }
                )

        follow_up = self.client.chat(messages=messages, tools=DEEPSEEK_SEARCH_TOOL, tool_choice="none")
        final_message = follow_up.choices[0].message
        primary_result = tool_results[-1] if tool_results else None
        return {
            "message": final_message.content or "",
            "toolCalls": tool_call_records,
            "results": tool_results,
            "primaryResult": primary_result,
        }

    def _execute_tool(self, args: Dict[str, Any]) -> Dict[str, Any]:
        return self.mcp_executor.search_images(
            query=args.get("query") or "",
            limit=args.get("limit") or 6,
            only_own=args.get("onlyOwn"),
        )

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
*** End Patch