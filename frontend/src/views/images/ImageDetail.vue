<template>
  <section class="image-detail">
    <el-page-header
      class="detail-header"
      content="图片详情"
      @back="handleBack"
    />

    <el-skeleton v-if="loading" animated :rows="8" class="detail-skeleton" />

    <el-result
      v-else-if="errorMessage"
      icon="warning"
      title="加载失败"
      :sub-title="errorMessage"
      class="detail-result"
    >
      <template #extra>
        <el-button type="primary" @click="fetchDetail">重新加载</el-button>
      </template>
    </el-result>

    <template v-else-if="detail && summary">
      <div class="hero-section">
        <div class="hero-copy">
          <p class="owner-chip">由 {{ detail.owner.username }} 上传</p>
          <h1>{{ summary.originalFilename }}</h1>
          <p class="hero-description">
            {{ summary.description || "这张图片还没有描述" }}
          </p>
          <p class="meta-line">
            {{ formatResolution(summary) }} ·
            {{ formatBytes(summary.fileSize) }} ·
            {{ formatDate(summary.uploadTime) }}
          </p>
          <div class="hero-actions">
            <el-button
              type="primary"
              :disabled="!detail.access.canDownloadOriginal"
              :loading="downloading"
              @click="handleDownloadOriginal"
            >
              下载原图
            </el-button>
            <el-button v-if="detail.access.canEdit" @click="openEditDialog">
              编辑图片
            </el-button>
            <el-button
              v-if="detail.access.canManageTags"
              text
              @click="openTagManager"
            >
              管理标签
            </el-button>
          </div>
        </div>
        <div class="hero-preview" :class="{ loading: previewLoading }">
          <img
            v-if="previewUrl"
            :src="previewUrl"
            :alt="summary.originalFilename"
          />
          <div v-else class="preview-placeholder">
            <span>正在生成预览...</span>
          </div>
        </div>
      </div>

      <el-row :gutter="24" class="info-grid">
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>文件 & 权限</template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="隐私">
                <el-tag
                  :type="
                    summary.privacyLevel === 'PUBLIC' ? 'success' : 'warning'
                  "
                >
                  {{ summary.privacyLevel === "PUBLIC" ? "公开" : "私有" }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="标签">
                <el-space wrap>
                  <el-tag
                    v-for="tag in summary.tags"
                    :key="tag"
                    size="small"
                    type="info"
                  >
                    {{ tag }}
                  </el-tag>
                  <span v-if="!summary.tags.length">暂无标签</span>
                </el-space>
              </el-descriptions-item>
              <el-descriptions-item label="可编辑">
                {{ detail.access.canEdit ? "是" : "否" }}
              </el-descriptions-item>
              <el-descriptions-item label="可下载原图">
                {{ detail.access.canDownloadOriginal ? "是" : "否" }}
              </el-descriptions-item>
              <el-descriptions-item label="存储路径">
                {{ summary.filePath }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>EXIF / 拍摄信息</template>
            <el-descriptions v-if="detail.exif" :column="1" border>
              <el-descriptions-item label="设备">
                {{ detail.exif?.cameraMake || "-" }}
                {{
                  detail.exif?.cameraModel
                    ? ` · ${detail.exif?.cameraModel}`
                    : ""
                }}
              </el-descriptions-item>
              <el-descriptions-item label="曝光">
                {{ detail.exif?.exposureTime || "-" }}
              </el-descriptions-item>
              <el-descriptions-item label="光圈">
                {{ detail.exif?.fNumber || "-" }}
              </el-descriptions-item>
              <el-descriptions-item label="ISO">
                {{ detail.exif?.isoSpeed ?? "-" }}
              </el-descriptions-item>
              <el-descriptions-item label="焦距">
                {{ detail.exif?.focalLength || "-" }}
              </el-descriptions-item>
              <el-descriptions-item label="拍摄时间">
                {{
                  detail.exif?.takenTime
                    ? formatDate(detail.exif.takenTime)
                    : "-"
                }}
              </el-descriptions-item>
              <el-descriptions-item label="位置">
                <template
                  v-if="detail.exif?.locationName || detail.exif?.latitude"
                >
                  {{ detail.exif?.locationName || "未知地点" }}
                  <span v-if="detail.exif?.latitude">
                    · {{ detail.exif?.latitude }}, {{ detail.exif?.longitude }}
                  </span>
                </template>
                <span v-else>无位置信息</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无 EXIF 信息" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="24" class="info-grid">
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>标签详情</span>
                <el-button
                  v-if="detail.access.canManageTags"
                  text
                  size="small"
                  @click="openTagManager"
                >
                  管理标签
                </el-button>
              </div>
            </template>
            <div v-if="detail.tagDetails.length" class="tag-grid">
              <div
                v-for="tag in detail.tagDetails"
                :key="tag.tagId"
                class="tag-chip"
              >
                <el-tag :type="tagTypeColor(tag.tagType)">
                  {{ tag.tagName }}
                </el-tag>
                <small>
                  {{ displayConfidence(tag.confidence) }} ·
                  {{
                    tag.tagType === "AI"
                      ? "AI"
                      : tag.tagType === "AUTO"
                      ? "系统"
                      : "自定义"
                  }}
                </small>
              </div>
            </div>
            <el-empty v-else description="暂无标签详情" />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>缩略图</template>
            <div v-if="summary.thumbnails.length" class="thumb-list">
              <div
                v-for="thumb in summary.thumbnails"
                :key="thumb.id"
                class="thumb-item"
              >
                <p>
                  {{ thumb.sizeType }} · {{ thumb.width }} × {{ thumb.height }}
                </p>
                <el-button text @click="downloadThumbnailAsset(thumb.id)">
                  下载
                </el-button>
              </div>
            </div>
            <el-empty v-else description="尚未生成缩略图" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <ImageEditDialog
      v-model="editDialogVisible"
      :image="detail?.summary ?? null"
      @edited="handleEditApplied"
    />

    <ImageTagManagerPanel
      v-model="tagManagerVisible"
      :image-id="summary?.id ?? null"
      @updated="handleTagsUpdated"
    />
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import ImageEditDialog from "@/components/ImageEditDialog.vue";
import ImageTagManagerPanel from "@/components/ImageTagManagerPanel.vue";
import {
  downloadOriginalImage,
  downloadThumbnail,
  fetchImageDetail,
} from "@/services/imageService";
import type { ImageDetail, ImageSearchResult } from "@/types/image";
import type { ImageSummaryThumbnail } from "@/types/image";
import type { ImageTag } from "@/types/tag";

const route = useRoute();
const router = useRouter();

const detail = ref<ImageDetail | null>(null);
const loading = ref(true);
const errorMessage = ref("");
const previewUrl = ref<string | null>(null);
const previewLoading = ref(false);
const downloading = ref(false);
const editDialogVisible = ref(false);
const tagManagerVisible = ref(false);

const summary = computed(() => detail.value?.summary ?? null);

const formatDate = (value: string) => {
  return new Date(value).toLocaleString();
};

const formatBytes = (size: number) => {
  if (!size && size !== 0) return "-";
  const units = ["B", "KB", "MB", "GB"];
  const idx = Math.min(
    units.length - 1,
    Math.floor(Math.log(size) / Math.log(1024))
  );
  const value = size / 1024 ** idx;
  return `${value.toFixed(2)} ${units[idx]}`;
};

const formatResolution = (image: ImageSearchResult) => {
  if (!image.width || !image.height) {
    return "未知分辨率";
  }
  return `${image.width} × ${image.height}`;
};

const tagTypeColor = (type: string) => {
  switch (type) {
    case "AI":
      return "success";
    case "AUTO":
      return "info";
    default:
      return "warning";
  }
};

const displayConfidence = (confidence: string | number | null) => {
  if (confidence === null || confidence === undefined || confidence === "") {
    return "置信度 --";
  }
  const value =
    typeof confidence === "number" ? confidence : Number(confidence);
  if (Number.isNaN(value)) {
    return "置信度 --";
  }
  return `置信度 ${(value * 100).toFixed(0)}%`;
};

const rememberObjectUrl = (blob: Blob) => {
  const url = URL.createObjectURL(blob);
  previewUrl.value = url;
};

const revokePreviewUrl = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = null;
  }
};

