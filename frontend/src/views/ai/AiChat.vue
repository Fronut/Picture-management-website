<template>
  <section class="ai-chat">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="chat-card">
          <template #header>
            <div class="card-header">
              <div>
                <h3>Deepseek 对话</h3>
                <p class="subtitle">支持自然语言检索并展示函数调用过程</p>
              </div>
              <el-space wrap>
                <div class="control">
                  <span class="label">返回数量</span>
                  <el-input-number
                    v-model="limit"
                    :min="1"
                    :max="20"
                    size="small"
                  />
                </div>
                <el-switch
                  v-model="onlyOwn"
                  active-text="仅看我的图片"
                  size="small"
                />
              </el-space>
            </div>
          </template>

          <div v-if="conversation.length" class="conversation">
            <div
              v-for="(message, index) in conversation"
              :key="index"
              class="message"
              :class="message.role"
            >
              <div class="message-meta">
                <span class="role-tag" :class="message.role">
                  {{ message.role === "user" ? "你" : "Deepseek" }}
                </span>
              </div>
              <div
                v-if="message.role === 'assistant'"
                class="message-content markdown"
                v-html="renderMarkdown(message.content)"
              ></div>
              <p v-else class="message-content">{{ message.content }}</p>
              <div v-if="message.toolCalls?.length" class="inline-tools">
                <p class="tool-title">调用的函数</p>
                <el-timeline>
                  <el-timeline-item
                    v-for="call in message.toolCalls"
                    :key="call.id || call.function?.name"
                    type="primary"
                  >
                    <div class="tool-call">
                      <p class="tool-name">
                        {{ call.function?.name || "(未知函数)" }}
                      </p>
                      <pre class="tool-args">{{
                        formatArguments(call.function?.arguments)
                      }}</pre>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </div>
          <el-empty
            v-else
            description="还没有对话，问问 Deepseek 如何帮你找到图片吧"
          />

          <div class="composer">
            <el-input
              v-model="input"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="例如：帮我找黄昏海边的人像，或问 Deepseek 如何筛选图片"
              @keyup.enter.exact.prevent="sendMessage"
            />
            <div class="composer-actions">
              <el-button type="primary" :loading="sending" @click="sendMessage">
                发送
              </el-button>
              <el-button text @click="resetConversation">清空</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="result-card">
          <template #header>
            <div class="card-header">
              <div>
                <h3>调用轨迹与结果</h3>
                <p class="subtitle">展示工具调用、解释和匹配到的图片</p>
              </div>
              <el-button
                v-if="primaryResult"
                size="small"
                type="primary"
                @click="applyToSearch"
              >
                同步到搜索页
              </el-button>
            </div>
          </template>

          <el-empty
            v-if="!lastResult"
            description="发送消息后会展示 Deepseek 的工具调用"
          />

          <div v-else class="result-body">
            <el-alert
              :title="lastResult.message"
              type="success"
              :closable="false"
              show-icon
            />

            <section class="tool-section">
              <h4>工具调用</h4>
              <el-empty v-if="!toolCalls.length" description="暂无工具调用" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="call in toolCalls"
                  :key="call.id || call.function?.name"
                  color="#409eff"
                >
                  <div class="tool-call">
                    <p class="tool-name">
                      {{ call.function?.name || "(未知函数)" }}
                    </p>
                    <pre class="tool-args">{{
                      formatArguments(call.function?.arguments)
                    }}</pre>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </section>

            <section v-if="primaryResult" class="payload-section">
              <h4>解析出的搜索条件</h4>
              <div class="payload-grid">
                <div class="payload-item" v-if="searchPayload.keyword">
                  <span class="label">关键词</span>
                  <strong>{{ searchPayload.keyword }}</strong>
                </div>
                <div class="payload-item" v-if="searchPayload.tags?.length">
                  <span class="label">标签</span>
                  <el-space wrap>
                    <el-tag
                      v-for="tag in searchPayload.tags"
                      :key="tag"
                      size="small"
                    >
                      {{ tag }}
                    </el-tag>
                  </el-space>
                </div>
                <div class="payload-item" v-if="searchPayload.onlyOwn">
                  <span class="label">仅看我的图片</span>
                  <strong>已启用</strong>
                </div>
                <div
                  class="payload-item"
                  v-if="
                    searchPayload.privacyLevel &&
                    searchPayload.privacyLevel !== 'ALL'
                  "
                >
                  <span class="label">隐私</span>
                  <strong>{{ searchPayload.privacyLevel }}</strong>
                </div>
                <div
                  class="payload-item"
                  v-if="searchPayload.uploadedFrom && searchPayload.uploadedTo"
                >
                  <span class="label">时间</span>
                  <strong
                    >{{ formatDate(searchPayload.uploadedFrom) }} ~
                    {{ formatDate(searchPayload.uploadedTo) }}</strong
                  >
                </div>
              </div>
            </section>

            <section v-if="matches.length" class="matches-section">
              <h4>匹配到的图片 ({{ matches.length }})</h4>
              <div class="match-list">
                <el-card
                  v-for="image in matches"
                  :key="image.id"
                  shadow="hover"
                  class="match-card"
                >
                  <div class="match-title">{{ image.originalFilename }}</div>
                  <p class="match-desc">
                    {{ image.description || "暂无描述" }}
                  </p>
                  <el-space wrap>
                    <el-tag
                      v-for="tag in image.tags"
                      :key="tag"
                      size="small"
                      type="info"
                    >
                      {{ tag }}
                    </el-tag>
                  </el-space>
                  <p class="match-meta">
                    {{ formatResolution(image) }} ·
                    {{ formatBytes(image.fileSize) }} ·
                    {{ formatDate(image.uploadTime) }}
                  </p>
                  <div class="match-actions">
                    <el-button text size="small" @click="goToDetail(image.id)">
                      查看详情
                    </el-button>
                  </div>
                </el-card>
              </div>
            </section>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { ElMessage } from "element-plus";
