from __future__ import annotations

from dotenv import load_dotenv
from flask import Flask
from flask_cors import CORS
import werkzeug

from .config import AppConfig, build_config
from .routes import register_blueprints
from .services.baidu_client import BaiduImageClassifier, BaiduTokenError
from .services.search_intent import SearchIntentService
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
    baidu_classifier = None
    if tagging_provider != "baidu":
        raise RuntimeError("Only 'baidu' tagging provider is supported now")

    # Allow tests to inject a stub classifier via overrides
    injected_classifier = overrides.get("baidu_classifier") if overrides else None
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
            raise RuntimeError(f"Baidu tagging not configured: {exc}") from exc

    app.extensions["tagging_service"] = TaggingService(
        max_tags=config.default_tag_limit,
        download_timeout=config.download_timeout,
        download_max_bytes=config.download_max_bytes,
        baidu_classifier=baidu_classifier,
    )
    app.extensions["search_intent_service"] = SearchIntentService()
    app.extensions["app_config"] = config

    register_blueprints(app)
    return app
