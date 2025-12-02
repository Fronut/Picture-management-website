from __future__ import annotations

import platform
from datetime import datetime, timezone
from flask import Blueprint, current_app, jsonify

bp = Blueprint("health", __name__, url_prefix="/ai/v1")


@bp.route("/health", methods=["GET"])
def healthcheck():
    config = current_app.extensions.get("app_config")
    payload = {
        "service": config.app_name if config else "ai-service",
        "version": config.app_version if config else "unknown",
        "status": "healthy",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "python": platform.python_version(),
    }
    return jsonify({"status": "ok", "data": payload}), 200