import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";

import { chatSearchImages } from "@/services/aiService";
import type { AiChatMessage, AiChatSearchResult, AiToolCall } from "@/types/ai";
import type { ImageSearchPayload, ImageSearchResult } from "@/types/image";
import { useImageSearchStore } from "@/stores/imageSearch";

interface ChatEntry {
  role: "user" | "assistant";
  content: string;
  toolCalls?: AiToolCall[];
}

const router = useRouter();
const store = useImageSearchStore();
const conversation = ref<ChatEntry[]>([]);
const lastResult = ref<AiChatSearchResult | null>(null);
const input = ref("");
const sending = ref(false);
const limit = ref(store.filters.size ?? 12);
const onlyOwn = ref(store.filters.onlyOwn ?? false);

const primaryResult = computed(() => lastResult.value?.primaryResult || null);
const toolCalls = computed(() => lastResult.value?.toolCalls ?? []);
const matches = computed(() => primaryResult.value?.matches ?? []);
const searchPayload = computed(() =>
  normalizePayload(primaryResult.value?.searchPayload)
);

const formatDate = (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm");
const formatBytes = (size: number) => {
  if (!size && size !== 0) return "-";
  const units = ["B", "KB", "MB", "GB"];
  const unitIndex = Math.min(
    units.length - 1,
    Math.floor(Math.log(size) / Math.log(1024))
  );
  const value = size / 1024 ** unitIndex;
  return `${value.toFixed(2)} ${units[unitIndex]}`;
};
const formatResolution = (image: ImageSearchResult) => {
  if (!image.width || !image.height) {
    return "未知分辨率";
  }
  return `${image.width} × ${image.height}`;
};

const goToDetail = (imageId: number) => {
  router.push({ name: "image-detail", params: { imageId } });
};

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
});

const renderMarkdown = (content: string) => {
  const rawHtml = markdown.render(content);
  return DOMPurify.sanitize(rawHtml);
};

const formatArguments = (args?: string) => {
  if (!args) return "(无参数)";
  try {
    const parsed = JSON.parse(args);
    return JSON.stringify(parsed, null, 2);
  } catch (error) {
    return args;
  }
};

const normalizePayload = (payload?: Partial<ImageSearchPayload>) => {
  const normalized: Partial<ImageSearchPayload> = {};
  if (!payload) return normalized;
  if (typeof payload.keyword === "string") normalized.keyword = payload.keyword;
  if (Array.isArray(payload.tags))
    normalized.tags = payload.tags.map((tag) => `${tag}`);
  if (typeof payload.onlyOwn === "boolean")
    normalized.onlyOwn = payload.onlyOwn;
  if (payload.privacyLevel && typeof payload.privacyLevel === "string") {
    normalized.privacyLevel =
      payload.privacyLevel as ImageSearchPayload["privacyLevel"];
  }
  if (typeof payload.cameraMake === "string")
    normalized.cameraMake = payload.cameraMake;
  if (typeof payload.cameraModel === "string")
    normalized.cameraModel = payload.cameraModel;
  const numericKeys: Array<keyof ImageSearchPayload> = [
    "minWidth",
    "maxWidth",
    "minHeight",
    "maxHeight",
  ];
  numericKeys.forEach((key) => {
    const value = payload[key];
    if (typeof value === "number") {
      normalized[key] = value as never;
    }
  });
  if (payload.uploadedFrom) normalized.uploadedFrom = `${payload.uploadedFrom}`;
  if (payload.uploadedTo) normalized.uploadedTo = `${payload.uploadedTo}`;
  if (typeof payload.size === "number") normalized.size = payload.size;
  if (typeof payload.page === "number") normalized.page = payload.page;
  if (typeof payload.sortBy === "string") {
    normalized.sortBy = payload.sortBy as ImageSearchPayload["sortBy"];
  }
  if (typeof payload.sortDirection === "string") {
    normalized.sortDirection =
      payload.sortDirection as ImageSearchPayload["sortDirection"];
  }
  return normalized;
};

