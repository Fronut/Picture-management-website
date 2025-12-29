# Picture Management Website — 测试报告

## 环境与准备

1. 测试日期：2025-12-29（UTC+08:00）。
2. 仓库路径：`d:/Homework/BS/Repos/Picture-management-website`。
3. 依赖容器：MySQL、Redis，均通过 `docker compose up -d mysql redis` 启动。
4. 后端使用 Maven Wrapper，JDK17；AI 服务使用 Conda Python 3.13.5；前端使用 Node 18.
5. 测试之前未对源码做额外修改；所有命令均在 PowerShell 中执行。

### 容器启动输出

> Set-Location: A positional parameter cannot be found that accepts argument 'd:\\Homework\\BS\\Repos\\Picture-management-website'.
>
> [+] Running 2/0
>
> ✔ Container picture-redis Running 0.0s
>
> ✔ Container picture-mysql Running 0.0s

## 测试清单概览

1. 后端 `./mvnw test`（Spring Boot + JUnit + Mockito）。
2. AI 服务 `pytest -q`（Flask 服务接口与搜索逻辑）。
3. 前端 `npm run test:unit`（Vitest + Vue 组件与 Store 单测）。
4. 前端 `npm run test:e2e`（Playwright 多浏览器冒烟）。

---

## 测试用例明细

### 后端（B-01 ~ B-56）

