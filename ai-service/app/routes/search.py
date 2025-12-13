from __future__ import annotations

from flask import Blueprint, current_app, jsonify, request

from ..services.deepseek_chat import DeepseekSearchOrchestrator

bp = Blueprint("search", __name__, url_prefix="/ai/v1/search")


@bp.route("/chat", methods=["POST"])
def chat_search():
    if not request.is_json:
        return jsonify({"status": "error", "message": "JSON body required"}), 400

    payload = request.get_json(silent=True) or {}
    query = (payload.get("query") or "").strip()
    limit = _safe_int(payload.get("limit")) or 6
    only_own = payload.get("onlyOwn")
    messages = payload.get("messages") if isinstance(payload.get("messages"), list) else None

    orchestrator: DeepseekSearchOrchestrator | None = current_app.extensions.get("deepseek_chat_service")
    if orchestrator is None:
        return jsonify({"status": "error", "message": "Deepseek integration is not configured"}), 503

    try:
        result = orchestrator.run_chat_search(query=query, limit=limit, only_own=only_own, history=messages)
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400
    except Exception as exc:  # pragma: no cover - runtime safety
        current_app.logger.exception("Deepseek chat search failed: %s", exc)
        return jsonify({"status": "error", "message": "Deepseek chat search failed"}), 500

    return jsonify({"status": "ok", "data": result}), 200


def _safe_int(value):
    if value is None:
        return None
    try:
        parsed = int(value)
        return parsed if parsed > 0 else None
    except (TypeError, ValueError):
        return None
