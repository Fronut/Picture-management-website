# AI Service

Lightweight Flask micro-service that powers automatic tag suggestions and natural-language
search interpretation for the Picture Management platform.

## Features

- `/ai/v1/tags/suggest`: accepts an image upload, remote `image_url`, or base64 payload and
  returns up to `TAG_MAX_RESULTS` tag suggestions with confidences and analysis metadata.
- `/ai/v1/search/interpret`: interprets free-form text into structured filters compatible with the
  backend `ImageSearchRequest` contract.
- `/ai/v1/health`: basic health probe used by Docker/Nginx.

## Configuration

Environment variables (defaults in parentheses):

| Variable                       | Description                                                                          |
| ------------------------------ | ------------------------------------------------------------------------------------ |
| `MODEL_PATH` (`/app/models`)   | Directory for heavy-weight models (unused for rule engine but kept for future work). |
| `TAG_MAX_RESULTS` (`8`)        | Maximum tag suggestions returned per request.                                        |
| `MAX_UPLOAD_MB` (`15`)         | Reject uploads larger than this size.                                                |
| `IMAGE_DOWNLOAD_TIMEOUT` (`5`) | Timeout (seconds) for `image_url` downloads.                                         |
| `IMAGE_DOWNLOAD_MAX_MB` (`8`)  | Max remote image download size.                                                      |
| `CORS_ALLOW_ORIGINS` (`*`)     | Allowed origins for browser calls.                                                   |

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