- **B-01** — [backend/src/test/java/com/imagemanagement/ai/AiChatControllerTest.java](backend/src/test/java/com/imagemanagement/ai/AiChatControllerTest.java) `chat_shouldReturnAiResponseEnvelope`: 认证用户提交 `sunset beach` 查询时，断言 AI 服务回包包含 primary result、工具调用与 200 响应。
- **B-02** — [backend/src/test/java/com/imagemanagement/ai/AiChatControllerTest.java](backend/src/test/java/com/imagemanagement/ai/AiChatControllerTest.java) `chat_shouldRejectBlankQuery`: 发送空白查询并确认控制器返回 400 及“Validation failed”提示。
- **B-03** — [backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java](backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java) `shouldFetchHealthStatus`: 通过 MockWebServer 验证 Java 客户端能正确读取 `/ai/v1/health` 状态字段。
- **B-04** — [backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java](backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java) `shouldSuggestTagsFromUploadedImage`: 上传示例图像并确认返回标签及图片元数据键。
- **B-05** — [backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java](backend/src/test/java/com/imagemanagement/ai/AiServiceConnectivityTest.java) `shouldPropagateAiServiceErrors`: 向 AI 服务发送非法文件时断言客户端抛出 `AiServiceException` 且信息包含“valid image”。
- **B-06** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `register_shouldCreateUserAndReturnToken`: 注册新用户后校验返回的 JWT/refresh token 以及数据库写入。
- **B-07** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `login_shouldReturnTokenForExistingUser`: 预置账户登录并验证响应中的 token、refresh token 与邮箱字段。
- **B-08** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `register_shouldFailWhenUsernameExists`: 创建重复用户名时断言 400 及“Username already exists”。
- **B-09** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `refresh_shouldRotateRefreshTokens`: 调用刷新接口后验证新 refresh token 与旧 token 被置为失效。
- **B-10** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `logout_shouldRevokeRefreshToken`: 注销单一会话并确认对应 refresh token 被撤销。
- **B-11** — [backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java](backend/src/test/java/com/imagemanagement/auth/AuthControllerTest.java) `logoutAllSessions_shouldInvalidateEveryRefreshToken`: 传入 `logoutAllSessions=true` 后验证该用户所有 refresh token 均被清空。
- **B-12** — [backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java) `uploadImages_shouldPersistMetadataAndReturnResponse`（beach.jpeg）: 上传 beach.jpeg 并检查元数据、缩略图文件以及用户上传目录结构。
- **B-13** — [backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java) `uploadImages_shouldPersistMetadataAndReturnResponse`（man2.png）: 以 man2.png 触发同样验证，覆盖 PNG 文件属性确认。
- **B-14** — [backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java) `uploadImages_shouldPersistMetadataAndReturnResponse`（tree.jpeg）: 使用 tree.jpeg 再次验证缩略图 preset 均生成且上传目录存在。
- **B-15** — [backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageUploadControllerTest.java) `uploadImages_shouldRejectDuplicateFilesForSameUser`: 同一用户重复上传相同哈希文件时返回 400 与重复列表。
- **B-16** — [backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java) `searchImages_shouldRespectOwnershipAndTags`: 仅查询本人图片并过滤 `city` 标签时，断言返回列表与标签字段匹配。
- **B-17** — [backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java) `searchImages_shouldHidePrivateImagesFromOtherUsers`: 其他用户访问仅能看到公开图片，私有结果被过滤。
- **B-18** — [backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java) `searchImages_shouldExposeAccessPermissionsForOwnerViewerAndAdmin`: 比较所有者、普通查看者、管理员的 `access` 字段差异。
- **B-19** — [backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java) `searchImages_shouldRejectInvalidDimensionRange`: 设置 `minWidth > maxWidth` 时得到 400。
- **B-20** — [backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageSearchControllerTest.java) `searchImages_shouldFilterByActualWidthRangeFromRealUploads`: 真实上传不同尺寸图片后，以宽度区间过滤只命中期望对象。
- **B-21** — [backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java) `editImage_shouldCropAndRegenerateThumbnails`: 对样图裁剪并调整亮度，验证宽高缩减与缩略图重建。
- **B-22** — [backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java) `editImage_shouldRotateAndRegenerateThumbnails`: 旋转图片 90 度后验证宽高互换及缩略图仍存在。
- **B-23** — [backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageEditControllerTest.java) `editImage_shouldRejectMissingOperations`: 未提供任何编辑操作时返回 400 并提示。
- **B-24** — [backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java) `getImageDetail_shouldReturnRichPayloadForOwner`: 所有者查询详情时收到完整 summary、EXIF、标签、访问权限等数据。
- **B-25** — [backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java) `getImageDetail_shouldForbidPrivateImageForNonOwner`: 非拥有者访问私有图片被 403 拒绝。
- **B-26** — [backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java) `getImageDetail_shouldAllowAdminToViewPrivateImage`: 管理员可访问私有图片并拥有编辑、管理标签权限。
- **B-27** — [backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDetailControllerTest.java) `getImageDetail_shouldReturn404ForMissingImage`: 请求不存在的 ID 时返回 404。
- **B-28** — [backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java) `deleteImage_shouldRemoveMetadataAndFiles`: 删除本人图片后确认数据库记录与物理文件都被清理。
- **B-29** — [backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java) `deleteImage_shouldFailForNonOwner`: 其他用户尝试删除时被 403 拒绝且原文件仍存在。
- **B-30** — [backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java](backend/src/test/java/com/imagemanagement/image/ImageDeletionControllerTest.java) `deleteImage_shouldAllowAdminToRemoveOtherUsersImage`: 管理员可以删除他人图片并获得成功响应。
- **B-31** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `getTags_shouldReturnExistingTagsForImage`: 返回图片现有标签并校验名称、类型。
- **B-32** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `addCustomTags_shouldAttachTagsForOwner`: 添加 `Travel/Portrait` 自定义标签并验证列表及使用次数。
- **B-33** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `deleteTag_shouldRemoveAssociationAndDecrementUsage`: 删除标签关联后 `usageCount` 回到 0。
- **B-34** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `getPopularTags_shouldRespectLimitAndOrder`: `limit=1` 时仅返回使用最多的 `alpha`。
- **B-35** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `getAvailableTags_shouldReturnOnlyActiveTagsWithLimit`: 仅返回使用次数大于 0 的标签并保持顺序。
- **B-36** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `addAiTags_shouldDeduplicateAndStoreWithAiType`: 同名 AI 标签去重后仅保留置信度高的一条并标记类型。
- **B-37** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `generateAiTags_shouldInvokeAiServiceAndPersistResponse`: 模拟 AI 服务响应并确认标签写入数据库。
- **B-38** — [backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java](backend/src/test/java/com/imagemanagement/image/TagControllerIntegrationTest.java) `addCustomTags_shouldRejectWhenUserDoesNotOwnImage`: 非拥有者调用添加标签接口被提示无权限。
- **B-39** — [backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java](backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java) `loadOriginal_shouldAllowAdminToAccessPrivateImage`: 管理员获取私有原图时成功返回 `MediaType.IMAGE_JPEG` 内容。
- **B-40** — [backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java](backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java) `loadOriginal_shouldRejectNonOwnerForPrivateImage`: 普通用户访问私有图像抛出 `ForbiddenException`。
- **B-41** — [backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java](backend/src/test/java/com/imagemanagement/service/ImageContentServiceTest.java) `loadThumbnail_shouldReturnResourceWhenAccessAllowed`: 管理员请求缩略图时返回正确内容长度。
- **B-42** — [backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java](backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java) `storeFile_shouldPersistFileToUserDirectory`: 将 `photo.png` 存入用户目录并验证文件内容。
- **B-43** — [backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java](backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java) `storeFile_shouldRejectOversizedFiles`: 过大文件抛出 `BadRequestException` 并提示 size。
- **B-44** — [backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java](backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java) `storeFile_shouldSkipWhitelistWhenContentTypeMissing`: 缺失 MIME 的文件默认写入 `application/octet-stream`。
- **B-45** — [backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java](backend/src/test/java/com/imagemanagement/service/FileStorageServiceTest.java) `storeFile_shouldRejectUnsupportedMimeType`: `image/gif` 文件因为不在允许列表而被拒绝。
- **B-46** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `assignCustomTags_shouldCreateTagsAndAssociations`: 将 `Travel/Sunset` 标签写入数据库并创建关联。
- **B-47** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `removeTag_shouldDetachAssociationAndDecrementUsage`: 删除标签后，关联清空且 usage=0。
- **B-48** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `assignAiTags_shouldRespectConfidenceAndDeduplicate`: 大小写不同的 `cat` 建议仅保留置信度 0.91 的版本。
- **B-49** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `assignAiTags_shouldRejectEmptyPayload`: 空列表触发 `BadRequestException`。
- **B-50** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `applyAutomaticTags_shouldGenerateExifBasedTags`: 带有 EXIF 的图片自动生成年份、相机、地点标签。
- **B-51** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `applyAutomaticTags_shouldUseStrongConfidenceForFormatTag`: 即使缺少 EXIF 也会生成 `format:image/jpeg` 且置信度 1.00。
- **B-52** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `assignCustomTags_shouldDeduplicateAndNormalizeNames`: `City` 不同写法被统一为一条标签。
- **B-53** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `getAvailableTags_shouldReturnOnlyTagsWithUsageCountAndOrderByUsage`: 返回按使用次数降序的标签列表并排除未使用项。
- **B-54** — [backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/TagServiceImplTest.java) `getTagsForImage_shouldFailWhenImageMissing`: 查询不存在的图片 ID 时抛出 `BadRequestException`。
- **B-55** — [backend/src/test/java/com/imagemanagement/service/impl/ExifExtractionServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/ExifExtractionServiceImplTest.java) `extract_shouldPopulateExifFieldsWhenMetadataExists`: 解析出的相机、曝光、ISO、焦距等字段被写入 `ExifData`。
- **B-56** — [backend/src/test/java/com/imagemanagement/service/impl/ExifExtractionServiceImplTest.java](backend/src/test/java/com/imagemanagement/service/impl/ExifExtractionServiceImplTest.java) `extract_shouldReturnEmptyWhenReaderFails`: 当元数据读取抛出异常时返回空结果。

