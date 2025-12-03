# Picture-management-website

Zhejiang University 2025 B/S Course Project

## AI Service Configuration

- The backend expects the AI microservice to be reachable via the `AI_SERVICE_URL` environment variable (defaults to `http://ai-service:5000`, which is wired up when running with Docker Compose).
- When running the Spring Boot backend locally, export `AI_SERVICE_URL` to point to your running `ai-service` instance, e.g. `http://localhost:5000`, to enable the new AI-assisted endpoints.
