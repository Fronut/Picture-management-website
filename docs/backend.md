# 后端（Spring Boot）

## 概览

- 技术栈：Spring Boot 3 / Java 17、Spring Security + JWT、Spring Data JPA、Flyway、Redis Cache、RestTemplate。
- 主要职责：用户认证、图片上传/存储、EXIF 提取、缩略图生成、标签管理、AI 交互代理、搜索与权限控制。
- 入口与配置：`application.yml` 多 profile（`dev`/`docker`/`prod`）；文件路径、AI 服务地址、Redis/DB 连接均可通过环境变量覆盖。

## 目录与关键类

- 配置
  - [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)（含 dev/docker/prod）
  - [backend/src/main/java/com/imagemanagement/config/SecurityConfig.java](backend/src/main/java/com/imagemanagement/config/SecurityConfig.java)（JWT 过滤器、CORS、放行路径）
  - [backend/src/main/java/com/imagemanagement/config/RedisConfig.java](backend/src/main/java/com/imagemanagement/config/RedisConfig.java)（缓存管理器）
  - [backend/src/main/java/com/imagemanagement/config/FileStorageProperties.java](backend/src/main/java/com/imagemanagement/config/FileStorageProperties.java)
- 控制器
  - 认证与会话：[AuthController](backend/src/main/java/com/imagemanagement/controller/AuthController.java)
  - 图片 CRUD & 搜索：[ImageController](backend/src/main/java/com/imagemanagement/controller/ImageController.java)
  - 图片内容访问（原图/缩略图）：[ImageContentController](backend/src/main/java/com/imagemanagement/controller/ImageContentController.java)
  - 标签管理：[TagController](backend/src/main/java/com/imagemanagement/controller/TagController.java)
  - AI 对话代理：[AiChatController](backend/src/main/java/com/imagemanagement/controller/AiChatController.java)
- 服务实现
  - 认证与令牌：[AuthServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/AuthServiceImpl.java)、[RefreshTokenServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/RefreshTokenServiceImpl.java)
  - 图片管线：上传/去重/EXIF/缩略图/编辑/搜索 [ImageServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/ImageServiceImpl.java)
  - 文件与缩略图：[FileStorageService](backend/src/main/java/com/imagemanagement/service/FileStorageService.java)、[ThumbnailServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/ThumbnailServiceImpl.java)
  - EXIF 解析：[ExifExtractionServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/ExifExtractionServiceImpl.java)
  - 标签与 AI 生成：[TagServiceImpl](backend/src/main/java/com/imagemanagement/service/impl/TagServiceImpl.java)、[AiServiceClient](backend/src/main/java/com/imagemanagement/client/AiServiceClient.java)
  - 内容权限校验：[ImageContentService](backend/src/main/java/com/imagemanagement/service/ImageContentService.java)
- 实体与迁移
  - 核心表：users、images、exif_data、tags、image_tags、thumbnails、refresh_tokens、content_hash 唯一索引
  - 迁移脚本位于 [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)

## 核心流程

- 认证
  - 注册/登录返回 JWT + 刷新令牌；刷新令牌记录于 Redis（或内存）并可注销。
  - `SecurityConfig` 放行 `/api/auth/**`、健康检查、公开缩略图/高光列表；其余需 Bearer Token。
- 上传
  - `ImageController.uploadImages` 接收 multipart；`ImageServiceImpl` 校验类型/大小、计算 content_hash 去重；写入磁盘 `/app/uploads`，持久化元数据，提取 EXIF，异步生成缩略图表记录与文件。
- 搜索
  - 支持关键词、标签、分辨率、拍摄设备、时间区间、隐私/仅看本人；使用 JPA 规范组合查询；分页排序。
  - 高光精选 `fetchHighlights` 返回公开且可展示的图片，用于首页轮播。
- 编辑
  - 支持裁剪、旋转、色调（亮度/对比/冷暖）调整；生成新文件替换原图，并刷新缩略图与尺寸字段。
- 标签
  - 自定义/AI/自动标签均存储于 `tags` + `image_tags`；可移除单条；热门标签统计使用计数。
  - AI 标签生成通过 [AiServiceClient](backend/src/main/java/com/imagemanagement/client/AiServiceClient.java) 调用 AI 微服务。
- AI 对话
  - `/api/ai/chat` 将用户输入转发到 AI 服务（Deepseek 工具调用），支持生成搜索条件或匹配结果。

## 配置要点

- 文件路径：默认 `/app/uploads` 和 `/app/thumbnails`，可通过 `UPLOAD_DIR` / `THUMBNAIL_DIR` 覆盖。
- 数据源：`spring.datasource.*` 来自 profile 环境变量；Flyway 启用。
- Redis：缓存注解用于用户/图片/搜索；缺省连接 `localhost:6379`。
- AI 服务：`AI_SERVICE_URL` 默认 `http://ai-service:5000`。
- JWT：`jwt.secret`、过期时间、刷新令牌过期在 `application.yml`。

## API 速览（主要路径）

- 认证
  - `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/logout`
- 图片
  - `POST /api/images/upload` 批量上传
  - `GET /api/images/search` 条件搜索
  - `GET /api/images/highlights` 首页精选
  - `GET /api/images/{id}` 详情；`DELETE /api/images/{id}` 删除（软删/彻底删取决于实现）
  - `POST /api/images/{id}/edit` 裁剪/旋转/色调
  - `GET /api/images/{id}/content` 原图；`GET /api/images/{id}/thumbnail/{thumbId}` 缩略图
- 标签
  - `GET /api/tags`（列表） / `GET /api/tags/popular`
  - `POST /api/tags/custom` / `POST /api/tags/ai/generate` / `DELETE /api/tags/{tagId}`
- AI 对话
  - `POST /api/ai/chat`（代理 AI 服务）

## 数据模型

- `users`：用户名/邮箱/密码（bcrypt）/角色
- `images`：owner、文件名、描述、路径、尺寸、mime、隐私、hash、上传时间
- `exif_data`：相机、曝光、光圈、ISO、焦距、拍摄时间、位置
- `thumbnails`：图片、多尺寸记录（small/medium/large 或定制）
- `tags` 与 `image_tags`：tag 类型（CUSTOM/AI/AUTO）、置信度、使用次数
- `refresh_tokens`：用户刷新令牌、状态、过期时间

## 缓存与权限

- 缓存：Redis CacheManager；常用缓存域覆盖用户/图片/搜索结果。
- 权限：`ImageContentService` 校验访问者是否 owner 或公开图片；缩略图/原图下载同样检查。
- CORS：默认允许常见方法/headers，可在 `application.yml` 或 `SecurityConfig` 调整。

## 开发与调试

- 运行：`cd backend && ./mvnw spring-boot:run`
- 代码风格：Java 17，避免写死 OS 路径；使用 `FileStorageProperties` 配置。
- 日志：默认 INFO；可通过 `logging.level.*` 配置。

## 测试

- 单元/集成：`./mvnw test`
- 预置数据：如需本地初始化，可在 `docker/mysql/init.sql` 或自建 migration 中添加。