### AI 服务（AI-01 ~ AI-04）

- **AI-01** — [ai-service/tests/test_health.py](ai-service/tests/test_health.py) `test_health_endpoint`: GET `/ai/v1/health` 并确认 `status=ok` 且 `data.status=healthy`。
- **AI-02** — [ai-service/tests/test_tags.py](ai-service/tests/test_tags.py) `test_tag_suggestions_from_real_file`: 使用真实图片请求标签，断言返回列表非空并记录分类器调用。
- **AI-03** — [ai-service/tests/test_tags.py](ai-service/tests/test_tags.py) `test_tag_suggestions_respect_limit_with_real_images`: 设置 `limit=2` 后仅返回两条标签。
- **AI-04** — [ai-service/tests/test_chat_search.py](ai-service/tests/test_chat_search.py) `test_chat_search_with_stub`: Stub 聊天搜索返回固定描述与 `searchPayload.size=2`。

### 前端单元测试（F-01 ~ F-15）

- **F-01** — [frontend/src/stores/**tests**/imageUploadStore.spec.ts](frontend/src/stores/__tests__/imageUploadStore.spec.ts) `skips duplicate files and notifies user`: 重复添加同一文件时仅保留一条候选并展示提示。
- **F-02** — [frontend/src/stores/**tests**/imageUploadStore.spec.ts](frontend/src/stores/__tests__/imageUploadStore.spec.ts) `warns when uploading without ready files`: 在无候选文件时调用 `upload()` 会弹出警告且不触发 API。
- **F-03** — [frontend/src/stores/**tests**/imageUploadStore.spec.ts](frontend/src/stores/__tests__/imageUploadStore.spec.ts) `uploads ready files and clears candidates on success`: 成功上传后校验服务调用参数、上传结果与统计摘要。
- **F-04** — [frontend/src/stores/**tests**/imageUploadStore.spec.ts](frontend/src/stores/__tests__/imageUploadStore.spec.ts) `marks failed uploads and surfaces duplicate details`: 模拟 Axios 错误后，候选状态标记为 `error` 并展示重复详情。
- **F-05** — [frontend/src/stores/**tests**/authStore.spec.ts](frontend/src/stores/__tests__/authStore.spec.ts) `starts unauthenticated by default`: 新 store 默认无用户、无 token、未认证。
- **F-06** — [frontend/src/stores/**tests**/authStore.spec.ts](frontend/src/stores/__tests__/authStore.spec.ts) `clears state, storage, and notifies backend on logout`: 调用 `logout()` 清理 Pinia 状态、本地缓存并通知后端。
- **F-07** — [frontend/src/stores/**tests**/authStore.spec.ts](frontend/src/stores/__tests__/authStore.spec.ts) `refreshes the session when forced`: `ensureSession(true)` 将刷新 token 并写入新的访问凭证。
- **F-08** — [frontend/src/stores/**tests**/imageSearchStore.spec.ts](frontend/src/stores/__tests__/imageSearchStore.spec.ts) `updates and resets filters`: 更新过滤条件后 `resetFilters()` 恢复默认值。
- **F-09** — [frontend/src/stores/**tests**/imageSearchStore.spec.ts](frontend/src/stores/__tests__/imageSearchStore.spec.ts) `fetches search results and updates pagination`: 成功查询后更新结果列表及分页信息，并推进下一页索引。
- **F-10** — [frontend/src/stores/**tests**/imageSearchStore.spec.ts](frontend/src/stores/__tests__/imageSearchStore.spec.ts) `shows an error message when search fails`: API 抛错时弹出错误消息并重置 `loading`。
- **F-11** — [frontend/src/stores/**tests**/imageTagStore.spec.ts](frontend/src/stores/__tests__/imageTagStore.spec.ts) `initializes by loading tags and popular tags`: `initialize()` 会记录当前图片 ID、加载标签及热门标签。
- **F-12** — [frontend/src/stores/**tests**/imageTagStore.spec.ts](frontend/src/stores/__tests__/imageTagStore.spec.ts) `warns when adding custom tags without image`: 未设置 `currentImageId` 时调用 `addCustom` 只给出警告。
- **F-13** — [frontend/src/stores/**tests**/imageTagStore.spec.ts](frontend/src/stores/__tests__/imageTagStore.spec.ts) `adds custom tags and updates state`: 成功添加 `macro` 标签后更新 `tags` 列表并弹出成功消息。
- **F-14** — [frontend/src/stores/**tests**/imageTagStore.spec.ts](frontend/src/stores/__tests__/imageTagStore.spec.ts) `removes tags and reloads list`: 删除标签后重新加载并验证 `tags` 与成功提示。
- **F-15** — [frontend/src/stores/**tests**/imageTagStore.spec.ts](frontend/src/stores/__tests__/imageTagStore.spec.ts) `sorts tags by confidence in descending order`: `loadTags()` 将不同置信度的标签降序排列。