const sendMessage = async () => {
  const trimmed = input.value.trim();
  if (!trimmed) {
    ElMessage.warning("请输入问题或指令");
    return;
  }

  const history: AiChatMessage[] = conversation.value.map(
    ({ role, content }) => ({
      role,
      content,
    })
  );

  const userEntry: ChatEntry = { role: "user", content: trimmed };
  conversation.value.push(userEntry);
  sending.value = true;

  try {
    const result = await chatSearchImages(
      trimmed,
      limit.value,
      onlyOwn.value,
      history
    );
    lastResult.value = result;
    const assistantEntry: ChatEntry = {
      role: "assistant",
      content: result.message,
      toolCalls: result.toolCalls ?? [],
    };
    conversation.value.push(assistantEntry);
    input.value = "";
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "AI 对话失败，请稍后再试"
    );
  } finally {
    sending.value = false;
  }
};

const applyToSearch = async () => {
  if (!primaryResult.value) {
    ElMessage.warning("暂无可同步的结果");
    return;
  }
  const payload = normalizePayload(primaryResult.value.searchPayload);
  store.updateFilters({ ...store.filters, ...payload, page: 0 });
  if (primaryResult.value.page) {
    store.hydrateFromPage(primaryResult.value.page);
  } else {
    await store.searchWithFilters(payload);
  }
  router.push({ name: "image-search" });
  ElMessage.success("已同步到搜索页");
};

const resetConversation = () => {
  conversation.value = [];
  lastResult.value = null;
};
</script>

<style scoped>
.ai-chat {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.subtitle {
  margin: 0;
  color: rgba(0, 0, 0, 0.55);
  font-size: 13px;
}

.control {
  display: flex;
  align-items: center;
  gap: 6px;
}

.control .label {
  color: rgba(0, 0, 0, 0.65);
  font-size: 12px;
}

.conversation {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.message {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
}

.message.user {
  border-color: #c6e2ff;
  background: #f5f9ff;
}

.message.assistant {
  border-color: #e4e7ed;
  background: #fafafa;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.role-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
  background: #f0f0f0;
}

.role-tag.user {
  background: #ecf5ff;
  color: #409eff;
}

.role-tag.assistant {
  background: #fdf6ec;
  color: #e6a23c;
}

.message-content {
  margin: 0;
  line-height: 1.6;
}

.message-content.markdown {
  color: rgba(0, 0, 0, 0.85);
  white-space: normal;
}

.message-content.markdown :where(p, ul, ol, pre, blockquote) {
  margin: 0 0 8px;
}

.message-content.markdown ul,
.message-content.markdown ol {
  padding-left: 18px;
}

.message-content.markdown code {
  background: #f2f4f8;
  border-radius: 4px;
  padding: 0 4px;
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
}

.message-content.markdown pre {
  background: #1e1e1e;
  color: #f8f8f2;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}

.message-content.markdown pre code {
  background: transparent;
  padding: 0;
  color: inherit;
}

.message-content.markdown a {
  color: #409eff;
  text-decoration: underline;
}

.inline-tools {
  margin-top: 8px;
}

.tool-title {
  margin: 0 0 4px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}

.composer {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.result-card .result-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tool-call {
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px 10px;
}

.tool-name {
  margin: 0 0 4px;
  font-weight: 600;
}

.tool-args {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.payload-section {
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}

.payload-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}

.payload-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.payload-item .label {
  display: block;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  margin-bottom: 4px;
}

.matches-section {
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}

.match-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.match-card {
  min-height: 120px;
}

.match-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.match-desc {
  margin: 0 0 6px;
  color: rgba(0, 0, 0, 0.55);
}

.match-meta {
  margin: 8px 0 0;
  color: rgba(0, 0, 0, 0.55);
  font-size: 13px;
}

.match-actions {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .composer-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .ai-chat {
    padding-bottom: 12px;
  }

  .card-header {
    align-items: flex-start;
    gap: 8px;
  }

  .chat-card,
  .result-card {
    margin-bottom: 12px;
  }

  .message-content.markdown pre {
    font-size: 12px;
    padding: 8px;
  }

  .composer-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
