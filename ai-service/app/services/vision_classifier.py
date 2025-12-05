from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import Dict, Iterable, List, Sequence

from PIL import Image

from .tagging_types import TagSuggestion

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class VisionTagDefinition:
    name: str
    prompt: str
    group: str


DEFAULT_VISION_TAGS: tuple[VisionTagDefinition, ...] = (
    VisionTagDefinition("scene:landscape", "a wide scenic natural landscape photograph", "scene"),
    VisionTagDefinition("scene:urban", "a busy modern city skyline at street level", "scene"),
    VisionTagDefinition("scene:indoor", "an indoor scene lit by artificial lighting", "scene"),
    VisionTagDefinition("scene:night", "a night scene or dark low-light environment", "scene"),
    VisionTagDefinition("subject:people", "a portrait of a person or people", "subject"),
    VisionTagDefinition("subject:group", "a group of people together", "subject"),
    VisionTagDefinition("subject:animals", "animals or wildlife", "subject"),
    VisionTagDefinition("subject:pets", "domestic pets such as cats or dogs", "subject"),
    VisionTagDefinition("subject:food", "food, meals, or cuisine", "subject"),
    VisionTagDefinition("subject:vehicle", "cars, trains, planes, or other vehicles", "subject"),
    VisionTagDefinition("subject:architecture", "architectural details or buildings", "subject"),
    VisionTagDefinition("subject:nature", "lush forests, trees, or vegetation", "subject"),
    VisionTagDefinition("subject:water", "oceans, lakes, rivers, or waterfalls", "subject"),
    VisionTagDefinition("event:sports", "sports, fitness, or fast motion scenes", "event"),
    VisionTagDefinition("event:travel", "travel or vacation imagery", "event"),
    VisionTagDefinition("mood:dramatic", "dramatic, high-contrast lighting", "mood"),
    VisionTagDefinition("mood:calm", "calm, peaceful, or serene atmosphere", "mood"),
    VisionTagDefinition("detail:macro", "macro or close-up photography", "detail"),
    VisionTagDefinition("detail:minimal", "minimalist compositions with lots of negative space", "detail"),
)


class VisionModelError(RuntimeError):
    """Raised whenever the zero-shot vision model is unavailable."""


class ZeroShotVisionClassifier:
    """Zero-shot image classifier built on top of Hugging Face CLIP models."""

    def __init__(
        self,
        model_id: str,
        *,
        hypothesis_template: str | None = None,
        tag_definitions: Sequence[VisionTagDefinition] | None = None,
        top_per_group: int = 2,
    ) -> None:
        if not tag_definitions:
            tag_definitions = DEFAULT_VISION_TAGS
        if not tag_definitions:
            raise ValueError("tag_definitions must not be empty")
        self.model_id = model_id
        self.hypothesis_template = hypothesis_template or "This photo mainly features {}."
        self._tag_definitions = list(tag_definitions)
        self._top_per_group = max(1, top_per_group)
        self._pipeline = None
        self._lock = threading.Lock()
        self._prompt_lookup: Dict[str, VisionTagDefinition] = {
            definition.prompt: definition for definition in self._tag_definitions
        }
        self._definition_by_name: Dict[str, VisionTagDefinition] = {
            definition.name: definition for definition in self._tag_definitions
        }
        self._group_order: List[str] = list(dict.fromkeys(definition.group for definition in self._tag_definitions))

    def classify(self, image: Image.Image, limit: int | None = None) -> List[TagSuggestion]:
        if image is None:
            raise ValueError("image must not be None")
        pipeline = self._ensure_pipeline()
        try:
            raw_outputs: Iterable[dict] = pipeline(
                image,
                candidate_labels=list(self._prompt_lookup.keys()),
                hypothesis_template=self.hypothesis_template,
                multi_label=True,
            )
        except Exception as exc:  # pragma: no cover - passthrough for model runtime errors
            raise VisionModelError(f"Vision model inference failed: {exc}") from exc

        suggestions: List[TagSuggestion] = []
        for output in raw_outputs:
            definition = self._prompt_lookup.get(output.get("label"))
            if not definition:
                continue
            score = float(output.get("score", 0.0))
            suggestions.append(TagSuggestion(
                name=definition.name,
                confidence=max(0.0, min(1.0, score)),
                source=f"vision:{self.model_id}",
            ))

        collapsed = self._collapse_by_group(suggestions)
        if limit is not None and limit > 0:
            return collapsed[:limit]
        return collapsed

    def _collapse_by_group(self, suggestions: Sequence[TagSuggestion]) -> List[TagSuggestion]:
        grouped: Dict[str, List[TagSuggestion]] = {}
        for suggestion in suggestions:
            definition = self._definition_by_name.get(suggestion.name)
            group = definition.group if definition else "general"
            grouped.setdefault(group, []).append(suggestion)
        ordered: List[TagSuggestion] = []
        for group, items in grouped.items():
            items.sort(key=lambda entry: entry.confidence, reverse=True)
            grouped[group] = items[: self._top_per_group]
        for group in self._group_order:
            ordered.extend(grouped.get(group, []))
        remaining_groups = [name for name in grouped.keys() if name not in self._group_order]
        for group in remaining_groups:
            ordered.extend(grouped[group])
        ordered.sort(key=lambda entry: entry.confidence, reverse=True)
        return ordered

    def _ensure_pipeline(self):
        if self._pipeline is not None:
            return self._pipeline
        with self._lock:
            if self._pipeline is not None:
                return self._pipeline
            try:
                from transformers import pipeline  # Lazy import to keep cold starts light
            except ImportError as exc:  # pragma: no cover - handled by config/tests
                raise VisionModelError("transformers is not installed") from exc
            try:
                logger.info("Loading vision model %s", self.model_id)
                self._pipeline = pipeline(
                    task="zero-shot-image-classification",
                    model=self.model_id,
                )
            except Exception as exc:  # pragma: no cover - heavy init errors surfaced at runtime
                raise VisionModelError(f"Failed to load vision model {self.model_id}: {exc}") from exc
        return self._pipeline