### 前端端到端测试（E2E-01）

- **E2E-01** — [frontend/tests/e2e/smoke.spec.ts](frontend/tests/e2e/smoke.spec.ts) `renders the application shell`: Playwright 在 Chromium/WebKit/Firefox 中访问 `/`，验证页面标题与 `#app` 元素可见。

---

## 后端 — Maven 测试

- 执行命令：`Set-Location -Path d:\\Homework\\BS\\Repos\\Picture-management-website\\backend; .\\mvnw.cmd test`
- 数据依赖：依托前置启动的 MySQL/Redis 容器；测试过程中 Flyway 负责迁移，Hibernate 自动建表。
- 结果：56 个测试全部通过，无失败、无跳过。
- 关键输出：

```
[INFO] Scanning for projects...
[INFO]
[INFO] -----------< com.imagemanagement:picture-management-backend >-----------
[INFO] Building Picture Management Backend 1.0.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-resources-plugin:3.3.1:resources (default-resources) @ picture-management-backend ---
[INFO] Copying 1 resource from src\main\resources to target\classes
[INFO] Copying 3 resources from src\main\resources to target\classes
[INFO]
[INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ picture-management-backend ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- maven-resources-plugin:3.3.1:testResources (default-testResources) @ picture-management-backend ---
[INFO] Copying 4 resources from src\test\resources to target\test-classes
[INFO]
[INFO] --- maven-compiler-plugin:3.11.0:testCompile (default-testCompile) @ picture-management-backend ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- maven-surefire-plugin:3.0.0:test (default-test) @ picture-management-backend ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.imagemanagement.ai.AiChatControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.204 s - in com.imagemanagement.ai.AiChatControllerTest
[INFO] Running com.imagemanagement.ai.AiServiceConnectivityTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.401 s - in com.imagemanagement.ai.AiServiceConnectivityTest
[INFO] Running com.imagemanagement.auth.AuthControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.886 s - in com.imagemanagement.auth.AuthControllerTest
[INFO] Running com.imagemanagement.image.ImageDeletionControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.879 s - in com.imagemanagement.image.ImageDeletionControllerTest
[INFO] Running com.imagemanagement.image.ImageDetailControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.605 s - in com.imagemanagement.image.ImageDetailControllerTest
[INFO] Running com.imagemanagement.image.ImageEditControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.522 s - in com.imagemanagement.image.ImageEditControllerTest
[INFO] Running com.imagemanagement.image.ImageSearchControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.192 s - in com.imagemanagement.image.ImageSearchControllerTest
[INFO] Running com.imagemanagement.image.ImageUploadControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.954 s - in com.imagemanagement.image.ImageUploadControllerTest
[INFO] Running com.imagemanagement.image.TagControllerIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.586 s - in com.imagemanagement.image.TagControllerIntegrationTest
[INFO] Running com.imagemanagement.service.FileStorageServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s - in com.imagemanagement.service.FileStorageServiceTest
[INFO] Running com.imagemanagement.service.ImageContentServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.194 s - in com.imagemanagement.service.ImageContentServiceTest
[INFO] Running com.imagemanagement.service.impl.ExifExtractionServiceImplTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s - in com.imagemanagement.service.impl.ExifExtractionServiceImplTest
[INFO] Running com.imagemanagement.service.impl.TagServiceImplTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.652 s - in com.imagemanagement.service.impl.TagServiceImplTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  20.265 s
[INFO] Finished at: 2025-12-29T01:49:30+08:00
[INFO] ------------------------------------------------------------------------
```

