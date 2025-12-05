# AI Service

Lightweight Flask micro-service that powers automatic tag suggestions and natural-language
search interpretation for the Picture Management platform.

## Features

- `/ai/v1/tags/suggest`: accepts an image upload, remote `image_url`, or base64 payload and
  returns up to `TAG_MAX_RESULTS` tag suggestions with confidences and analysis metadata powered by a
  zero-shot CLIP vision model plus lightweight heuristics for orientation/lighting detection.
- `/ai/v1/search/interpret`: interprets free-form text into structured filters compatible with the
  backend `ImageSearchRequest` contract.
- `/ai/v1/health`: basic health probe used by Docker/Nginx.

### Vision model quick facts

- The service uses Hugging Face's CLIP zero-shot classification pipeline (default: `openai/clip-vit-base-patch32`).
- Models are downloaded and cached on first use under `~/.cache/huggingface`. The `MODEL_PATH` directory can
  be mounted inside Docker to persist weights between restarts.
- Set `ENABLE_VISION_MODEL=false` when running unit tests or on constrained devices to fall back to the
  rule-based heuristics only.

## Configuration

Environment variables (defaults in parentheses):

| Variable                                                        | Description                                                           |
| --------------------------------------------------------------- | --------------------------------------------------------------------- |
| `MODEL_PATH` (`/app/models`)                                    | Directory to persist cache/model weights.                             |
| `ENABLE_VISION_MODEL` (`true`)                                  | Toggle the CLIP-based vision classifier (set `false` to disable).     |
| `VISION_MODEL_ID` (`openai/clip-vit-base-patch32`)              | Hugging Face model identifier passed to `transformers.pipeline`.      |
| `VISION_HYPOTHESIS_TEMPLATE` (`This photo mainly features {}.`) | Template for zero-shot prompts; `{}` is replaced per candidate tag.   |
| `VISION_GROUP_TOP_N` (`2`)                                      | Maximum number of tags kept per semantic group from the model output. |
| `TAG_MAX_RESULTS` (`8`)                                         | Maximum tag suggestions returned per request.                         |
| `MAX_UPLOAD_MB` (`15`)                                          | Reject uploads larger than this size.                                 |
| `IMAGE_DOWNLOAD_TIMEOUT` (`5`)                                  | Timeout (seconds) for `image_url` downloads.                          |
| `IMAGE_DOWNLOAD_MAX_MB` (`8`)                                   | Max remote image download size.                                       |
| `CORS_ALLOW_ORIGINS` (`*`)                                      | Allowed origins for browser calls.                                    |

## Local Development

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
FLASK_ENV=development flask --app app.main run --port 5000
```

## Testing

```bash
cd ai-service
pytest -q
```

## Request Examples

### Tag suggestions

```bash
curl -X POST http://localhost:5000/ai/v1/tags/suggest \
     -F "file=@/path/to/photo.jpg" \
     -F "limit=6"
```

### Search interpretation

```bash
curl -X POST http://localhost:5000/ai/v1/search/interpret \
     -H "Content-Type: application/json" \
     -d '{"query": "sunset beach 4k portrait"}'
```
