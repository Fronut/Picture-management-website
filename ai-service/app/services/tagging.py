from __future__ import annotations

import base64
import logging
from io import BytesIO
from typing import Iterable, List, Sequence, Tuple
from urllib.parse import urlparse

import numpy as np
import requests
from PIL import Image, UnidentifiedImageError
from werkzeug.datastructures import FileStorage

from .tagging_types import TagSuggestion
from .vision_classifier import VisionModelError, ZeroShotVisionClassifier

SAFE_URL_SCHEMES = {"http", "https"}


class TaggingService:
    """Lightweight rule-based tag suggestion engine."""

    def __init__(
        self,
        max_tags: int,
        download_timeout: float,
        download_max_bytes: int,
        *,
        vision_classifier: ZeroShotVisionClassifier | None = None,
    ) -> None:
        self.default_limit = max(1, max_tags)
        self.download_timeout = download_timeout
        self.download_max_bytes = download_max_bytes
        self.vision_classifier = vision_classifier
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
        raw_tags.extend(self._vision_tags(image, limit))
        raw_tags.extend(self._orientation_tags(stats))
        raw_tags.extend(self._lighting_tags(stats))
        raw_tags.extend(self._color_tags(stats))
        raw_tags.extend(self._subject_tags(stats))
        raw_tags.extend(self._detail_tags(stats))
        if hints:
            raw_tags.extend(self._hint_tags(hints))

        merged = self._merge_tags(raw_tags)
        top_n = min(max(1, limit or self.default_limit), len(merged))
        tags = [item.to_dict() for item in merged[:top_n]]
        metadata = {
            "width": stats["width"],
            "height": stats["height"],
            "aspect_ratio": round(stats["aspect_ratio"], 3),
            "brightness": round(stats["brightness"], 3),
            "blue_ratio": round(stats["blue_ratio"], 3),
            "green_ratio": round(stats["green_ratio"], 3),
            "red_ratio": round(stats["red_ratio"], 3),
            "skin_ratio": round(stats["skin_ratio"], 3),
            "edge_density": round(stats["edge_density"], 3),
        }
        return tags, metadata

    def _vision_tags(self, image: Image.Image, limit: int | None) -> List[TagSuggestion]:
        if not self.vision_classifier:
            return []
        expanded_limit = max(self.default_limit, (limit or self.default_limit) + 3)
        try:
            return self.vision_classifier.classify(image, limit=expanded_limit)
        except VisionModelError as exc:  # pragma: no cover - logged for observability only
            self._logger.warning("Vision classifier unavailable: %s", exc)
            return []

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
        resized = image.resize((128, 128))
        arr = np.asarray(resized, dtype=np.float32) / 255.0
        grayscale = np.dot(arr[..., :3], [0.299, 0.587, 0.114])
        brightness = float(grayscale.mean())
        red_ratio = float((arr[..., 0] > 0.55).mean())
        green_ratio = float((arr[..., 1] > 0.55).mean())
        blue_ratio = float((arr[..., 2] > 0.55).mean())

        # Rudimentary skin detector
        skin_mask = (
            (arr[..., 0] > 0.35)
            & (arr[..., 1] > 0.2)
            & (arr[..., 2] > 0.15)
            & (arr[..., 0] > arr[..., 1])
            & (arr[..., 0] - arr[..., 2] > 0.1)
        )
        skin_ratio = float(skin_mask.mean())

        # Edge density proxy
        sobel_x = np.abs(np.diff(arr, axis=1)).mean()
        sobel_y = np.abs(np.diff(arr, axis=0)).mean()
        edge_density = float(min(1.0, (sobel_x + sobel_y)))

        return {
            "width": width,
            "height": height,
            "aspect_ratio": width / max(height, 1),
            "brightness": brightness,
            "red_ratio": red_ratio,
            "green_ratio": green_ratio,
            "blue_ratio": blue_ratio,
            "skin_ratio": skin_ratio,
            "edge_density": edge_density,
        }

    def _orientation_tags(self, stats: dict) -> List[TagSuggestion]:
        width = stats["width"]
        height = stats["height"]
        aspect = stats["aspect_ratio"]
        if aspect > 1.2:
            return [TagSuggestion("orientation:landscape", 0.92, "geometry")]
        if aspect < 0.85:
            return [TagSuggestion("orientation:portrait", 0.9, "geometry")]
        return [TagSuggestion("orientation:square", 0.75, "geometry")]

    def _lighting_tags(self, stats: dict) -> List[TagSuggestion]:
        brightness = stats["brightness"]
        tags: List[TagSuggestion] = []
        if brightness >= 0.7:
            tags.append(TagSuggestion("lighting:bright", 0.82, "luminance"))
        elif brightness <= 0.25:
            tags.append(TagSuggestion("lighting:low", 0.78, "luminance"))
        else:
            tags.append(TagSuggestion("lighting:balanced", 0.7, "luminance"))
        return tags

    def _color_tags(self, stats: dict) -> List[TagSuggestion]:
        tags: List[TagSuggestion] = []
        red_ratio = stats["red_ratio"]
        green_ratio = stats["green_ratio"]
        blue_ratio = stats["blue_ratio"]
        warmth = red_ratio - blue_ratio
        if red_ratio > 0.35:
            tags.append(TagSuggestion("color:warm", min(1.0, 0.6 + red_ratio / 2), "color-balance"))
        if blue_ratio > 0.3:
            tags.append(TagSuggestion("color:cool", min(1.0, 0.55 + blue_ratio / 2), "color-balance"))
            tags.append(TagSuggestion("subject:water", min(0.95, 0.5 + blue_ratio / 2), "color-balance"))
        if green_ratio > 0.3:
            tags.append(TagSuggestion("subject:nature", min(0.95, 0.55 + green_ratio / 2), "color-balance"))
        if warmth > 0.15:
            tags.append(TagSuggestion("mood:vibrant", 0.68 + min(0.2, warmth), "color-balance"))
        elif warmth < -0.15:
            tags.append(TagSuggestion("mood:calm", 0.68 + min(0.2, abs(warmth)), "color-balance"))
        return tags

    def _subject_tags(self, stats: dict) -> List[TagSuggestion]:
        tags: List[TagSuggestion] = []
        skin_ratio = stats["skin_ratio"]
        blue_ratio = stats["blue_ratio"]
        green_ratio = stats["green_ratio"]
        brightness = stats["brightness"]
        if skin_ratio > 0.08:
            tags.append(TagSuggestion("subject:people", min(0.95, 0.7 + skin_ratio), "skin-detection"))
        if blue_ratio > 0.45 and brightness < 0.4:
            tags.append(TagSuggestion("scene:night-sky", 0.72, "color-context"))
        if green_ratio > 0.45 and brightness > 0.35:
            tags.append(TagSuggestion("scene:forest", 0.7, "color-context"))
        if blue_ratio > 0.35 and brightness > 0.4:
            tags.append(TagSuggestion("scene:coast", 0.68, "color-context"))
        return tags

    def _detail_tags(self, stats: dict) -> List[TagSuggestion]:
        tags: List[TagSuggestion] = []
        edge_density = stats["edge_density"]
        if edge_density > 0.35:
            tags.append(TagSuggestion("detail:rich", min(0.95, 0.6 + edge_density), "texture"))
        else:
            tags.append(TagSuggestion("detail:smooth", 0.65, "texture"))
        return tags

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
