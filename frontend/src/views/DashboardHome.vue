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
            plain
            size="large"
            :disabled="!visibleHighlights.length || highlightsLoading"
            @click="enterFullscreenCarousel"
          >
            全屏播放
          </el-button>
          <el-button
            plain
            size="large"
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
      width="880px"
      destroy-on-close
      class="manage-dialog"
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
          :disabled="!manualHighlightIds.length"
          @click="resetSelection"
        >
          恢复自动展示
        </el-button>
      </div>
      <div class="selection-grid-panel">
        <el-checkbox-group
          v-model="selectionDraft"
          class="selection-grid__group"
        >
          <el-checkbox
            v-for="image in filteredSelectionCandidates"
            :key="image.id"
            :label="image.id"
            class="selection-item"
          >
            <div class="selection-thumb">
              <img
                class="selection-thumb__image"
                :src="previewSource(image)"
                :alt="image.originalFilename || '精选图片'"
                loading="lazy"
              />
              <div class="selection-thumb__overlay">
                <span class="selection-name">{{ image.originalFilename }}</span>
                <small class="selection-meta">
                  {{ formatResolution(image) }}
                </small>
              </div>
            </div>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <span class="selection-count">
            已选 {{ selectionDraft.length }} / {{ MAX_MANUAL_SELECTION }}
          </span>
          <div class="footer-actions">
            <el-button @click="manageDialogVisible = false"> 取消 </el-button>
            <el-button type="primary" @click="applySelection">
              保存展示
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
    <teleport to="body">
      <div
        v-if="fullscreenCarouselActive && visibleHighlights.length"
        class="fullscreen-carousel"
        role="dialog"
        aria-modal="true"
      >
        <div class="fullscreen-carousel__controls">
          <span class="controls-title">精选轮播</span>
          <el-button
            type="primary"
            round
            plain
            size="large"
            @click="exitFullscreenCarousel"
          >
            退出全屏
          </el-button>
        </div>
        <div class="fullscreen-carousel__player">
          <el-carousel
            :interval="5500"
            :height="fullscreenCarouselHeight"
            :autoplay="true"
            :loop="true"
            :pause-on-hover="false"
            indicator-position="none"
            trigger="click"
            arrow="always"
          >
            <el-carousel-item
              v-for="image in visibleHighlights"
              :key="`fullscreen-${image.id}`"
            >
              <div class="fullscreen-slide" :style="backgroundStyle(image)">
                <div class="slide-overlay">
                  <span class="slide-filename">
                    {{ image.originalFilename }}
                  </span>
                  <span class="slide-meta">{{ formatResolution(image) }}</span>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>
      </div>
    </teleport>
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
const fullscreenCarouselHeight = "calc(100vh - 160px)";

const highlightPool = ref<HighlightItem[]>([]);
const highlightsLoading = ref(false);
const manualHighlightIds = ref<number[]>(loadStoredSelection());
const manageDialogVisible = ref(false);
const selectionDraft = ref<number[]>([]);
const selectionFilter = ref("");
const fullscreenCarouselActive = ref(false);

