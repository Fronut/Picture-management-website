from __future__ import annotations

from flask import Flask

from .health import bp as health_bp
from .search import bp as search_bp
from .tags import bp as tags_bp


def register_blueprints(app: Flask) -> None:
    app.register_blueprint(health_bp)
    app.register_blueprint(tags_bp)
    app.register_blueprint(search_bp)
