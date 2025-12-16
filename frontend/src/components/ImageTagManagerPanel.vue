<template>
  <el-drawer
    class="tag-manager-drawer"
    :model-value="modelValue"
    size="70%"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <template #header>
      <div class="drawer-header">
        <div>
          <h3>管理标签</h3>
          <p v-if="imageId" class="subtitle">针对图片 #{{ imageId }}</p>
          <p v-else class="subtitle">请选择有效的图片即可管理标签</p>
        </div>
      </div>
    </template>

    <el-empty v-if="!imageId" description="请先在图片详情中选择有效图片" />

    <div v-else class="drawer-content">
      <el-row :gutter="16">
        <el-col :md="16" :xs="24">
          <el-card shadow="never" class="tag-list-card">
            <template #header>
              <div class="card-header">
                <div>
                  <h4>已关联标签</h4>
                  <p class="card-subtitle">
                    与该图片绑定的全部标签，可直接移除
                  </p>
                </div>
                <el-button
                  text
                  size="small"
                  :loading="isLoading"
                  @click="reloadTags"
                >
                  刷新
                </el-button>
              </div>
            </template>

            <el-empty
              v-if="!tags.length && !isLoading"
              description="暂无标签"
            />
            <el-table v-else v-loading="isLoading" :data="tags" size="small">
              <el-table-column label="标签名" prop="tagName" min-width="150" />
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-tag :type="tagTypeColor(row.tagType)">
                    {{ formatTagType(row.tagType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="可信度" width="130">
                <template #default="{ row }">
                  {{ formatConfidence(row.confidence) }}
                </template>
              </el-table-column>
              <el-table-column label="使用次数" width="120">
                <template #default="{ row }">
                  {{ row.usageCount ?? 0 }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button
                    text
                    type="danger"
                    size="small"
                    :loading="isMutating"
                    @click="handleRemove(row.tagId)"
                  >
                    移除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :md="8" :xs="24">
          <el-card
            v-loading="popularLoading"
            shadow="never"
            class="popular-card"
          >
            <template #header>
              <div class="card-header">
                <div>
                  <h4>热门标签</h4>
                  <p class="card-subtitle">点击即可快速添加到自定义列表</p>
                </div>
                <el-button text size="small" @click="refreshPopular">
                  刷新
                </el-button>
              </div>
            </template>
            <el-empty v-if="!popularTags.length" description="暂无热门标签" />
            <el-space v-else wrap>
              <el-tag
                v-for="tag in popularTags"
                :key="tag.tagId"
                class="clickable-tag"
                @click="appendCustomTag(tag.tagName)"
              >
                {{ tag.tagName }}
              </el-tag>
            </el-space>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="form-row">
        <el-col :md="12" :xs="24">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <div>
                  <h4>添加自定义标签</h4>
                  <p class="card-subtitle">支持一次批量提交多个标签</p>
                </div>
              </div>
            </template>
            <el-form @submit.prevent>
              <el-form-item label="标签列表">
                <el-select
                  v-model="customTagInput"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="输入后按 Enter 添加，可点热门标签快速填充"
                >
                  <el-option
                    v-for="tag in popularTags"
                    :key="tag.tagId"
                    :label="tag.tagName"
                    :value="tag.tagName"
                  />
                </el-select>
              </el-form-item>
              <el-button
                type="primary"
                :loading="isMutating"
                :disabled="!customTagInput.length"
                @click="submitCustomTags"
              >
                提交
              </el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :md="12" :xs="24">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <div>
                  <h4>同步 AI 标签</h4>
                  <p class="card-subtitle">支持手动输入或直接让 AI 生成</p>
                </div>
              </div>
            </template>
            <div class="ai-form">
              <div
                v-for="(suggestion, index) in aiSuggestions"
                :key="index"
                class="ai-row"
              >
                <el-input
                  v-model="suggestion.name"
                  placeholder="如 sunset / portrait"
                />
                <el-input-number
                  v-model="suggestion.confidence"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :controls="false"
                  placeholder="置信度"
                />
                <el-button
                  text
                  type="danger"
                  :disabled="aiSuggestions.length === 1"
                  @click="removeSuggestionRow(index)"
                >
                  删除
                </el-button>
              </div>
              <el-button text type="primary" @click="addSuggestionRow">
                新增行
              </el-button>
            </div>
            <el-button
              type="primary"
              plain
              :loading="isMutating"
              @click="submitAiTags"
            >
              同步到后端
            </el-button>
            <el-divider>或</el-divider>
            <el-form
              class="ai-generate-form"
              label-position="top"
              @submit.prevent
            >
              <el-form-item label="AI 提示词（可选）">
                <el-select
                  v-model="aiHints"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="输入主题、地点或风格"
                />
              </el-form-item>
              <el-form-item label="生成标签数量">
                <el-input-number
                  v-model="aiGenerateLimit"
                  :min="1"
                  :max="20"
                  :step="1"
                />
              </el-form-item>
              <el-button
                type="success"
                plain
                :loading="aiGenerating"
                @click="handleGenerateAi"
              >
                让 AI 直接生成
              </el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { storeToRefs } from "pinia";

import { useImageTagStore } from "@/stores/imageTags";
import type { ImageTag } from "@/types/tag";

interface Props {
  modelValue: boolean;
  imageId: number | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "updated", tags: ImageTag[]): void;
}>();

const tagStore = useImageTagStore();
const {
  tags,
  isLoading,
  isMutating,
  popularTags,
  popularLoading,
  aiGenerating,
} = storeToRefs(tagStore);

const imageId = computed(() => props.imageId ?? null);
const initializedImageId = ref<number | null>(null);
const customTagInput = ref<string[]>([]);
const aiSuggestions = ref(
  Array.from({ length: 2 }, () => ({ name: "", confidence: 0.9 }))
);
const aiHints = ref<string[]>([]);
const aiGenerateLimit = ref<number>(6);

const resetForms = () => {
  customTagInput.value = [];
  aiHints.value = [];
  aiGenerateLimit.value = 6;
  aiSuggestions.value = Array.from({ length: 2 }, () => ({
    name: "",
    confidence: 0.9,
  }));
};

const initializeIfNeeded = async () => {
  if (!imageId.value) {
    return;
  }
  if (initializedImageId.value === imageId.value && tags.value.length) {
    return;
  }
  initializedImageId.value = imageId.value;
  await tagStore.initialize(imageId.value);
};

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void initializeIfNeeded();
    } else {
      resetForms();
    }
  }
);

