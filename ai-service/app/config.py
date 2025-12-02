from __future__ import annotations

from dataclasses import asdict, dataclass
import os
from typing import Any, Dict


def _int_from_env(key: str, default: int) -> int:
    raw = os.getenv(key)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _float_from_env(key: str, default: float) -> float:
    raw = os.getenv(key)
    if raw is None:
        return default
    try:
        return float(raw)
    except ValueError:
        return default


@dataclass(slots=True)
class AppConfig:
    """Typed configuration model for the AI service."""

    app_name: str = os.getenv("APP_NAME", "picture-ai-service")
    app_version: str = os.getenv("APP_VERSION", "0.1.0")
    model_path: str = os.getenv("MODEL_PATH", "/app/models")
    cors_allow_origins: str = os.getenv("CORS_ALLOW_ORIGINS", "*")
    max_upload_mb: int = _int_from_env("MAX_UPLOAD_MB", 15)
    download_timeout: float = _float_from_env("IMAGE_DOWNLOAD_TIMEOUT", 5.0)
    download_max_mb: int = _int_from_env("IMAGE_DOWNLOAD_MAX_MB", 8)
    default_tag_limit: int = _int_from_env("TAG_MAX_RESULTS", 8)
    enable_profiler: bool = os.getenv("ENABLE_PROFILER", "false").lower() in {"1", "true", "yes"}

    def as_flask_config(self) -> Dict[str, Any]:
        data: Dict[str, Any] = asdict(self)
        data["MAX_CONTENT_LENGTH"] = self.max_upload_bytes
        data["DOWNLOAD_MAX_BYTES"] = self.download_max_bytes
        return data

    @property
    def max_upload_bytes(self) -> int:
        return self.max_upload_mb * 1024 * 1024

    @property
    def download_max_bytes(self) -> int:
        return self.download_max_mb * 1024 * 1024


def build_config(overrides: Dict[str, Any] | None = None) -> AppConfig:
    config = AppConfig()
    if overrides:
        for key, value in overrides.items():
            if hasattr(config, key):
                setattr(config, key, value)
    return config
