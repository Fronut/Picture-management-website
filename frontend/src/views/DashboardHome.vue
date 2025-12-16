<template>
  <section class="dashboard">
    <div class="hero-card">
      <div class="hero-copy">
        <p class="eyebrow">智能图片空间</p>
        <h1>你好，{{ user?.username }}，让灵感在画面中流动</h1>
        <p class="hero-desc">
          通过 AI 标签、EXIF
          检索与批量管理，快速找到下一张灵感图。精选轮播实时展示
          你最近上传的作品，让仪表盘更具生命力。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="goToUpload">
            立即上传图片
          </el-button>
          <el-button
            text
            :loading="highlightsLoading"
            @click="refreshHighlights"
          >
            刷新精选
          </el-button>
          <el-button
            plain
            :disabled="!highlightPool.length || highlightsLoading"
            @click="openManageDialog"
          >
            管理展示
          </el-button>
        </div>
      </div>
      <div class="hero-visual">
        <div v-if="highlightsLoading" class="hero-skeleton">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" class="skeleton-img" />
            </template>
          </el-skeleton>
        </div>
        <div v-else-if="visibleHighlights.length" class="carousel-wrapper">
          <el-carousel
            :interval="5500"
            height="280px"
            trigger="click"
            indicator-position="outside"
          >
            <el-carousel-item
              v-for="image in visibleHighlights"
              :key="image.id"
            >
              <div class="carousel-slide" :style="backgroundStyle(image)">
                <div class="slide-overlay">
                  <span class="slide-filename">{{
                    image.originalFilename
                  }}</span>
                  <span class="slide-meta">{{ formatResolution(image) }}</span>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>
        <div v-else class="hero-empty">
          <el-empty description="上传一些图片来点亮这里吧" />
        </div>
      </div>
    </div>

    <el-row :gutter="24" class="info-row">
      <el-col :lg="12" :sm="24">
        <el-card shadow="never" class="info-card">
          <template #header>
            <span>快速提示</span>
          </template>
          <ul class="tips-list">
            <li>上传时自动生成缩略图和 EXIF 信息，便于日后检索。</li>
            <li>利用标签和描述字段，为图片建立语义索引。</li>
            <li>AI 检索可理解自然语言，试试“有阳光的旅行照片”。</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover" class="session-card">
          <template #header>
            <div class="card-header">
              <span>当前会话</span>
            </div>
          </template>
          <el-descriptions :column="1">
            <el-descriptions-item label="用户名">
              {{ user?.username }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ user?.email }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              {{ user?.role }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="manageDialogVisible"
      title="管理精选展示"
      width="720px"
      destroy-on-close
    >
      <p class="selection-tip">
        选择希望在轮播中展示的图片，最多
        {{ MAX_MANUAL_SELECTION }} 张。不保存即表示恢复为自动推荐。
      </p>
      <div class="selection-toolbar">
        <el-input
          v-model="selectionFilter"
          placeholder="按文件名快速过滤"
          clearable
        />
        <el-button
          text
          @click="resetSelection"
          :disabled="!manualHighlightIds.length"
        >
          恢复自动展示
        </el-button>
      </div>
      <el-checkbox-group v-model="selectionDraft" class="selection-grid">
        <el-checkbox
          v-for="image in filteredSelectionCandidates"
          :key="image.id"
          :label="image.id"
          class="selection-item"
        >
          <div class="selection-thumb" :style="backgroundStyle(image)">
            <span class="selection-name">{{ image.originalFilename }}</span>
            <small class="selection-meta">{{ formatResolution(image) }}</small>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <div class="dialog-footer">
          <span class="selection-count">
            已选 {{ selectionDraft.length }} / {{ MAX_MANUAL_SELECTION }}
          </span>
          <div class="footer-actions">
            <el-button @click="manageDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="applySelection">
              保存展示
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { storeToRefs } from "pinia";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";

import {
  downloadOriginalImage,
  downloadThumbnail,
  fetchHighlightImages,
} from "@/services/imageService";
import { useAuthStore } from "@/stores/auth";
import type { ImageSearchResult } from "@/types/image";

const authStore = useAuthStore();
const { user } = storeToRefs(authStore);
const router = useRouter();

type HighlightItem = ImageSearchResult & { previewUrl?: string };

const HIGHLIGHT_SELECTION_KEY = "pm.dashboard.highlightSelection";
const MAX_MANUAL_SELECTION = 8;
const HIGHLIGHT_FETCH_SIZE = 16;

const highlightPool = ref<HighlightItem[]>([]);
const highlightsLoading = ref(false);
const manualHighlightIds = ref<number[]>(loadStoredSelection());
const manageDialogVisible = ref(false);
const selectionDraft = ref<number[]>([]);
const selectionFilter = ref("");

const visibleHighlights = computed(() => {
  if (!manualHighlightIds.value.length) {
    return highlightPool.value;
  }
  const map = new Map(highlightPool.value.map((item) => [item.id, item]));
  return manualHighlightIds.value
    .map((id) => map.get(id))
    .filter((item): item is HighlightItem => Boolean(item));
});

const filteredSelectionCandidates = computed(() => {
  const keyword = selectionFilter.value.trim().toLowerCase();
  if (!keyword) {
    return highlightPool.value;
  }
  return highlightPool.value.filter((item) =>
    item.originalFilename?.toLowerCase().includes(keyword)
  );
});

const revokePreviewUrls = () => {
  highlightPool.value.forEach((item) => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl);
    }
  });
};

