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
      <el-col :md="14" :xs="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>手动添加标签</h3>
            </div>
          </template>
          <div class="tag-draft-list">
            <div
              v-for="(draft, index) in tagDrafts"
              :key="index"
              class="tag-draft-row"
            >
              <el-select
                v-model="draft.name"
                filterable
                allow-create
                default-first-option
                placeholder="输入或选择标签"
                class="name-select"
              >
                <el-option
                  v-for="tag in popularTags"
                  :key="tag.tagId"
                  :label="tag.tagName"
                  :value="tag.tagName"
                />
              </el-select>
              <el-select v-model="draft.tagType" class="type-select">
                <el-option label="自定义" value="CUSTOM" />
                <el-option label="AI 生成" value="AI" />
              </el-select>
              <el-input-number
                v-model="draft.confidence"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="2"
                :controls="false"
                class="confidence-input"
                placeholder="置信度"
              />
              <el-button
                text
                type="danger"
                :disabled="tagDrafts.length === 1"
                @click="removeDraftRow(index)"
              >
                删除
              </el-button>
            </div>
            <el-button text type="primary" @click="addDraftRow">
              新增行
            </el-button>
          </div>
          <el-button
            type="primary"
            :loading="isMutating"
            @click="submitTagDrafts"
          >
            提交
          </el-button>
        </el-card>
      </el-col>

      <el-col :md="10" :xs="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>让 AI 协助</h3>
            </div>
          </template>
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
                :max="MAX_AI_TAG_LIMIT"
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
import type { TagDraftInput } from "@/types/tag";

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

const MAX_AI_TAG_LIMIT = 5;

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
      resetDrafts();
      return;
    }
    editableImageId.value = parsed;
    tagStore.initialize(parsed);
  },
  { immediate: true }
);

const createDraft = (
  overrides: Partial<TagDraftInput> = {}
): TagDraftInput => ({
  name: "",
  confidence: 0.9,
  tagType: "CUSTOM",
  ...overrides,
});

const tagDrafts = ref<TagDraftInput[]>([createDraft()]);
const aiHints = ref<string[]>([]);
const aiGenerateLimit = ref<number>(MAX_AI_TAG_LIMIT);

const clampAiLimit = (value: number | null | undefined): number => {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return MAX_AI_TAG_LIMIT;
  }
  if (value < 1) {
    return 1;
  }
  if (value > MAX_AI_TAG_LIMIT) {
    return MAX_AI_TAG_LIMIT;
  }
  return Math.trunc(value);
};

watch(aiGenerateLimit, (value) => {
  const normalized = clampAiLimit(value);
  if (normalized !== value) {
    aiGenerateLimit.value = normalized;
  }
});

const resetDrafts = () => {
  tagDrafts.value = [createDraft()];
};

const handleLoadImage = () => {
  if (!editableImageId.value) {
    ElMessage.warning("请输入有效的图片 ID");
    return;
  }
  resetDrafts();
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
  const trimmed = tagName.trim();
  if (!trimmed) return;
  const vacancy = tagDrafts.value.find((draft) => !draft.name.trim());
  if (vacancy) {
    vacancy.name = trimmed;
    vacancy.tagType = "CUSTOM";
    return;
  }
  tagDrafts.value.push(createDraft({ name: trimmed, tagType: "CUSTOM" }));
};

const addDraftRow = () => {
  tagDrafts.value.push(createDraft());
};

const removeDraftRow = (index: number) => {
  if (tagDrafts.value.length === 1) return;
  tagDrafts.value.splice(index, 1);
};

const clampConfidence = (value: number | undefined) => {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return undefined;
  }
  if (value < 0) return 0;
  if (value > 1) return 1;
  return Number(value.toFixed(2));
};

const submitTagDrafts = async () => {
  const prepared = tagDrafts.value
    .map((draft) => ({
      name: draft.name.trim(),
      tagType: draft.tagType,
      confidence: clampConfidence(draft.confidence),
    }))
    .filter((draft) => draft.name.length > 0);

  if (!prepared.length) {
    ElMessage.warning("请输入至少一个标签");
    return;
  }

  const customPayload = prepared
    .filter((draft) => draft.tagType === "CUSTOM")
    .map((draft) => ({ name: draft.name, confidence: draft.confidence }));
  const aiPayload = prepared
    .filter((draft) => draft.tagType === "AI")
    .map((draft) => ({ name: draft.name, confidence: draft.confidence }));

  if (!customPayload.length && !aiPayload.length) {
    ElMessage.warning("请选择至少一个标签类型");
    return;
  }

  try {
    if (customPayload.length) {
      await tagStore.addCustom(customPayload);
    }
    if (aiPayload.length) {
      await tagStore.addAi(aiPayload);
    }
    resetDrafts();
  } catch (error) {
    // store handles errors
  }
};

const handleGenerateAi = () => {
  const hints = aiHints.value.map((hint) => hint.trim()).filter(Boolean);
  const payload: { hints?: string[]; limit?: number } = {};
  if (hints.length) {
    payload.hints = hints;
  }
  const normalizedLimit = clampAiLimit(aiGenerateLimit.value);
  if (normalizedLimit && normalizedLimit > 0) {
    payload.limit = normalizedLimit;
    aiGenerateLimit.value = normalizedLimit;
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

.tag-draft-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tag-draft-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 140px auto;
  gap: 12px;
  align-items: center;
}

.tag-draft-row .name-select,
.tag-draft-row .type-select,
.tag-draft-row .confidence-input {
  width: 100%;
}

.ai-generate-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .tag-draft-row {
    grid-template-columns: 1fr;
  }
}
</style>
