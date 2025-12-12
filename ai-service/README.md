# AI Service

Lightweight Flask micro-service that powers automatic tag suggestions and natural-language
search interpretation for the Picture Management platform.

## Features

- `/ai/v1/tags/suggest`: accepts an image upload, remote `image_url`, or base64 payload and
  returns up to `TAG_MAX_RESULTS` tag suggestions with confidences and metadata. The service now
  uses Baidu Advanced General (`advanced_general`) exclusively.
- `/ai/v1/search/interpret`: interprets free-form text into structured filters compatible with the
  backend `ImageSearchRequest` contract.
- `/ai/v1/health`: basic health probe used by Docker/Nginx.

### Tagging provider

- **Baidu (only)**: uses the Baidu "Advanced General" API; requires `BAIDU_API_KEY` and `BAIDU_SECRET_KEY`
  supplied via environment only (do not commit secrets).

## Configuration

Environment variables (defaults in parentheses):

| Variable                                                                                              | Description                                                        |
| ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `TAGGING_PROVIDER` (`baidu`)                                                                          | Must be `baidu` (only supported provider).                         |
| `ALLOW_BAIDU_STUB` (`true`)                                                                           | When credentials are missing, use a local stub (for CI/offline).   |
| `BAIDU_API_KEY` (unset)                                                                               | API Key from Baidu console; inject via env only (never commit).    |
| `BAIDU_SECRET_KEY` (unset)                                                                            | Secret Key from Baidu console; inject via env only (never commit). |
| `BAIDU_TOKEN_URL` (`https://aip.baidubce.com/oauth/2.0/token`)                                        | Access token endpoint.                                             |
| `BAIDU_ADVANCED_GENERAL_URL` (`https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general`) | Baidu Advanced General classify endpoint.                          |
| `BAIDU_TIMEOUT_SECONDS` (`8.0`)                                                                       | Timeout for Baidu HTTP calls (seconds).                            |
| `BAIDU_TOKEN_GRACE_SECONDS` (`300`)                                                                   | Seconds before expiry to proactively refresh token.                |
| `BAIDU_MAX_RESULTS` (`5`)                                                                             | Maximum Baidu results to keep (Baidu returns up to 5).             |
| `TAG_MAX_RESULTS` (`8`)                                                                               | Maximum tag suggestions returned per request.                      |
| `MAX_UPLOAD_MB` (`15`)                                                                                | Reject uploads larger than this size.                              |
| `IMAGE_DOWNLOAD_TIMEOUT` (`5`)                                                                        | Timeout (seconds) for `image_url` downloads.                       |
| `IMAGE_DOWNLOAD_MAX_MB` (`8`)                                                                         | Max remote image download size.                                    |
| `CORS_ALLOW_ORIGINS` (`*`)                                                                            | Allowed origins for browser calls.                                 |

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
