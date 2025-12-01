<template>
  <section class="image-search">
    <el-row :gutter="24">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="filter-card">
          <template #header>
            <div class="card-header">
              <h3>搜索条件</h3>
              <el-button text size="small" @click="handleResetFilters">
                重置
              </el-button>
            </div>
          </template>

          <el-form label-position="top" :model="filters" class="filter-form">
            <el-form-item label="关键词">
              <el-input
                v-model="localFilters.keyword"
                placeholder="输入文件名或描述关键字"
                clearable
              />
            </el-form-item>

            <el-form-item label="标签">
              <el-select
                v-model="localFilters.tags"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入标签并回车添加"
              />
            </el-form-item>

            <el-form-item label="拍摄设备">
              <el-input
                v-model="localFilters.cameraMake"
                placeholder="品牌，如 Canon"
                clearable
                class="inline-input"
              />
              <el-input
                v-model="localFilters.cameraModel"
                placeholder="型号，如 EOS R7"
                clearable
                class="inline-input"
              />
            </el-form-item>

            <el-form-item label="分辨率范围 (px)">
              <div class="range-inputs">
                <el-input-number
                  v-model="localFilters.minWidth"
                  :min="1"
                  placeholder="最小宽"
                />
                <span>~</span>
                <el-input-number
                  v-model="localFilters.maxWidth"
                  :min="1"
                  placeholder="最大宽"
                />
              </div>
              <div class="range-inputs">
                <el-input-number
                  v-model="localFilters.minHeight"
                  :min="1"
                  placeholder="最小高"
                />
                <span>~</span>
                <el-input-number
                  v-model="localFilters.maxHeight"
                  :min="1"
                  placeholder="最大高"
                />
              </div>
            </el-form-item>

            <el-form-item label="时间区间">
              <el-date-picker
                v-model="dateRange"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
                :editable="false"
                format="YYYY-MM-DD HH:mm"
                :shortcuts="dateShortcuts"
                unlink-panels
                range-separator="至"
              />
            </el-form-item>

            <el-form-item label="隐私">
              <el-radio-group v-model="localFilters.privacyLevel">
                <el-radio-button label="PUBLIC">公开</el-radio-button>
                <el-radio-button label="PRIVATE">私有</el-radio-button>
                <!-- use explicit ALL token and convert to undefined in request normalization -->
                <el-radio-button label="ALL">全部</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item>
              <el-checkbox v-model="localFilters.onlyOwn">
                仅查看我的图片
              </el-checkbox>
            </el-form-item>

            <el-button
              type="primary"
              :loading="loading"
              block
              @click="handleSearch"
            >
              搜索
            </el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="result-card">
          <template #header>
            <div class="card-header">
              <div>
                <h3>搜索结果</h3>
                <p class="result-info">
                  共 {{ pagination.totalElements }} 张图片
                </p>
              </div>
              <el-select
                v-model="sortValue"
                @change="handleSortChange"
                size="small"
              >
                <el-option label="最新上传" value="uploadTime|DESC" />
                <el-option label="最早上传" value="uploadTime|ASC" />
                <el-option label="文件名 A-Z" value="originalFilename|ASC" />
                <el-option label="文件名 Z-A" value="originalFilename|DESC" />
                <el-option label="文件体积从大到小" value="fileSize|DESC" />
                <el-option label="文件体积从小到大" value="fileSize|ASC" />
              </el-select>
            </div>
          </template>

          <el-skeleton v-if="loading" :rows="6" animated />

          <el-empty v-else-if="!hasResults" description="暂未找到匹配图片" />

          <div v-else class="result-grid">
            <el-row :gutter="16">
              <el-col
                v-for="image in results"
                :key="image.id"
                :xs="24"
                :md="12"
              >
                <el-card shadow="hover" class="image-card">
                  <div class="image-cover">
                    <img
                      :src="thumbnailUrl(image)"
                      :alt="image.originalFilename"
                    />
                  </div>
                  <div class="image-meta">
                    <div class="image-title" :title="image.originalFilename">
                      {{ image.originalFilename }}
                    </div>
                    <p class="image-desc">
                      {{ image.description || "暂无描述" }}
                    </p>
                    <el-space wrap>
                      <el-tag
                        v-for="tag in image.tags"
                        :key="tag"
                        type="info"
                        size="small"
                      >
                        {{ tag }}
                      </el-tag>
                    </el-space>
                    <el-descriptions
                      :column="2"
                      size="small"
                      class="meta-descriptions"
                    >
                      <el-descriptions-item label="分辨率">
                        {{ formatResolution(image) }}
                      </el-descriptions-item>
                      <el-descriptions-item label="大小">
                        {{ formatBytes(image.fileSize) }}
                      </el-descriptions-item>
                      <el-descriptions-item label="上传时间" :span="2">
                        {{ formatDate(image.uploadTime) }}
                      </el-descriptions-item>
                    </el-descriptions>
                    <div class="card-actions">
                      <el-space>
                        <el-button
                          text
                          type="danger"
                          size="small"
                          :loading="deletingId === image.id"
                          @click="handleDeleteImage(image)"
                        >
                          删除
                        </el-button>
                        <el-button
                          text
                          size="small"
                          @click="goToTagManager(image.id)"
                        >
                          管理标签
                        </el-button>
                      </el-space>
                      <el-tag
                        size="small"
                        :type="
                          image.privacyLevel === 'PUBLIC'
                            ? 'success'
                            : 'warning'
                        "
                      >
                        {{ image.privacyLevel === "PUBLIC" ? "公开" : "私有" }}
                      </el-tag>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>

          <div class="pagination" v-if="hasResults">
            <el-pagination
              background
              layout="prev, pager, next, sizes, jumper"
              :total="pagination.totalElements"
              :page-size="pagination.pageSize"
              :current-page="pagination.pageNumber + 1"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
              :page-sizes="[10, 20, 40, 60]"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { ElMessage, ElMessageBox } from "element-plus";

