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


def _bool_from_env(key: str, default: bool) -> bool:
    raw = os.getenv(key)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(slots=True)
class AppConfig:
    """Typed configuration model for the AI service."""

    app_name: str = os.getenv("APP_NAME", "picture-ai-service")
    app_version: str = os.getenv("APP_VERSION", "0.1.0")
    cors_allow_origins: str = os.getenv("CORS_ALLOW_ORIGINS", "*")
    max_upload_mb: int = _int_from_env("MAX_UPLOAD_MB", 15)
    download_timeout: float = _float_from_env("IMAGE_DOWNLOAD_TIMEOUT", 5.0)
    download_max_mb: int = _int_from_env("IMAGE_DOWNLOAD_MAX_MB", 8)
    default_tag_limit: int = _int_from_env("TAG_MAX_RESULTS", 8)
    enable_profiler: bool = os.getenv("ENABLE_PROFILER", "false").lower() in {"1", "true", "yes"}
    tagging_provider: str = os.getenv("TAGGING_PROVIDER", "baidu")

    # Baidu image classify API
    baidu_api_key: str | None = os.getenv("BAIDU_API_KEY")
    baidu_secret_key: str | None = os.getenv("BAIDU_SECRET_KEY")
    baidu_token_url: str = os.getenv(
        "BAIDU_TOKEN_URL",
        "https://aip.baidubce.com/oauth/2.0/token",
    )
    baidu_general_url: str = os.getenv(
        "BAIDU_ADVANCED_GENERAL_URL",
        "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general",
    )
    baidu_timeout_seconds: float = _float_from_env("BAIDU_TIMEOUT_SECONDS", 8.0)
    baidu_token_grace_seconds: int = _int_from_env("BAIDU_TOKEN_GRACE_SECONDS", 300)
    baidu_max_results: int = _int_from_env("BAIDU_MAX_RESULTS", 5)
    allow_baidu_stub: bool = _bool_from_env("ALLOW_BAIDU_STUB", True)

    # Deepseek / MCP bridging
    deepseek_api_key: str | None = os.getenv("DEEPSEEK_API_KEY")
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    deepseek_timeout_seconds: float = _float_from_env("DEEPSEEK_TIMEOUT_SECONDS", 15.0)
    backend_api_base_url: str = os.getenv("BACKEND_API_BASE_URL", "http://localhost:8080")
    backend_api_token: str | None = os.getenv("BACKEND_API_TOKEN")

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