const resolvePreviewUrl = async (image: ImageSearchResult) => {
  const preferredThumb =
    image.thumbnails?.find((t) => t.sizeType === "LARGE") ||
    image.thumbnails?.[0];
  try {
    if (preferredThumb) {
      const blob = await downloadThumbnail(image.id, preferredThumb.id);
      return URL.createObjectURL(blob);
    }
    const original = await downloadOriginalImage(image.id);
    return URL.createObjectURL(original);
  } catch (error) {
    console.error("Failed to fetch highlight image preview", error);
    return undefined;
  }
};

const loadHighlights = async () => {
  highlightsLoading.value = true;
  try {
    const raw = await fetchHighlightImages(HIGHLIGHT_FETCH_SIZE);
    revokePreviewUrls();
    highlightPool.value = await Promise.all(
      raw.map(async (item) => ({
        ...item,
        previewUrl: await resolvePreviewUrl(item),
      }))
    );
    syncManualSelection();
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "获取精选图片失败，请稍后再试";
    ElMessage.error(message);
  } finally {
    highlightsLoading.value = false;
  }
};

onMounted(loadHighlights);

const goToUpload = () => {
  router.push({ name: "image-upload" });
};

const refreshHighlights = () => {
  if (!highlightsLoading.value) {
    loadHighlights();
  }
};

onUnmounted(() => {
  revokePreviewUrls();
});

const backgroundStyle = (image: HighlightItem) => ({
  backgroundImage: `linear-gradient(145deg, rgba(8,8,8,0.25), rgba(8,8,8,0.65)), url(${
    image.previewUrl || image.filePath
  })`,
});

const formatResolution = (image: ImageSearchResult) => {
  if (image.width && image.height) {
    return `${image.width} × ${image.height}`;
  }
  return image.mimeType || "未知尺寸";
};

function loadStoredSelection(): number[] {
  if (typeof window === "undefined") {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(HIGHLIGHT_SELECTION_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed.filter((id) => Number.isInteger(id));
    }
    return [];
  } catch (error) {
    console.warn("Failed to load highlight selection", error);
    return [];
  }
}

const persistManualSelection = () => {
  if (typeof window === "undefined") {
    return;
  }
  if (manualHighlightIds.value.length) {
    window.localStorage.setItem(
      HIGHLIGHT_SELECTION_KEY,
      JSON.stringify(manualHighlightIds.value)
    );
  } else {
    window.localStorage.removeItem(HIGHLIGHT_SELECTION_KEY);
  }
};