import { useImageSearchStore } from "@/stores/imageSearch";
import { useImageUploadStore } from "@/stores/imageUpload";
import type { ImageSearchResult, ImageSearchPayload } from "@/types/image";
import {
  downloadOriginalImage,
  downloadThumbnail,
  deleteImage,
} from "@/services/imageService";

const router = useRouter();
const store = useImageSearchStore();
const uploadStore = useImageUploadStore();
const sortValue = ref(`${store.filters.sortBy}|${store.filters.sortDirection}`);

const localFilters = reactive({ ...store.filters });
const dateRange = ref<[string, string] | null>(
  store.filters.uploadedFrom && store.filters.uploadedTo
    ? [store.filters.uploadedFrom, store.filters.uploadedTo]
    : null
);

const results = computed(() => store.results);
const pagination = computed(() => store.pagination);
const filters = computed(() => store.filters);
const loading = computed(() => store.loading);
const hasResults = computed(() => store.hasResults);
const deletingId = ref<number | null>(null);

const thumbnailSrcMap = reactive<Record<number, string>>({});
const originalSrcMap = reactive<Record<number, string>>({});
const thumbnailLoads = new Set<number>();
const originalLoads = new Set<number>();
const allocatedUrls = new Set<string>();
const transparentPixel =
  "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";

const dateShortcuts = [
  {
    text: "最近 7 天",
    value: () =>
      [dayjs().subtract(7, "day").startOf("day"), dayjs()].map((d) =>
        d.format("YYYY-MM-DDTHH:mm:ss")
      ),
  },
  {
    text: "最近 30 天",
    value: () =>
      [dayjs().subtract(30, "day").startOf("day"), dayjs()].map((d) =>
        d.format("YYYY-MM-DDTHH:mm:ss")
      ),
  },
];

const handleSearch = () => {
  const payload: Partial<ImageSearchPayload> = {
    ...localFilters,
  };
  if (dateRange.value) {
    payload.uploadedFrom = dateRange.value[0];
    payload.uploadedTo = dateRange.value[1];
  } else {
    payload.uploadedFrom = undefined;
    payload.uploadedTo = undefined;
  }
  store.searchWithFilters(payload);
};

const syncLocalFilters = (source: Partial<ImageSearchPayload>) => {
  // remove keys not present in source so localFilters doesn't keep stale values
  Object.keys(localFilters).forEach((k) => {
    if (!(k in source)) {
      // @ts-ignore
      delete localFilters[k];
    }
  });
  Object.assign(localFilters, JSON.parse(JSON.stringify(source)));
};

const handleResetFilters = () => {
  store.resetFilters();
  // ensure localFilters exactly mirrors store.filters
  syncLocalFilters(store.filters);
  dateRange.value = null;
  sortValue.value = `${store.filters.sortBy}|${store.filters.sortDirection}`;
  store.searchWithFilters();
};

const handlePageChange = (page: number) => {
  store.changePage(page - 1);
};

const handleSizeChange = (size: number) => {
  store.changePageSize(size);
};

const handleSortChange = (value: string) => {
  const [sortBy, direction] = value.split("|") as [
    NonNullable<ImageSearchPayload["sortBy"]>,
    "ASC" | "DESC"
  ];
  store.changeSort(sortBy, direction);
};

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
    return "未知";
  }
  return `${image.width} × ${image.height}`;
};