---

## AI 服务 — Pytest

- 执行命令：`Set-Location -Path d:\\Homework\\BS\\Repos\\Picture-management-website\\ai-service; $env:PYTHONPATH = 'd:\\Homework\\BS\\Repos\\Picture-management-website\\ai-service'; D:/anaconda3/Scripts/conda.exe run -p D:\\anaconda3 --no-capture-output pytest -q`
- 说明：为确保包可见性，临时设置 `PYTHONPATH` 指向 `ai-service` 根目录，并在测试完成后清空该变量。
- 结果：4 个测试全部通过，执行时长 0.15s，无失败、无跳过。
- Pytest 输出原文：

> .... [100%]
>
> 4 passed in 0.15s

---

## 前端 — Vitest 单元测试

- 执行命令：`Set-Location -Path d:\\Homework\\BS\\Repos\\Picture-management-website\\frontend; npm run test:unit`
- 环境：使用项目自带 `package.json` 中的 `vitest` 配置，默认 watch 模式。
- 结果：4 份测试文件、15 条断言，全部通过。
- 输出原文：

```
> picture-management-frontend@1.0.0 test:unit
> vitest


 DEV  v0.34.6 D:/Homework/BS/Repos/Picture-management-website/frontend

 ✓ src/stores/__tests__/imageUploadStore.spec.ts (4)
 ✓ src/stores/__tests__/authStore.spec.ts (3)
 ✓ src/stores/__tests__/imageSearchStore.spec.ts (3)
 ✓ src/stores/__tests__/imageTagStore.spec.ts (5)

 Test Files  4 passed (4)
      Tests  15 passed (15)
   Start at  01:34:04
   Duration  1.13s (transform 222ms, setup 0ms, collect 782ms, tests 47ms, environment 2.15s, prepare 458ms)


 PASS  Waiting for file changes...
       press h to show help, press q to quit
```

