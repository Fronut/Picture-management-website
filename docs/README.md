# Picture Management Website 文档索引

本目录收录了项目的分模块文档，面向日常开发、调试与部署场景。

- [后端 (Spring Boot)](backend.md)
- [前端 (Vue 3 + Vite)](frontend.md)
- [AI 微服务 (Flask)](ai-service.md)

## 快速上手

1. 安装依赖

   - 后端：在 `backend` 目录执行 `./mvnw -v` 确认 Maven Wrapper 可用。
   - 前端：在 `frontend` 目录执行 `npm install`。
   - AI 服务：在 `ai-service` 目录执行 `pip install -r requirements.txt` 或使用 `conda env create -f environment.yml`。

2. 本地开发（推荐分开运行）

   - 数据库与缓存：`docker-compose up -d mysql redis`
   - 后端：在 `backend` 执行 `./mvnw spring-boot:run`
   - 前端：在 `frontend` 执行 `npm run dev`
   - AI 服务：在 `ai-service` 执行 `python -m app.main`

3. 一键容器化开发

   - 根目录执行 `make dev` 或 `docker-compose up --build`

4. 环境变量关键项

   - `AI_SERVICE_URL`：后端访问 AI 微服务地址，默认 `http://ai-service:5000`
   - `SPRING_PROFILES_ACTIVE`：后端 profile（`dev`/`docker`/`prod`）
   - `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`：数据库连接
   - `REDIS_HOST`/`REDIS_PORT`：Redis 连接
   - `JWT_SECRET`：后端 JWT 密钥
   - `UPLOAD_DIR` / `THUMBNAIL_DIR`：文件与缩略图存储路径（默认 `/app/uploads`、`/app/thumbnails`）

5. 测试与质量
   - 后端：`cd backend && ./mvnw test`
   - 前端：`cd frontend && npm run lint && npm run test`（如有配置）
   - AI 服务：`cd ai-service && pytest -q`

## 目录速览

- 根 README（当前文件）概览与入口
- 分模块细节请参阅对应文档。
