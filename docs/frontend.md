# 前端（Vue 3 + Vite）

## 概览

- 技术栈：Vue 3 + TypeScript、Vite、Pinia、Vue Router、Element Plus。
- 主要职责：认证、图片上传/搜索/详情/标签管理、AI 对话检索、轮播高光展示。
- 开发命令：`cd frontend && npm install && npm run dev`；质量：`npm run lint`。

## 目录速览

- 入口与布局
  - [frontend/src/main.ts](frontend/src/main.ts)：应用创建、Element Plus 注册、Pinia/Router 挂载。
  - [frontend/src/App.vue](frontend/src/App.vue)：主框架，包含侧栏、头部与 `<router-view>`。
  - 全局样式：[frontend/src/styles/main.scss](frontend/src/styles/main.scss)
- 路由
  - [frontend/src/router/index.ts](frontend/src/router/index.ts)：路由表与导航守卫，登录态保护 `/dashboard` 及子路由；支持 redirect query。
- 状态（Pinia）
  - 认证：[frontend/src/stores/auth.ts](frontend/src/stores/auth.ts)：登录/注册/刷新令牌、持久化 token、用户信息。
  - 上传：[frontend/src/stores/imageUpload.ts](frontend/src/stores/imageUpload.ts)：候选队列、批量上传、摘要与结果。
  - 搜索：[frontend/src/stores/imageSearch.ts](frontend/src/stores/imageSearch.ts)：过滤条件、分页、结果集、排序。
  - 标签：[frontend/src/stores/imageTags.ts](frontend/src/stores/imageTags.ts)：标签列表、热门标签、AI 生成、增删标签。
- 服务（HTTP 封装）
  - Axios 基础配置与拦截器：[frontend/src/services/apiClient.ts](frontend/src/services/apiClient.ts)（附带 Bearer Token、401 处理）。
  - 认证 API：[frontend/src/services/authService.ts](frontend/src/services/authService.ts)
  - 图片 API：[frontend/src/services/imageService.ts](frontend/src/services/imageService.ts)（上传、搜索、下载原图/缩略图、编辑、详情、高光）。
  - 标签 API：[frontend/src/services/tagService.ts](frontend/src/services/tagService.ts)
  - AI 对话/搜索 API：[frontend/src/services/aiService.ts](frontend/src/services/aiService.ts)
- 类型定义：位于 [frontend/src/types](frontend/src/types)

## 主要页面/组件

- 仪表盘高光：[frontend/src/views/DashboardHome.vue](frontend/src/views/DashboardHome.vue)
  - 展示最近高光轮播、手动选择展示池、全屏播放。
- 批量上传：[frontend/src/views/ImageUpload.vue](frontend/src/views/ImageUpload.vue)
  - 拖拽/选择文件、隐私与描述设置、上传状态表、结果时间线。
- 搜索与列表：[frontend/src/views/images/ImageSearch.vue](frontend/src/views/images/ImageSearch.vue)
  - 多条件过滤（关键词/标签/设备/分辨率/时间/隐私/仅看自己），分页排序；AI 自然语言解析为搜索条件；支持编辑/删除/查看详情；下载缩略图/原图时使用 blob URL 缓存。
- 详情页：[frontend/src/views/images/ImageDetail.vue](frontend/src/views/images/ImageDetail.vue)
  - 原图/缩略图预览、EXIF、标签详情、权限、下载原图、打开编辑/标签管理面板。
- 标签管理页（独立）：[frontend/src/views/ImageTagManager.vue](frontend/src/views/ImageTagManager.vue)
  - 通过图片 ID 加载标签，支持自定义/AI 标签增删、热门标签快捷填充。
- AI 对话检索：[frontend/src/views/ai/AiChat.vue](frontend/src/views/ai/AiChat.vue)
  - Deepseek 对话与工具调用轨迹、解析出的搜索条件、匹配结果；一键同步到搜索页。
- 登录/注册：[frontend/src/views/AuthLogin.vue](frontend/src/views/AuthLogin.vue) / [frontend/src/views/AuthRegister.vue](frontend/src/views/AuthRegister.vue)
- 复用组件
  - 图片编辑弹窗：[frontend/src/components/ImageEditDialog.vue](frontend/src/components/ImageEditDialog.vue)（裁剪/旋转/色调实时预览）
  - 标签管理抽屉：[frontend/src/components/ImageTagManagerPanel.vue](frontend/src/components/ImageTagManagerPanel.vue)

## 路由与鉴权

- 受保护路由统一在 `beforeEach` 中检查 `authStore.isAuthenticated`，未登录重定向至 `/auth/login`，并附带 redirect query。
- `/auth/login` 与 `/auth/register` 对已登录用户自动跳转至 `/dashboard`。

## 交互要点

- 上传：队列去重、状态标签、错误提示；上传结果可跳转详情或标签管理。
- 搜索：AI 解析（Deepseek）可填充 filters；结果卡片显示分辨率/大小/时间/标签，支持编辑/删除；下载缩略图采用懒加载与 object URL 管理避免内存泄漏。
- 详情：下载原图/缩略图需权限；编辑完成后同步更新详情和预览；标签抽屉实时更新。
- 标签管理：支持自定义与 AI 标签混合提交；热门标签点击填入草稿；AI 生成可限制数量。
- 高光：本地存储记忆手动选择；可全屏播放轮播。

## 构建与质量

- 开发：`npm run dev`
- 生产构建：`npm run build`
- 代码质量：`npm run lint`（当前需修复 lint 报错后再通过）

## 对接后端

- 所有 API 基础地址来自 `VITE_API_BASE_URL`（在 `.env`/`.env.*` 配置）。
- 401 统一在 axios 拦截器处处理并清理登录态。
