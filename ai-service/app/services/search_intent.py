from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Sequence


@dataclass(slots=True)
class ConceptRule:
    name: str
    keywords: Sequence[str]
    tags: Sequence[str]
    default_keyword: str | None = None
    filter_updates: Dict[str, Any] = field(default_factory=dict)

    def matches(self, query_lower: str, query_raw: str) -> bool:
        for keyword in self.keywords:
            if keyword in query_lower or keyword in query_raw:
                return True
        return False


class SearchIntentService:
    """Interprets free-form text into structured image search filters."""

    def __init__(self) -> None:
        self._concepts: List[ConceptRule] = [
            ConceptRule(
                name="sunset",
                keywords=["sunset", "dusk", "golden hour", "黄昏", "落日"],
                tags=("sunset", "daypart:evening"),
                default_keyword="sunset",
                filter_updates={"keyword": "sunset"},
            ),
            ConceptRule(
                name="beach",
                keywords=["beach", "coast", "海滩", "沙滩"],
                tags=("beach", "subject:water"),
                default_keyword="beach",
            ),
            ConceptRule(
                name="mountain",
                keywords=["mountain", "山", "雪山", "alps"],
                tags=("mountain", "subject:nature"),
                default_keyword="mountain",
            ),
            ConceptRule(
                name="city",
                keywords=["city", "urban", "street", "街景", "建筑"],
                tags=("city", "subject:architecture"),
                default_keyword="city",
            ),
            ConceptRule(
                name="portrait",
                keywords=["portrait", "人物", "人像", "自拍"],
                tags=("portrait", "subject:people"),
                default_keyword="portrait",
                filter_updates={"orientation": "portrait"},
            ),
        ]
        self._orientation_terms = {
            "orientation:portrait": ["portrait", "vertical", "竖"],
            "orientation:landscape": ["landscape", "horizontal", "横"],
        }
        self._dayparts = {
            "daypart:morning": ["dawn", "morning", "清晨"],
            "daypart:night": ["night", "夜景", "星空"],
            "daypart:evening": ["sunset", "evening", "傍晚", "黄昏"],
        }
        self._colors = {
            "color:blue": ["blue", "海蓝", "天空"],
            "color:green": ["green", "森林", "绿色"],
            "color:red": ["red", "暖色", "红色"],
            "color:monochrome": ["黑白", "monochrome", "b&w"],
        }

    def interpret(self, query: str, *, limit_tags: int | None = None) -> Dict[str, Any]:
        query_raw = query.strip()
        if not query_raw:
            raise ValueError("query is required")
        query_lower = query_raw.lower()
        tags: List[str] = []
        keywords: List[str] = []
        explanations: List[Dict[str, Any]] = []
        filters: Dict[str, Any] = {
            "keyword": None,
            "tags": [],
            "minWidth": None,
            "minHeight": None,
            "maxWidth": None,
            "maxHeight": None,
            "privacyLevel": None,
            "cameraMake": None,
            "cameraModel": None,
            "onlyOwn": False,
        }

        # Concept rules
        for rule in self._concepts:
            if rule.matches(query_lower, query_raw):
                tags.extend(rule.tags)
                if rule.default_keyword:
                    keywords.append(rule.default_keyword)
                for key, value in rule.filter_updates.items():
                    filters[key] = value
                explanations.append({"rule": rule.name, "reason": "keyword matched"})

        # Orientation detection adds tags to keep consistency with backend tag style
        for tag_name, terms in self._orientation_terms.items():
            if self._contains_any(query_lower, query_raw, terms):
                tags.append(tag_name)
                explanations.append({"rule": tag_name, "reason": "orientation hint"})

        # Daypart and colors
        for tag_name, terms in self._dayparts.items():
            if self._contains_any(query_lower, query_raw, terms):
                tags.append(tag_name)
        for tag_name, terms in self._colors.items():
            if self._contains_any(query_lower, query_raw, terms):
                tags.append(tag_name)

        # Quality hints for resolution
        if "4k" in query_lower or "超清" in query_raw:
            filters["minWidth"] = max(filters.get("minWidth") or 0, 3840)
            filters["minHeight"] = max(filters.get("minHeight") or 0, 2160)
            explanations.append({"rule": "resolution", "reason": "found 4k keyword"})
        elif "1080" in query_lower or "高清" in query_raw:
            filters["minWidth"] = max(filters.get("minWidth") or 0, 1920)
            filters["minHeight"] = max(filters.get("minHeight") or 0, 1080)

        dedup_tags = list(dict.fromkeys(tags))
        if limit_tags:
            dedup_tags = dedup_tags[:limit_tags]
        filters["tags"] = dedup_tags
        filters["keyword"] = " ".join(dict.fromkeys(keywords)) or None

        confidence = round(min(0.95, 0.35 + 0.1 * len(explanations)), 3)
        result = {
            "query": query_raw,
            "keywords": list(dict.fromkeys(keywords)),
            "tags": dedup_tags,
            "filters": filters,
            "explanations": explanations,
            "confidence": confidence,
        }
        return result

    def _contains_any(self, query_lower: str, query_raw: str, terms: Iterable[str]) -> bool:
        for term in terms:
            if term in query_lower or term in query_raw:
                return True
        return False
