from __future__ import annotations

from flask import Blueprint, current_app, jsonify, request

from ..services.search_intent import SearchIntentService

bp = Blueprint("search", __name__, url_prefix="/ai/v1/search")


@bp.route("/interpret", methods=["POST"])
def interpret_query():
    if not request.is_json:
        return jsonify({"status": "error", "message": "JSON body required"}), 400

    payload = request.get_json(silent=True) or {}
    query = (payload.get("query") or "").strip()
    limit_tags = payload.get("limit")

    service: SearchIntentService = current_app.extensions["search_intent_service"]
    try:
        result = service.interpret(query=query, limit_tags=_safe_int(limit_tags))
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400

    return jsonify({"status": "ok", "data": result}), 200


def _safe_int(value):
    if value is None:
        return None
    try:
        parsed = int(value)
        return parsed if parsed > 0 else None
    except (TypeError, ValueError):
        return None
