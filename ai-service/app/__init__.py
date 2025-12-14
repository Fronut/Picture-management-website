from __future__ import annotations

import os

from dotenv import load_dotenv
from flask import Flask
from flask_cors import CORS
import werkzeug

from .config import AppConfig, build_config
from .routes import register_blueprints
from .services.baidu_client import BaiduImageClassifier, BaiduTokenError, StubBaiduClassifier
from .services.deepseek_chat import DeepseekClient, DeepseekConfig, DeepseekSearchOrchestrator
from .services.mcp_search import McpSearchConfig, McpSearchExecutor
from .services.tagging import TaggingService


def create_app(overrides: dict | None = None) -> Flask:
    """Application factory used by both tests and Gunicorn."""

    load_dotenv()
    # Werkzeug 3.1 removed the __version__ attribute which Flask's test client still references.
    if not hasattr(werkzeug, "__version__"):
        werkzeug.__version__ = "3"
    config: AppConfig = build_config(overrides)

    app = Flask(__name__)
    app.config.from_mapping(config.as_flask_config())
    if overrides:
        app.config.update(overrides)

    CORS(app, resources={r"/ai/*": {"origins": config.cors_allow_origins}})

    # Register core services for later reuse via current_app.extensions
    tagging_provider = (config.tagging_provider or "baidu").lower()
    if tagging_provider != "baidu":
        raise RuntimeError("Only 'baidu' tagging provider is supported now")

    # Allow tests or offline CI to inject a stub classifier
    injected_classifier = overrides.get("baidu_classifier") if overrides else None
    baidu_classifier = None
    if injected_classifier:
        baidu_classifier = injected_classifier
    else:
        try:
            baidu_classifier = BaiduImageClassifier(
                api_key=config.baidu_api_key,
                secret_key=config.baidu_secret_key,
                token_url=config.baidu_token_url,
                general_url=config.baidu_general_url,
                timeout_seconds=config.baidu_timeout_seconds,
                token_grace_seconds=config.baidu_token_grace_seconds,
                max_results=config.baidu_max_results,
            )
        except BaiduTokenError as exc:
            if config.allow_baidu_stub:
                app.logger.warning("Baidu credentials missing; using stub classifier because ALLOW_BAIDU_STUB=true: %s", exc)
                baidu_classifier = StubBaiduClassifier()
            else:
                raise RuntimeError(
                    "Baidu tagging is not configured; set BAIDU_API_KEY/BAIDU_SECRET_KEY or enable ALLOW_BAIDU_STUB=true for test stubs"
                ) from exc

    app.extensions["tagging_service"] = TaggingService(
        max_tags=config.default_tag_limit,
        download_timeout=config.download_timeout,
        download_max_bytes=config.download_max_bytes,
        baidu_classifier=baidu_classifier,
    )

    # Deepseek + MCP orchestrator (optional)
    deepseek_service = overrides.get("deepseek_chat_service") if overrides else None
    if not deepseek_service and config.deepseek_api_key:
        try:
            deepseek_client = DeepseekClient(
                DeepseekConfig(
                    api_key=config.deepseek_api_key,
                    base_url=config.deepseek_base_url,
                    model=config.deepseek_model,
                    timeout_seconds=config.deepseek_timeout_seconds,
                )
            )
            mcp_executor = McpSearchExecutor(
                McpSearchConfig(
                    backend_api_base_url=config.backend_api_base_url,
                    backend_api_token=config.backend_api_token,
                    timeout_seconds=config.deepseek_timeout_seconds,
                )
            )
            deepseek_service = DeepseekSearchOrchestrator(deepseek_client, mcp_executor)
        except Exception as exc:  # pragma: no cover - defensive logging
            app.logger.error("Failed to initialise Deepseek orchestrator: %s", exc)
            deepseek_service = None
    app.extensions["deepseek_chat_service"] = deepseek_service
    app.extensions["app_config"] = config

    register_blueprints(app)
    return app