const visibleHighlights = computed(() => {
  if (!manualHighlightIds.value.length) {
    return highlightPool.value;
  }
  const map = new Map(highlightPool.value.map((item) => [item.id, item]));
  const manual = manualHighlightIds.value
    .map((id) => map.get(id))
    .filter((item): item is HighlightItem => Boolean(item));
  if (manual.length) {
    return manual;
  }
  return highlightPool.value;
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

const requestViewportFullscreen = async () => {
  if (typeof document === "undefined") {
    return;
  }
  const element = document.documentElement as HTMLElement & {
    webkitRequestFullscreen?: () => Promise<void> | void;
    msRequestFullscreen?: () => Promise<void> | void;
  };
  if (element.requestFullscreen) {
    await element.requestFullscreen();
    return;
  }
  if (element.webkitRequestFullscreen) {
    element.webkitRequestFullscreen();
    return;
  }
  if (element.msRequestFullscreen) {
    element.msRequestFullscreen();
  }
};

const exitDocumentFullscreen = async () => {
  if (typeof document === "undefined") {
    return;
  }
  const fullDoc = document as Document & {
    webkitExitFullscreen?: () => Promise<void> | void;
    msExitFullscreen?: () => Promise<void> | void;
  };
  if (fullDoc.exitFullscreen) {
    await fullDoc.exitFullscreen();
    return;
  }
  if (fullDoc.webkitExitFullscreen) {
    fullDoc.webkitExitFullscreen();
    return;
  }
  if (fullDoc.msExitFullscreen) {
    fullDoc.msExitFullscreen();
  }
};

const getFullscreenElement = () => {
  if (typeof document === "undefined") {
    return null;
  }
  const fullDoc = document as Document & {
    webkitFullscreenElement?: Element | null;
    msFullscreenElement?: Element | null;
  };
  return (
    fullDoc.fullscreenElement ??
    fullDoc.webkitFullscreenElement ??
    fullDoc.msFullscreenElement ??
    null
  );
};

const handleFullscreenChange = () => {
  if (!getFullscreenElement()) {
    fullscreenCarouselActive.value = false;
  }
};

const enterFullscreenCarousel = async () => {
  if (!visibleHighlights.value.length) {
    ElMessage.info("暂无可播放的精选图片");
    return;
  }
  try {
    if (!getFullscreenElement()) {
      await requestViewportFullscreen();
    }
    fullscreenCarouselActive.value = true;
  } catch (error) {
    fullscreenCarouselActive.value = true;
    ElMessage.warning("浏览器阻止了全屏请求，但您仍可手动退出");
  }
};

const exitFullscreenCarousel = async () => {
  fullscreenCarouselActive.value = false;
  if (getFullscreenElement()) {
    await exitDocumentFullscreen();
  }
};

onMounted(() => {
  loadHighlights();
  if (typeof document !== "undefined") {
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    document.addEventListener("webkitfullscreenchange", handleFullscreenChange);
    document.addEventListener("msfullscreenchange", handleFullscreenChange);
  }
});

const goToUpload = () => {
  router.push({ name: "image-upload" });
};

onUnmounted(() => {
  revokePreviewUrls();
  if (typeof document !== "undefined") {
    document.removeEventListener("fullscreenchange", handleFullscreenChange);
    document.removeEventListener(
      "webkitfullscreenchange",
      handleFullscreenChange
    );
    document.removeEventListener("msfullscreenchange", handleFullscreenChange);
  }
  if (fullscreenCarouselActive.value && getFullscreenElement()) {
    exitDocumentFullscreen();
  }
});

const backgroundStyle = (image: HighlightItem) => ({
  backgroundImage: `linear-gradient(145deg, rgba(8,8,8,0.25), rgba(8,8,8,0.65)), url(${
    image.previewUrl || image.filePath
  })`,
});

const previewSource = (image: HighlightItem) =>
  image.previewUrl || image.filePath || "";

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

@media (max-width: 640px) {
  .dashboard {
    padding: 12px 0 28px;
  }

  .hero-card {
    gap: 20px;
    padding: 20px;
    border-radius: 24px;
  }

  .hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .fullscreen-carousel__controls {
    padding: 14px 18px;
  }

  .fullscreen-carousel__player {
    padding: 0 14px 18px;
  }
}

.selection-tip {
  margin: 0 0 12px;
  color: #4a5568;
}

:deep(.manage-dialog) {
  width: min(980px, 94vw);
}

:deep(.manage-dialog .el-dialog__body) {
  padding: 18px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 520px;
  max-height: 90vh;
  overflow-y: auto;
}

.selection-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  width: 100%;
}

.selection-toolbar :deep(.el-input) {
  flex: 1;
  min-width: 260px;
}

.selection-toolbar :deep(.el-button) {
  align-self: stretch;
}

.selection-grid-panel {
  flex: 1;
  min-height: 380px;
  width: 100%;
  margin-top: 12px;
}

.selection-grid__group {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(auto-fill, minmax(180px, 220px));
  gap: 18px;
  justify-content: flex-start;
  margin: 0 auto;
}

.selection-item {
  width: 100%;
  margin: 0;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.selection-item :deep(.el-checkbox__input) {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
}

.selection-item :deep(.el-checkbox__inner) {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 2px solid rgba(255, 255, 255, 0.8);
  background-color: rgba(7, 12, 26, 0.6);
}

.selection-item :deep(.el-checkbox__label) {
  width: 100%;
  padding: 0;
  display: block;
}

.selection-thumb {
  position: relative;
  width: 100%;
  max-width: 220px;
  margin: 0;
  aspect-ratio: 4 / 3;
  border-radius: 12px;
  overflow: hidden;
  background: #090c17;
  box-shadow: 0 6px 20px rgba(9, 12, 23, 0.35);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}

.selection-thumb__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.selection-thumb__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 12px;
  background: linear-gradient(
    180deg,
    rgba(4, 6, 12, 0) 35%,
    rgba(4, 6, 12, 0.85)
  );
  color: #fff;
  pointer-events: none;
}

.selection-name {
  font-weight: 600;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.5);
}

.selection-meta {
  opacity: 0.85;
}

:deep(.selection-item.is-checked .selection-thumb) {
  box-shadow: 0 8px 24px rgba(12, 38, 92, 0.45),
    0 0 0 3px rgba(59, 130, 246, 0.8);
}

:deep(.selection-item.is-checked .selection-thumb__overlay) {
  background: linear-gradient(
    180deg,
    rgba(3, 16, 40, 0) 40%,
    rgba(20, 92, 255, 0.88)
  );
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

.fullscreen-carousel {
  position: fixed;
  inset: 0;
  background: radial-gradient(circle at top, rgba(6, 11, 25, 0.85), #050507);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(2px);
}

.fullscreen-carousel__controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 32px;
  color: #fff;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.controls-title {
  font-size: 0.95rem;
  font-weight: 600;
}

.fullscreen-carousel__player {
  flex: 1;
  padding: 0 32px 32px;
  display: flex;
  min-height: 0;
}

.fullscreen-carousel__player :deep(.el-carousel) {
  flex: 1;
}

.fullscreen-carousel__player :deep(.el-carousel__container) {
  height: 100%;
  min-height: 360px;
  border-radius: 28px;
  overflow: hidden;
}

.fullscreen-slide {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  position: relative;
}

.fullscreen-slide .slide-overlay {
  padding: 24px 32px;
}
</style>
