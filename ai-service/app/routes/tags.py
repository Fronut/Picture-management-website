from __future__ import annotations

from typing import Any, Dict, List

from flask import Blueprint, current_app, jsonify, request

from ..services.tagging import TaggingService

bp = Blueprint("tags", __name__, url_prefix="/ai/v1/tags")


@bp.route("/suggest", methods=["POST"])
def suggest_tags():
    payload = _extract_payload()
    service: TaggingService = current_app.extensions["tagging_service"]
    limit = _safe_int(payload.get("limit"))
    hints = _normalize_hints(payload.get("hints"))

    try:
        tags, metadata = service.analyze(
            file_storage=request.files.get("file"),
            image_url=payload.get("image_url"),
            image_base64=payload.get("image_base64"),
            hints=hints,
            limit=limit,
        )
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400

    response = {"status": "ok", "data": {"tags": tags, "metadata": metadata}}
    return jsonify(response), 200


def _extract_payload() -> Dict[str, Any]:
    if request.is_json:
        return request.get_json(silent=True) or {}
    data: Dict[str, Any] = {}
    data.update(request.form.to_dict(flat=True))
    return data


def _normalize_hints(raw: Any) -> List[str]:
    if raw is None:
        return []
    if isinstance(raw, list):
        return [str(item) for item in raw]
    if isinstance(raw, str):
        if raw.strip().startswith("["):
            # Attempt to parse comma-separated values from JSON-like input without using eval
            cleaned = raw.strip().strip("[]")
            if not cleaned:
                return []
            return [segment.strip().strip("\"'") for segment in cleaned.split(",") if segment.strip()]
        return [segment.strip() for segment in raw.split(",") if segment.strip()]
    return [str(raw)]


def _safe_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        parsed = int(value)
        return parsed if parsed > 0 else None
    except (TypeError, ValueError):
        return None