const ensurePreview = async (image: ImageSearchResult) => {
  previewLoading.value = true;
  revokePreviewUrl();
  try {
    const preferred =
      image.thumbnails.find((t) => t.sizeType === "LARGE") ||
      image.thumbnails.find((t) => t.sizeType === "MEDIUM") ||
      image.thumbnails[0];
    if (preferred) {
      const blob = await downloadThumbnail(image.id, preferred.id);
      rememberObjectUrl(blob);
      return;
    }
    const original = await downloadOriginalImage(image.id);
    rememberObjectUrl(original);
  } catch (error) {
    console.warn("Failed to load preview", error);
  } finally {
    previewLoading.value = false;
  }
};

const parseImageId = () => {
  const raw = Number(route.params.imageId);
  return Number.isFinite(raw) && raw > 0 ? raw : null;
};

const fetchDetail = async () => {
  const id = parseImageId();
  if (!id) {
    loading.value = false;
    errorMessage.value = "无效的图片 ID";
    detail.value = null;
    revokePreviewUrl();
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  detail.value = null;
  revokePreviewUrl();
  try {
    const response = await fetchImageDetail(id);
    detail.value = response;
    await ensurePreview(response.summary);
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "加载图片详情失败";
    detail.value = null;
  } finally {
    loading.value = false;
  }
};

const handleDownloadOriginal = async () => {
  if (!detail.value?.summary || !detail.value.access.canDownloadOriginal) {
    return;
  }
  try {
    downloading.value = true;
    const blob = await downloadOriginalImage(detail.value.summary.id);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = detail.value.summary.originalFilename;
    a.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "下载失败，请稍后再试"
    );
  } finally {
    downloading.value = false;
  }
};

