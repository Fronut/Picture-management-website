from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class TagSuggestion:
    name: str
    confidence: float
    source: str

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "confidence": round(float(self.confidence), 4),
            "source": self.source,
        }
