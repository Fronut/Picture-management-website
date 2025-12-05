<template>
  <section class="tag-manager">
    <el-page-header content="图片标签管理" @back="handleBack" />

    <el-card shadow="never" class="selector-card">
      <el-form inline @submit.prevent>
        <el-form-item label="图片 ID">
          <el-input-number
            v-model="editableImageId"
            :min="1"
            :controls="false"
            placeholder="输入图片 ID"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :disabled="!editableImageId"
            @click="handleLoadImage"
          >
            加载标签
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!imageId"
        title="请通过上方输入框指定一个图片 ID"
        type="info"
        :closable="false"
      />
    </el-card>

    <el-row :gutter="16">
      <el-col :md="16" :xs="24">
        <el-card shadow="never" class="tag-list-card">
          <template #header>
            <div class="card-header">
              <h3>已关联标签</h3>
              <el-button
                text
                size="small"
                :loading="isLoading"
                :disabled="!imageId"
                @click="reloadTags"
              >
                刷新
              </el-button>
            </div>
          </template>

          <el-empty v-if="!tags.length && !isLoading" description="暂无标签" />

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
        <el-card v-loading="popularLoading" shadow="never" class="popular-card">
          <template #header>
            <div class="card-header">
              <h3>热门标签</h3>
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
              <h3>添加自定义标签</h3>
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
                placeholder="输入后按 Enter 添加，可点击热门标签快速填充"
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
              <h3>同步 AI 标签</h3>
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
                placeholder="输入主题、地点或风格，用于提示 AI"
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
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";

import { useImageTagStore } from "@/stores/imageTags";

const router = useRouter();
const route = useRoute();
const tagStore = useImageTagStore();

const {
  tags,
  isLoading,
  isMutating,
  popularTags,
  popularLoading,
  aiGenerating,
} = storeToRefs(tagStore);

const imageId = computed(() => {
  const raw = Number(route.params.imageId);
  return Number.isNaN(raw) ? null : raw;
});

const editableImageId = ref<number | null>(imageId.value);

watch(
  () => route.params.imageId,
  (newValue) => {
    const parsed = Number(newValue);
    if (Number.isNaN(parsed)) {
      tagStore.$reset();
      editableImageId.value = null;
      return;
    }
    editableImageId.value = parsed;
    tagStore.initialize(parsed);
  },
  { immediate: true }
);

const customTagInput = ref<string[]>([]);
const aiSuggestions = ref(
  Array.from({ length: 2 }, () => ({ name: "", confidence: 0.9 }))
);
const aiHints = ref<string[]>([]);
const aiGenerateLimit = ref<number>(6);

const handleLoadImage = () => {
  if (!editableImageId.value) {
    ElMessage.warning("请输入有效的图片 ID");
    return;
  }
  router.replace({
    name: "image-tags",
    params: { imageId: editableImageId.value },
  });
  tagStore.initialize(editableImageId.value);
};

const handleBack = () => {
  router.back();
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
.tag-manager {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding-bottom: 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.selector-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header h3 {
  margin: 0;
}

.tag-list-card {
  overflow-x: auto;
}

.tag-list-card :deep(.el-table) {
  min-width: 560px;
}

.popular-card {
  min-height: 200px;
}

.clickable-tag {
  cursor: pointer;
}

.form-row {
  margin-top: 16px;
}

.ai-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-generate-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.ai-row {
  display: grid;
  grid-template-columns: 1fr 120px auto;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .ai-row {
    grid-template-columns: 1fr;
  }
}
</style>
