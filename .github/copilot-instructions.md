# Picture-management-website – AI Agent 指南（中文）

## 概要说明

- 本仓库为课程项目“图片管理网站”的单体源码仓库，包含三套可部署服务：后端（Spring Boot）、前端（Vite + Vue 3）和 AI 微服务（Flask）。
- 容器编排由 `docker-compose.yml` 管理；MySQL/Redis 持久化、Nginx 反代与静态文件、以及容器间网络均在 compose 中配置。

## 日常开发流程

- 快速准备：

```
docker-compose up -d mysql redis
```

- 后端本地开发：

```
cd backend
./mvnw spring-boot:run
```

- 前端本地开发：

```
cd frontend
npm run dev
```

- 一键容器化开发：`make dev` 或 `docker-compose up --build`

## 代码约定与重要配置

- 后端使用 Maven Wrapper（`./mvnw` / `mvnw.cmd`），目标 Java 版本 17；数据库迁移使用 Flyway，脚本放在 `backend/src/main/resources/db/migration/`。
- 前端采用 Vue 3 + TypeScript、Element Plus、Pinia；Vite 别名 `@ -> src` 已配置。
- AI 服务为独立 Flask 应用（通过 Gunicorn 暴露），模型权重与较大依赖应放到容器卷 `/app/models`。

## 运行与测试（建议）

- 后端测试（Maven）：

```
cd backend
./mvnw test
```

- AI 服务测试（pytest）：

```
cd ai-service
pytest -q
```

## 核心设计要点（摘录）

- 核心模块：用户认证、图片上传与存储、EXIF 提取、标签管理、缩略图生成、图片检索、图片编辑、AI 分析服务。
- 文件存储约定：容器内统一使用 `/app/uploads` 存放原图，`/app/thumbnails` 存放缩略图；不要在代码中写死操作系统路径，使用 `FileStorageConfig`/`application.yml` 可配置项。
- 安全：JWT 鉴权、密码使用 bcrypt 存储、敏感配置放 `.env`、接口校验与文件类型/大小白名单（默认 20MB）。

## 重要 API（参考）

- `POST /api/auth/register` — 用户注册（用户名/邮箱/密码）
- `POST /api/auth/login` — 用户登录（返回 JWT）
- `POST /api/images/upload` — 图片上传（`multipart/form-data`，支持批量）
- `DELETE /api/images/{imageId}` — 删除图片（支持软删/彻底删）
- `GET /api/images/search` — 多条件图片检索（支持关键词、时间、相机、标签等）
- `POST /api/images/ai-search` — AI 自然语言检索（后端调用 AI 服务解析意图）
- `POST /api/images/{imageId}/edit` — 图片编辑（裁剪/滤镜/缩放等）
- `GET /api/images/{imageId}/thumbnail` — 获取或实时生成缩略图（支持 small/medium/large 与自定义尺寸）

## 数据库与缓存（要点）

- 主要表：`users`, `images`, `exif_data`, `tags`, `image_tags`, `thumbnails`。建表脚本与迁移统一放 `backend/src/main/resources/db/migration/`。
- Redis 用于缓存用户/图片/搜索结果等，建议过期策略：用户 30 分钟、图片 1 小时、搜索 15 分钟、标签 2 小时。

## 建议的开发任务优先级（可选）

1. 实现后端图片上传接口（含文件验证、存储、DB 元数据写入）并添加单元/集成测试。
2. 实现 EXIF 提取与持久化（使用 metadata-extractor 或类似库），并触发缩略图生成任务。
3. 实现缩略图服务（多尺寸、缓存）及对应 API。
4. 集成 AI 服务（图片分析），实现自动标签入库与 AI 检索桥接。
5. 实现前端上传组件、进度展示与图片库展示页并联调后端 API。
