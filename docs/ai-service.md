# AI 微服务（Flask）

## 概览

- 技术栈：Flask + Gunicorn（可选）、requests；零样本视觉分类（CLIP scaffold）、可选 Deepseek 对话与 MCP 搜索工具链。
- 主要职责：
  - 标签建议：下载图片并生成 AI 标签（Baidu/CLIP 组合或 stub）。
  - 对话式搜索：接收用户自然语言，调用后台搜索工具，生成匹配结果与解析出的搜索条件。
  - 健康检查。

## 目录与关键文件

- 应用入口
  - [ai-service/app/**init**.py](ai-service/app/__init__.py)：`create_app` 注册蓝图与服务。
  - [ai-service/app/main.py](ai-service/app/main.py)：开发模式运行入口。
  - 配置：[ai-service/app/config.py](ai-service/app/config.py)（环境变量读取）。
- 路由
  - 健康检查：[ai-service/app/routes/health.py](ai-service/app/routes/health.py) — `GET /ai/v1/health`
  - 标签建议：[ai-service/app/routes/tags.py](ai-service/app/routes/tags.py) — `POST /ai/v1/tags/suggest`
  - 对话搜索：[ai-service/app/routes/search.py](ai-service/app/routes/search.py) — `POST /ai/v1/search/chat`
- 服务
  - 标签生成：[ai-service/app/services/tagging.py](ai-service/app/services/tagging.py)（下载图片、验证 MIME/尺寸、合并标签结果）
  - 视觉分类（Baidu/CLIP）：[ai-service/app/services/baidu_client.py](ai-service/app/services/baidu_client.py)、[ai-service/app/services/vision_classifier.py](ai-service/app/services/vision_classifier.py)
  - Deepseek 对话工具调用：[ai-service/app/services/deepseek_chat.py](ai-service/app/services/deepseek_chat.py)
  - 调用后端搜索/标签可用性工具（MCP）：[ai-service/app/services/mcp_search.py](ai-service/app/services/mcp_search.py)
  - 类型定义：[ai-service/app/services/tagging_types.py](ai-service/app/services/tagging_types.py)

## API 行为

- `GET /ai/v1/health`
  - 返回 `status: ok` 用于存活探针。
- `POST /ai/v1/tags/suggest`
  - 请求体：`{"imageUrl": "http...", "limit": 10, "hints": ["beach"], "imageId": 123}`
  - 响应：包含建议标签列表（name/type/confidence/source），过滤掉 MIME/尺寸不合法图片。
- `POST /ai/v1/search/chat`
  - 请求体：`{"message": "夕阳海滩人像", "limit": 12, "onlyOwn": true, "history": []}`
  - 行为：调用 Deepseek 模型（如配置）并通过工具调用后端 `/api/images/search` 和 `/api/tags/available`，返回解析 summary、matches、工具调用日志。

## 配置与环境变量

- 基础
  - `PORT`（默认 5000），`HOST`（默认 0.0.0.0）
  - `ENABLE_VISION_MODEL`（是否启用 CLIP/视觉模型），`VISION_MODEL_ID`
  - `AI_SERVICE_URL`（供后端使用，不影响服务本身）
- 外部服务
  - `BAIDU_API_KEY` / `BAIDU_SECRET_KEY`：启用 Baidu 图像识别客户端。
  - `DEEPSEEK_API_KEY`：启用 Deepseek 对话与工具调用。
  - `BACKEND_API_BASE`：用于 MCP 搜索工具访问后端 API（默认 `http://backend:8080` 容器网络）。
- 资源路径
  - 模型或大文件推荐挂载到 `/app/models`（在容器 compose 中设置卷）。

## 运行与部署

- 本地快速启动：
  - `cd ai-service && pip install -r requirements.txt`
  - `python -m app.main`
- 使用 Gunicorn（生产示例）：`gunicorn -w 2 -b 0.0.0.0:5000 'app:create_app()'`
- Docker：由根 `docker-compose.yml` 统一编排（服务名 `ai-service`）。

## 测试

- 运行：`cd ai-service && pytest -q`

## 与后端集成

- 后端通过 `AI_SERVICE_URL` 调用本服务的 `/ai/v1/tags/suggest` 与 `/ai/v1/search/chat`。
- 对话搜索内部会回调后端 `/api/images/search` 与 `/api/tags/available`，需保证网络可达且提供有效 JWT（在 MCP 工具配置中带上 `PICTURE_API_TOKEN`）。
