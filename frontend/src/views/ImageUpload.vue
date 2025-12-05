<template>
  <section class="upload-page">
    <el-row :gutter="24">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="upload-card">
          <template #header>
            <div class="card-header">
              <div>
                <h2>图片上传</h2>
                <p>选择图片、设置隐私与描述，点击上传即可。</p>
              </div>
              <el-tag type="info"> 最大 20MB/张 (后端限制) </el-tag>
            </div>
          </template>

          <el-upload
            ref="uploadRef"
            drag
            action=""
            multiple
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            accept="image/*"
            class="upload-dropzone"
          >
            <el-icon class="el-icon--upload">
              <UploadFilled />
            </el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，或 <em>点击选择</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 png/jpg/gif/webp，多选会一次性上传
              </div>
            </template>
          </el-upload>

          <el-form label-width="80px" class="upload-form">
            <el-form-item label="隐私">
              <el-radio-group v-model="privacyLevel">
                <el-radio-button label="PRIVATE"> 私有 </el-radio-button>
                <el-radio-button label="PUBLIC"> 公开 </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="description"
                type="textarea"
                :rows="3"
                placeholder="为本次上传添加描述（可选）"
              />
            </el-form-item>
          </el-form>

          <div class="candidate-section">
            <div class="section-header">
              <h3>待上传文件</h3>
              <el-space>
                <el-button
                  v-if="hasFiles"
                  text
                  type="danger"
                  @click="handleClearAll"
                >
                  清空
                </el-button>
              </el-space>
            </div>

            <el-empty v-if="!candidates.length" description="暂无文件" />

            <div v-else class="candidate-table">
              <el-table :data="candidates" size="small">
                <el-table-column label="文件名" min-width="200">
                  <template #default="{ row }">
                    <div class="file-name">
                      {{ row.file.name }}
                    </div>
                    <small class="file-meta">
                      {{ formatBytes(row.file.size) }} ·
                      {{ row.file.type || "未知类型" }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="140">
                  <template #default="{ row }">
                    <el-tag :type="statusType(row.status)">
                      {{ statusLabel(row.status) }}
                    </el-tag>
                    <div v-if="row.errorMessage" class="error-text">
                      {{ row.errorMessage }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button
                      text
                      type="danger"
                      size="small"
                      :disabled="isUploading"
                      @click="handleRemove(row.id)"
                    >
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <div class="actions">
            <el-button
              type="primary"
              :icon="UploadFilled"
              :loading="isUploading"
              :disabled="!hasReadyFiles"
              @click="handleUpload"
            >
              开始上传
            </el-button>
            <el-button
              :disabled="!hasFiles || isUploading"
              @click="handleClearAll"
            >
              清空列表
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="result-card">
          <template #header>
            <div class="card-header">
              <h3>上传结果</h3>
            </div>
          </template>

          <el-empty v-if="!summary" description="上传完成后显示结果" />

          <template v-else>
            <el-statistic title="总数" :value="summary.total" />
            <el-divider />
            <el-statistic title="成功" :value="summary.success" />
            <el-divider />
            <el-statistic title="失败" :value="summary.failed" />

            <el-timeline class="result-timeline">
              <el-timeline-item
                v-for="image in results"
                :key="image.id"
                type="success"
                :timestamp="formatTimestamp(image.uploadTime)"
              >
                <div class="timeline-item">
                  <p class="file-name">
                    {{ image.originalFilename }}
                  </p>
                  <small class="file-meta">
                    {{ formatBytes(image.fileSize) }} · {{ image.mimeType }}
                  </small>
                  <el-button
                    text
                    size="small"
                    @click="goToTagManager(image.id)"
                  >
                    管理标签
                  </el-button>
                </div>
              </el-timeline-item>
            </el-timeline>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import type {
  UploadFile,
  UploadFiles,
  UploadInstance,
  UploadProps,
  UploadRawFile,
} from "element-plus";
import { ElMessage } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";

import { useImageUploadStore } from "@/stores/imageUpload";

const router = useRouter();
const uploadRef = ref<UploadInstance>();
const uploadStore = useImageUploadStore();

const { candidates, isUploading, summary, results, privacyLevel, description } =
  storeToRefs(uploadStore);

const hasReadyFiles = computed(() => uploadStore.readyFiles.length > 0);
const hasFiles = computed(() => uploadStore.hasFiles);

const handleFileChange: UploadProps["onChange"] = (
  _file: UploadFile,
  fileList: UploadFiles
) => {
  const rawFiles = fileList
    .map((item) => item.raw)
    .filter((file): file is UploadRawFile => Boolean(file));
  if (!rawFiles.length) {
    return;
  }
  uploadStore.addFiles(rawFiles);
  uploadRef.value?.clearFiles();
};

const handleExceed: UploadProps["onExceed"] = () => {
  ElMessage.info("已添加到待上传列表");
};

const handleRemove = (id: string) => {
  uploadStore.removeCandidate(id);
};

const handleClearAll = () => {
  uploadStore.clearAll();
  uploadRef.value?.clearFiles();
};

const handleUpload = () => {
  uploadStore.upload();
};

const goToTagManager = (imageId: number) => {
  router.push({ name: "image-tags", params: { imageId } });
};

const formatBytes = (size: number) => {
  if (!size) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const unitIndex = Math.min(
    units.length - 1,
    Math.floor(Math.log(size) / Math.log(1024))
  );
  const value = size / 1024 ** unitIndex;
  return `${value.toFixed(2)} ${units[unitIndex]}`;
};

const statusLabel = (status: string) => {
  switch (status) {
    case "uploading":
      return "上传中";
    case "success":
      return "已完成";
    case "error":
      return "失败";
    default:
      return "待上传";
  }
};

const statusType = (status: string) => {
  switch (status) {
    case "success":
      return "success";
    case "error":
      return "danger";
    case "uploading":
      return "warning";
    default:
      return "info";
  }
};

const formatTimestamp = (value: string) => {
  const date = new Date(value);
  return date.toLocaleString();
};
</script>

<style scoped>
.upload-page {
  max-width: 1200px;
  margin: 0 auto;
  padding-bottom: 32px;
  width: 100%;
}

.upload-card,
.result-card {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-dropzone {
  margin-bottom: 24px;
}

.upload-form {
  margin-bottom: 24px;
}

.candidate-section {
  margin-bottom: 24px;
}

.candidate-table {
  width: 100%;
  overflow-x: auto;
}

.candidate-table :deep(.el-table) {
  min-width: 560px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.file-name {
  font-weight: 600;
}

.file-meta {
  color: rgba(0, 0, 0, 0.45);
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.actions .el-button {
  flex: 1 1 220px;
}

.result-timeline {
  margin-top: 16px;
}

.timeline-item {
  display: flex;
  flex-direction: column;
}

@media (max-width: 900px) {
  .upload-card,
  .result-card {
    margin-bottom: 16px;
  }

  .actions .el-button {
    flex-basis: 100%;
  }
}
</style>