---

## 前端 — Playwright 端到端测试

- 执行命令：`Set-Location -Path d:\\Homework\\BS\\Repos\\Picture-management-website\\frontend; npm run test:e2e`
- 流程：Playwright 自动启动本地 `vite` 开发服务器（host 127.0.0.1, port 4173），随后并行运行 Chromium / WebKit / Firefox 冒烟用例。
- 结果：3 条跨浏览器测试全部通过，耗时约 4.9s，无失败无跳过。
- 输出原文：

```
> picture-management-frontend@1.0.0 test:e2e
> playwright test

[WebServer]
[WebServer] > picture-management-frontend@1.0.0 dev
[WebServer] > vite --host 127.0.0.1 --port 4173
[WebServer]
[WebServer]
[WebServer]   VITE v4.5.14  ready in 282 ms
[WebServer]
[WebServer]   ➜  Local:   http://127.0.0.1:4173/

Running 3 tests using 3 workers

  ✓  1 [chromium] › tests\e2e\smoke.spec.ts:4:3 › Landing page › renders the application shell (1.1s)
  ✓  2 [webkit] › tests\e2e\smoke.spec.ts:4:3 › Landing page › renders the application shell (1.3s)
  ✓  3 [firefox] › tests\e2e\smoke.spec.ts:4:3 › Landing page › renders the application shell (1.3s)
[WebServer] DEPRECATION WARNING [legacy-js-api]: The legacy JS API is deprecated and will be removed in Dart Sass 2.0.0.
[WebServer]
[WebServer] More info: https://sass-lang.com/d/legacy-js-api
[WebServer]

  3 passed (4.9s)
```
