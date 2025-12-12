from __future__ import annotations

import base64
import logging
from io import BytesIO
from typing import Iterable, List, Sequence, Tuple
from urllib.parse import urlparse

import requests
from PIL import Image, UnidentifiedImageError
from werkzeug.datastructures import FileStorage

from .tagging_types import TagSuggestion

SAFE_URL_SCHEMES = {"http", "https"}


class TaggingService:
    """Baidu-backed tag suggestion engine."""

    def __init__(
        self,
        max_tags: int,
        download_timeout: float,
        download_max_bytes: int,
        *,
        baidu_classifier=None,
    ) -> None:
        self.default_limit = max(1, max_tags)
        self.download_timeout = download_timeout
        self.download_max_bytes = download_max_bytes
        self.baidu_classifier = baidu_classifier
        self._logger = logging.getLogger(__name__)

    def analyze(
        self,
        *,
        file_storage: FileStorage | None = None,
        image_url: str | None = None,
        image_base64: str | None = None,
        hints: Sequence[str] | None = None,
        limit: int | None = None,
    ) -> Tuple[List[dict], dict]:
        image_bytes = self._resolve_image_bytes(file_storage, image_url, image_base64)
        image = self._load_image(image_bytes)
        stats = self._extract_stats(image)
        raw_tags: List[TagSuggestion] = []
        raw_tags.extend(self._baidu_tags(image_bytes=image_bytes, image_url=image_url, limit=limit))
        if hints:
            raw_tags.extend(self._hint_tags(hints))

        merged = self._merge_tags(raw_tags)
        top_n = min(max(1, limit or self.default_limit), len(merged))
        tags = [item.to_dict() for item in merged[:top_n]]
        metadata = {
            "width": stats["width"],
            "height": stats["height"],
            "aspect_ratio": round(stats["aspect_ratio"], 3),
        }
        return tags, metadata

    def _baidu_tags(self, *, image_bytes: bytes, image_url: str | None, limit: int | None) -> List[TagSuggestion]:
        if not self.baidu_classifier:
            raise ValueError("Baidu tagging is not configured")
        return self.baidu_classifier.classify(
            image_bytes=image_bytes,
            image_url=image_url,
            limit=limit or self.default_limit,
        )

    def _resolve_image_bytes(
        self,
        file_storage: FileStorage | None,
        image_url: str | None,
        image_base64: str | None,
    ) -> bytes:
        if file_storage is not None:
            data = file_storage.read()
            file_storage.stream.seek(0)
            if not data:
                raise ValueError("Uploaded file is empty")
            return data
        if image_url:
            return self._download_image(image_url)
        if image_base64:
            try:
                return base64.b64decode(image_base64)
            except (ValueError, base64.binascii.Error) as exc:  # type: ignore[attr-defined]
                raise ValueError("image_base64 is not valid base64 data") from exc
        raise ValueError("Provide either a file upload, image_url, or image_base64 payload")

    def _download_image(self, url: str) -> bytes:
        parsed = urlparse(url)
        if parsed.scheme.lower() not in SAFE_URL_SCHEMES:
            raise ValueError("Only http/https URLs are supported")
        try:
            response = requests.get(url, stream=True, timeout=self.download_timeout)
            response.raise_for_status()
        except requests.RequestException as exc:
            raise ValueError(f"Failed to download image: {exc}") from exc

        chunks: List[bytes] = []
        total = 0
        for chunk in response.iter_content(chunk_size=8192):
            if not chunk:
                continue
            total += len(chunk)
            if total > self.download_max_bytes:
                raise ValueError("Remote image exceeds configured size limit")
            chunks.append(chunk)
        if not chunks:
            raise ValueError("Downloaded image is empty")
        return b"".join(chunks)

    def _load_image(self, data: bytes) -> Image.Image:
        try:
            image = Image.open(BytesIO(data))
            return image.convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            raise ValueError("Provided content is not a valid image") from exc

    def _extract_stats(self, image: Image.Image) -> dict:
        width, height = image.size
        aspect_ratio = width / max(height, 1)
        return {
            "width": width,
            "height": height,
            "aspect_ratio": aspect_ratio,
        }

    def _hint_tags(self, hints: Sequence[str]) -> List[TagSuggestion]:
        results: List[TagSuggestion] = []
        for hint in hints:
            normalized = (hint or "").strip()
            if not normalized:
                continue
            results.append(TagSuggestion(normalized.lower(), 0.65, "hint"))
        return results

    def _merge_tags(self, tags: Iterable[TagSuggestion]) -> List[TagSuggestion]:
        dedup: dict[str, TagSuggestion] = {}
        for tag in tags:
            key = tag.name.lower()
            existing = dedup.get(key)
            if existing is None or tag.confidence > existing.confidence:
                dedup[key] = tag
        return sorted(dedup.values(), key=lambda item: item.confidence, reverse=True)