watch(imageId, (newId, oldId) => {
  if (newId !== oldId) {
    initializedImageId.value = null;
  }
  if (props.modelValue && newId) {
    void initializeIfNeeded();
  }
  if (!newId) {
    tagStore.$reset();
  }
});

watch(tags, (next) => {
  emit("updated", next);
});

onBeforeUnmount(() => {
  tagStore.$reset();
});

const handleClose = () => {
  emit("update:modelValue", false);
};

const reloadTags = () => {
  if (!imageId.value) return;
  tagStore.loadTags(imageId.value);
};

const refreshPopular = () => {
  tagStore.loadPopularTags();
};

const appendCustomTag = (tagName: string) => {
  if (!customTagInput.value.includes(tagName)) {
    customTagInput.value.push(tagName);
  }
};

const submitCustomTags = () => {
  const payload = customTagInput.value.map((tag) => tag.trim()).filter(Boolean);
  if (!payload.length) {
    ElMessage.warning("请输入至少一个标签");
    return;
  }
  tagStore.addCustom(payload);
  customTagInput.value = [];
};

const addSuggestionRow = () => {
  aiSuggestions.value.push({ name: "", confidence: 0.85 });
};

const removeSuggestionRow = (index: number) => {
  if (aiSuggestions.value.length === 1) return;
  aiSuggestions.value.splice(index, 1);
};

const submitAiTags = () => {
  const payload = aiSuggestions.value
    .map((item) => ({
      name: item.name.trim(),
      confidence: item.confidence ?? undefined,
    }))
    .filter((item) => item.name.length > 0);
  if (!payload.length) {
    ElMessage.warning("请先填写 AI 标签");
    return;
  }
  tagStore.addAi(payload);
};

const handleGenerateAi = () => {
  const hints = aiHints.value.map((hint) => hint.trim()).filter(Boolean);
  const payload: { hints?: string[]; limit?: number } = {};
  if (hints.length) {
    payload.hints = hints;
  }
  if (aiGenerateLimit.value && aiGenerateLimit.value > 0) {
    payload.limit = aiGenerateLimit.value;
  }
  tagStore.generateAi(payload);
};

const handleRemove = (tagId: number) => {
  tagStore.remove(tagId);
};

const formatConfidence = (value: string | number | null) => {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return value;
  }
  return `${(num * 100).toFixed(0)}%`;
};

const tagTypeColor = (type: string) => {
  switch (type) {
    case "CUSTOM":
      return "success";
    case "AI":
      return "warning";
    default:
      return "info";
  }
};

const formatTagType = (type: string) => {
  switch (type) {
    case "CUSTOM":
      return "自定义";
    case "AI":
      return "AI 生成";
    default:
      return "自动";
  }
};
</script>

<style scoped>
.tag-manager-drawer :deep(.el-drawer__body) {
  padding: 0 16px 24px;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-header h3 {
  margin: 0;
}

.subtitle {
  margin: 0;
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header h4 {
  margin: 0;
}

.card-subtitle {
  margin: 2px 0 0;
  color: rgba(0, 0, 0, 0.5);
  font-size: 12px;
}

.clickable-tag {
  cursor: pointer;
}

.form-row {
  margin-top: 8px;
}

.ai-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ai-row .el-input,
.ai-row .el-input-number {
  flex: 1;
}

@media (max-width: 768px) {
  .tag-manager-drawer :deep(.el-drawer__body) {
    padding: 0 8px 16px;
  }
}
</style>
