from __future__ import annotations

import base64
import logging
import threading
import time
from dataclasses import dataclass
from typing import List, Optional

import requests

from .tagging_types import TagSuggestion

logger = logging.getLogger(__name__)


class BaiduTokenError(RuntimeError):
    """Raised when access token retrieval fails."""


@dataclass(slots=True)
class _TokenCache:
    access_token: str
    expires_at: float

    @property
    def is_valid(self) -> bool:
        return time.time() < self.expires_at


class BaiduImageClassifier:
    """Thin client for Baidu advanced_general image classification API."""

    def __init__(
        self,
        *,
        api_key: Optional[str],
        secret_key: Optional[str],
        token_url: str,
        general_url: str,
        timeout_seconds: float,
        token_grace_seconds: int,
        max_results: int,
    ) -> None:
        if not api_key or not secret_key:
            raise BaiduTokenError("BAIDU_API_KEY and BAIDU_SECRET_KEY must be set")
        self.api_key = api_key
        self.secret_key = secret_key
        self.token_url = token_url
        self.general_url = general_url
        self.timeout_seconds = timeout_seconds
        self.token_grace_seconds = max(0, token_grace_seconds)
        self.max_results = max(1, max_results)
        self._token_cache: Optional[_TokenCache] = None
        self._lock = threading.Lock()

    def classify(self, *, image_bytes: bytes | None, image_url: str | None = None, limit: int | None = None) -> List[TagSuggestion]:
        if not image_bytes and not image_url:
            raise ValueError("image_bytes or image_url is required for Baidu classification")
        token = self._get_access_token()
        request_url = f"{self.general_url}?access_token={token}"

        payload = {}
        if image_bytes:
            payload["image"] = base64.b64encode(image_bytes).decode()
        elif image_url:
            payload["url"] = image_url

        headers = {"content-type": "application/x-www-form-urlencoded"}
        try:
            response = requests.post(request_url, data=payload, headers=headers, timeout=self.timeout_seconds)
        except requests.RequestException as exc:
            raise ValueError(f"Baidu classify request failed: {exc}") from exc

        if not response.ok:
            raise ValueError(f"Baidu classify request returned {response.status_code}: {response.text}")

        data = response.json()
        results = data.get("result") or []
        if not isinstance(results, list):
            raise ValueError("Unexpected Baidu response format: missing result list")

        max_count = min(self.max_results, limit) if limit else self.max_results
        suggestions: List[TagSuggestion] = []
        for item in results[:max_count]:
            keyword = item.get("keyword")
            score = item.get("score")
            if not keyword:
                continue
            try:
                confidence = float(score)
            except (TypeError, ValueError):
                confidence = 0.0
            suggestions.append(
                TagSuggestion(
                    name=str(keyword),
                    confidence=max(0.0, min(1.0, confidence)),
                    source="baidu",
                )
            )
        return suggestions

    def _get_access_token(self) -> str:
        cached = self._token_cache
        if cached and cached.is_valid:
            return cached.access_token

        with self._lock:
            cached = self._token_cache
            if cached and cached.is_valid:
                return cached.access_token

            params = {
                "grant_type": "client_credentials",
                "client_id": self.api_key,
                "client_secret": self.secret_key,
            }
            try:
                response = requests.get(self.token_url, params=params, timeout=self.timeout_seconds)
            except requests.RequestException as exc:
                raise BaiduTokenError(f"Failed to obtain Baidu access token: {exc}") from exc

            if not response.ok:
                raise BaiduTokenError(f"Failed to obtain Baidu access token: {response.status_code} {response.text}")

            data = response.json()
            access_token = data.get("access_token")
            expires_in = data.get("expires_in")
            if not access_token or not expires_in:
                raise BaiduTokenError("Baidu token response missing access_token or expires_in")

            try:
                ttl = int(expires_in)
            except (TypeError, ValueError):
                ttl = 0
            expires_at = time.time() + max(0, ttl - self.token_grace_seconds)
            self._token_cache = _TokenCache(access_token=access_token, expires_at=expires_at)
            logger.info("Baidu access token cached for ~%s seconds", ttl)
            return access_token