const downloadThumbnailAsset = async (
  thumbnailId: ImageSummaryThumbnail["id"]
) => {
  if (!detail.value?.summary) return;
  try {
    const blob = await downloadThumbnail(detail.value.summary.id, thumbnailId);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${detail.value.summary.originalFilename}-thumb-${thumbnailId}`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error("下载缩略图失败");
  }
};

const openEditDialog = () => {
  if (!detail.value) return;
  editDialogVisible.value = true;
};

const handleEditApplied = (updated: ImageSearchResult) => {
  if (!detail.value) return;
  detail.value = {
    ...detail.value,
    summary: updated,
  };
  ElMessage.success("已更新图片信息");
  void ensurePreview(updated);
};

const openTagManager = () => {
  if (!detail.value?.summary || !detail.value.access.canManageTags) {
    return;
  }
  tagManagerVisible.value = true;
};

const handleTagsUpdated = (updatedTags: ImageTag[]) => {
  if (!detail.value) return;
  detail.value = {
    ...detail.value,
    tagDetails: updatedTags,
    summary: {
      ...detail.value.summary,
      tags: updatedTags.map((tag) => tag.tagName),
    },
  };
};

const handleBack = () => {
  router.back();
};

watch(
  () => route.params.imageId,
  () => {
    tagManagerVisible.value = false;
    void fetchDetail();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  revokePreviewUrl();
});
</script>

<style scoped>
.image-detail {
  max-width: 1180px;
  margin: 0 auto;
  padding: 16px 0 40px;
  width: 100%;
}

.detail-header {
  margin-bottom: 16px;
}

.detail-skeleton,
.detail-result {
  margin-top: 24px;
}

.hero-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 24px;
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(135deg, #101522, #1f2c44 60%, #0a0f1c);
  color: #f9fbff;
  margin-bottom: 24px;
}

.hero-copy h1 {
  margin: 8px 0 8px;
  font-size: clamp(1.6rem, 4vw, 2.2rem);
}

.hero-description {
  margin: 0 0 12px;
  color: rgba(249, 251, 255, 0.85);
}

.meta-line {
  margin-bottom: 16px;
  color: rgba(249, 251, 255, 0.65);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.owner-chip {
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-size: 0.85rem;
  color: rgba(249, 251, 255, 0.7);
}

.hero-preview {
  border-radius: 18px;
  background: rgba(0, 0, 0, 0.25);
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-placeholder {
  color: rgba(249, 251, 255, 0.8);
  font-size: 0.95rem;
}

.info-grid {
  margin-bottom: 24px;
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.tag-chip {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-chip small {
  color: rgba(0, 0, 0, 0.45);
}

.thumb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.thumb-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.thumb-item:last-child {
  border-bottom: none;
}

@media (max-width: 768px) {
  .hero-section {
    padding: 20px;
  }
}
</style>