const openManageDialog = () => {
  selectionDraft.value = manualHighlightIds.value.length
    ? [...manualHighlightIds.value]
    : highlightPool.value.map((item) => item.id);
  selectionFilter.value = "";
  manageDialogVisible.value = true;
};

const applySelection = () => {
  if (selectionDraft.value.length > MAX_MANUAL_SELECTION) {
    ElMessage.warning(`最多只能选择 ${MAX_MANUAL_SELECTION} 张图片`);
    return;
  }

  if (!selectionDraft.value.length) {
    manualHighlightIds.value = [];
  } else {
    manualHighlightIds.value = Array.from(new Set(selectionDraft.value));
  }
  persistManualSelection();
  manageDialogVisible.value = false;
};

const resetSelection = () => {
  manualHighlightIds.value = [];
  selectionDraft.value = [];
  persistManualSelection();
};

const syncManualSelection = () => {
  if (!manualHighlightIds.value.length) {
    return;
  }
  const availableIds = new Set(highlightPool.value.map((item) => item.id));
  const filtered = manualHighlightIds.value.filter((id) =>
    availableIds.has(id)
  );
  if (filtered.length !== manualHighlightIds.value.length) {
    manualHighlightIds.value = filtered;
    persistManualSelection();
  }
};

watch(highlightPool, syncManualSelection);
</script>

<style scoped>
.dashboard {
  max-width: 1180px;
  margin: 0 auto;
  padding: 16px 0 40px;
}

.hero-card {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 32px;
  padding: 32px;
  border-radius: 32px;
  background: radial-gradient(
    circle at 10% 20%,
    #1f5ef5 0%,
    #132347 55%,
    #0a0f1c 100%
  );
  color: #f7f9ff;
  margin-bottom: 32px;
  box-shadow: 0 20px 45px rgba(10, 38, 92, 0.3);
}

.hero-copy h1 {
  font-size: 2rem;
  margin-bottom: 12px;
  line-height: 1.3;
}

.hero-desc {
  color: rgba(247, 249, 255, 0.85);
  line-height: 1.6;
  margin-bottom: 20px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.eyebrow {
  letter-spacing: 0.2em;
  text-transform: uppercase;
  margin-bottom: 8px;
  font-size: 0.85rem;
  color: rgba(247, 249, 255, 0.75);
}

.hero-visual {
  min-height: 300px;
}

.hero-skeleton,
.hero-empty {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 20px;
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.skeleton-img {
  width: 100%;
  height: 280px;
  border-radius: 20px;
}

.carousel-wrapper :deep(.el-carousel__container) {
  border-radius: 20px;
}

.carousel-slide {
  width: 100%;
  height: 280px;
  background-size: cover;
  background-position: center;
  border-radius: 20px;
  position: relative;
  overflow: hidden;
}

.slide-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 20px;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.75));
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.slide-filename {
  font-weight: 600;
}

.slide-meta {
  font-size: 0.9rem;
  opacity: 0.85;
}

.info-row {
  margin-top: 16px;
}

.info-card,
.session-card {
  border-radius: 18px;
}

.tips-list {
  margin: 0;
  padding-left: 18px;
  color: #4a5568;
  line-height: 1.6;
}

.card-header {
  font-weight: 600;
}

@media (max-width: 768px) {
  .hero-card {
    padding: 24px;
  }

  .hero-copy h1 {
    font-size: 1.6rem;
  }
}

.selection-tip {
  margin: 0 0 12px;
  color: #4a5568;
}

.selection-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.selection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px;
}

.selection-item {
  width: 100%;
}

.selection-item :deep(.el-checkbox__label) {
  width: 100%;
  padding: 0;
}

.selection-thumb {
  position: relative;
  height: 120px;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background-size: cover;
  background-position: center;
  padding: 12px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  backdrop-filter: blur(3px);
}

.selection-name {
  font-weight: 600;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.5);
}

.selection-meta {
  opacity: 0.85;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.selection-count {
  color: #4a5568;
}

.footer-actions {
  display: flex;
  gap: 12px;
}
</style>