const getPreferredThumbnail = (image: ImageSearchResult) => {
  if (!image.thumbnails.length) {
    return null;
  }
  return (
    image.thumbnails.find((thumb) => thumb.sizeType === "SMALL") ??
    image.thumbnails[0]
  );
};

const rememberObjectUrl = (blob: Blob) => {
  const url = URL.createObjectURL(blob);
  allocatedUrls.add(url);
  return url;
};

const clearMediaCaches = () => {
  allocatedUrls.forEach((url) => URL.revokeObjectURL(url));
  allocatedUrls.clear();
  Object.keys(thumbnailSrcMap).forEach((key) => {
    delete thumbnailSrcMap[Number(key)];
  });
  Object.keys(originalSrcMap).forEach((key) => {
    delete originalSrcMap[Number(key)];
  });
  thumbnailLoads.clear();
  originalLoads.clear();
};

const ensureOriginal = async (image: ImageSearchResult) => {
  if (originalSrcMap[image.id] || originalLoads.has(image.id)) {
    return;
  }
  originalLoads.add(image.id);
  try {
    const blob = await downloadOriginalImage(image.id);
    originalSrcMap[image.id] = rememberObjectUrl(blob);
  } catch (error) {
    ElMessage.warning("原图加载失败，稍后重试");
  } finally {
    originalLoads.delete(image.id);
  }
};

const ensureThumbnail = async (image: ImageSearchResult) => {
  const preferred = getPreferredThumbnail(image);
  if (!preferred) {
    await ensureOriginal(image);
    return;
  }
  if (thumbnailSrcMap[preferred.id] || thumbnailLoads.has(preferred.id)) {
    return;
  }
  thumbnailLoads.add(preferred.id);
  try {
    const blob = await downloadThumbnail(image.id, preferred.id);
    thumbnailSrcMap[preferred.id] = rememberObjectUrl(blob);
  } catch (error) {
    ElMessage.warning("缩略图加载失败，尝试使用原图");
    await ensureOriginal(image);
  } finally {
    thumbnailLoads.delete(preferred.id);
  }
};

const thumbnailUrl = (image: ImageSearchResult) => {
  const preferred = getPreferredThumbnail(image);
  if (preferred) {
    if (thumbnailSrcMap[preferred.id]) {
      return thumbnailSrcMap[preferred.id];
    }
    void ensureThumbnail(image);
    return transparentPixel;
  }

  if (originalSrcMap[image.id]) {
    return originalSrcMap[image.id];
  }
  void ensureOriginal(image);
  return transparentPixel;
};

const goToTagManager = (imageId: number) => {
  router.push({ name: "image-tags", params: { imageId } });
};

const handleDeleteImage = async (image: ImageSearchResult) => {
  try {
    await ElMessageBox.confirm(
      `删除后将无法恢复，确认删除「${image.originalFilename}」吗？`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
      }
    );
  } catch {
    return;
  }

  try {
    deletingId.value = image.id;
    await deleteImage(image.id);
    ElMessage.success("删除成功");
    // 同步清理上传页中的残留记录（如果用户刚刚上传过这张图片）
    try {
      uploadStore.removeResultById(image.id);
    } catch (e) {
      // ignore
    }
    await store.refreshCurrentPage();
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "删除失败，请稍后再试"
    );
  } finally {
    deletingId.value = null;
  }
};

watch(
  () => store.filters,
  (newFilters) => {
    // ensure localFilters matches store filters exactly
    syncLocalFilters(newFilters);
    // ensure computed filters value (store) reflects newFilters
    Object.assign(filters.value, newFilters);
    sortValue.value = `${newFilters.sortBy}|${newFilters.sortDirection}`;
    if (newFilters.uploadedFrom && newFilters.uploadedTo) {
      dateRange.value = [newFilters.uploadedFrom, newFilters.uploadedTo];
    }
  },
  { deep: true }
);

watch(
  () => store.results,
  (images, previous) => {
    if (previous) {
      clearMediaCaches();
    }
    images.forEach((image) => {
      void ensureThumbnail(image);
    });
  },
  { immediate: true }
);

onMounted(() => {
  if (!store.hasResults) {
    store.fetch();
  }
});

onBeforeUnmount(() => {
  clearMediaCaches();
});
</script>

<style scoped>
.image-search {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filter-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inline-input {
  margin-bottom: 8px;
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-info {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
}

.result-grid {
  margin-top: 16px;
}

.image-card {
  margin-bottom: 16px;
}

.image-cover {
  width: 100%;
  height: 180px;
  background-color: #f5f5f5;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-meta {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.image-title {
  font-weight: 600;
  font-size: 16px;
}

.image-desc {
  color: rgba(0, 0, 0, 0.55);
  margin: 0;
}

.meta-descriptions {
  margin-top: 8px;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
