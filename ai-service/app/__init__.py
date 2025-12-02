from __future__ import annotations

from dotenv import load_dotenv
from flask import Flask
from flask_cors import CORS
import werkzeug

from .config import AppConfig, build_config
from .routes import register_blueprints
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
    app.extensions["tagging_service"] = TaggingService(
        max_tags=config.default_tag_limit,
        download_timeout=config.download_timeout,
        download_max_bytes=config.download_max_bytes,
    )
    app.extensions["search_intent_service"] = SearchIntentService()
    app.extensions["app_config"] = config

    register_blueprints(app)
    return app
