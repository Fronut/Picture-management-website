<template>
  <el-dialog
    class="image-edit-dialog"
    :model-value="modelValue"
    width="min(520px, 92vw)"
    :before-close="handleDialogClose"
    append-to-body
    title="图片编辑"
  >
    <div v-if="!image" class="empty-state">请选择一张图片进行编辑。</div>
    <div v-else>
      <el-alert
        type="info"
        :closable="false"
        class="info-alert"
        title="裁剪与色调调整会覆盖原图并重新生成缩略图，操作不可撤销，请谨慎使用"
      />

      <el-form label-position="top" class="edit-form">
        <section class="form-section">
          <div class="section-header">
            <div>
              <h4>裁剪</h4>
              <small v-if="canCrop"
                >当前尺寸：{{ image.width }} × {{ image.height }}</small
              >
              <small v-else>当前图片缺少分辨率信息，无法裁剪</small>
            </div>
            <el-switch v-model="form.cropEnabled" :disabled="!canCrop" />
          </div>
          <div v-if="form.cropEnabled && canCrop" class="crop-grid">
            <el-form-item label="起始 X">
              <el-input-number
                v-model="form.crop.x"
                :min="0"
                :max="maxCropStartX"
                :step="1"
              />
            </el-form-item>
            <el-form-item label="起始 Y">
              <el-input-number
                v-model="form.crop.y"
                :min="0"
                :max="maxCropStartY"
                :step="1"
              />
            </el-form-item>
            <el-form-item label="裁剪宽度">
              <el-input-number
                v-model="form.crop.width"
                :min="1"
                :max="maxCropWidth"
                :step="1"
              />
            </el-form-item>
            <el-form-item label="裁剪高度">
              <el-input-number
                v-model="form.crop.height"
                :min="1"
                :max="maxCropHeight"
                :step="1"
              />
            </el-form-item>
          </div>
        </section>

        <el-divider />

        <section class="form-section">
          <div class="section-header">
            <div>
              <h4>色调调节</h4>
              <small>范围 -1 ~ 1，0 表示不调整</small>
            </div>
            <el-switch v-model="form.toneEnabled" />
          </div>
          <div class="tone-controls" :class="{ disabled: !form.toneEnabled }">
            <el-form-item label="亮度">
              <el-slider
                v-model="form.tone.brightness"
                :min="-1"
                :max="1"
                :step="0.1"
                :disabled="!form.toneEnabled"
                show-input
              />
            </el-form-item>
            <el-form-item label="对比度">
              <el-slider
                v-model="form.tone.contrast"
                :min="-0.9"
                :max="1"
                :step="0.1"
                :disabled="!form.toneEnabled"
                show-input
              />
            </el-form-item>
            <el-form-item label="冷暖色调">
              <el-slider
                v-model="form.tone.warmth"
                :min="-1"
                :max="1"
                :step="0.1"
                :disabled="!form.toneEnabled"
                show-input
              />
            </el-form-item>
          </div>
        </section>
      </el-form>
    </div>

    <template #footer>
      <el-space>
        <el-button :disabled="submitting" @click="handleReset">
          重置
        </el-button>
        <el-button :disabled="submitting" @click="handleDialogClose">
          取消
        </el-button>
        <el-button
          type="primary"
          :disabled="!canSubmit || submitting"
          :loading="submitting"
          @click="handleSubmit"
        >
          应用编辑
        </el-button>
      </el-space>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";

import { editImage } from "@/services/imageService";
import type { ImageEditPayload, ImageSearchResult } from "@/types/image";

interface Props {
  modelValue: boolean;
  image: ImageSearchResult | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "edited", image: ImageSearchResult): void;
}>();

const submitting = ref(false);

const form = reactive({
  cropEnabled: false,
  toneEnabled: false,
  crop: {
    x: 0,
    y: 0,
    width: 0,
    height: 0,
  },
  tone: {
    brightness: 0,
    contrast: 0,
    warmth: 0,
  },
});

const canCrop = computed(() => !!props.image?.width && !!props.image?.height);

const hasCropSelection = computed(
  () =>
    form.cropEnabled &&
    canCrop.value &&
    form.crop.width > 0 &&
    form.crop.height > 0
);

const hasToneSelection = computed(() => {
  if (!form.toneEnabled) {
    return false;
  }
  return (
    form.tone.brightness !== 0 ||
    form.tone.contrast !== 0 ||
    form.tone.warmth !== 0
  );
});

const canSubmit = computed(
  () => hasCropSelection.value || hasToneSelection.value
);

const maxCropStartX = computed(() => {
  if (!props.image?.width) {
    return 0;
  }
  return Math.max(0, props.image.width - 1);
});

const maxCropStartY = computed(() => {
  if (!props.image?.height) {
    return 0;
  }
  return Math.max(0, props.image.height - 1);
});

const maxCropWidth = computed(() => {
  if (!props.image?.width) {
    return 0;
  }
  return Math.max(1, props.image.width - form.crop.x);
});

const maxCropHeight = computed(() => {
  if (!props.image?.height) {
    return 0;
  }
  return Math.max(1, props.image.height - form.crop.y);
});

const resetForm = () => {
  form.cropEnabled = false;
  form.toneEnabled = false;
  form.tone.brightness = 0;
  form.tone.contrast = 0;
  form.tone.warmth = 0;
  form.crop.x = 0;
  form.crop.y = 0;
  const width = props.image?.width ?? 0;
  const height = props.image?.height ?? 0;
  form.crop.width = width > 0 ? width : 0;
  form.crop.height = height > 0 ? height : 0;
};

watch(
  () => props.image,
  () => {
    resetForm();
  },
  { immediate: true }
);

watch(
  () => form.crop.x,
  (value) => {
    if (!props.image?.width) {
      return;
    }
    const maxStart = Math.max(0, props.image.width - 1);
    if (value > maxStart) {
      form.crop.x = maxStart;
    }
    if (form.crop.width > maxCropWidth.value) {
      form.crop.width = maxCropWidth.value;
    }
  }
);

watch(
  () => form.crop.y,
  (value) => {
    if (!props.image?.height) {
      return;
    }
    const maxStart = Math.max(0, props.image.height - 1);
    if (value > maxStart) {
      form.crop.y = maxStart;
    }
    if (form.crop.height > maxCropHeight.value) {
      form.crop.height = maxCropHeight.value;
    }
  }
);

const handleReset = () => {
  resetForm();
};

const closeDialog = () => {
  emit("update:modelValue", false);
};

const handleDialogClose = () => {
  if (submitting.value) {
    return;
  }
  resetForm();
  closeDialog();
};

const buildTonePayload = () => {
  if (!hasToneSelection.value) {
    return undefined;
  }
  const tonePayload: Record<string, number> = {};
  const assignIfNeeded = (key: keyof typeof form.tone) => {
    const value = form.tone[key];
    if (value !== 0) {
      tonePayload[key] = Number(value.toFixed(2));
    }
  };
  assignIfNeeded("brightness");
  assignIfNeeded("contrast");
  assignIfNeeded("warmth");
  return Object.keys(tonePayload).length ? tonePayload : undefined;
};

const handleSubmit = async () => {
  if (!props.image) {
    return;
  }
  if (!canSubmit.value) {
    ElMessage.warning("请至少启用裁剪或色调调整");
    return;
  }
  submitting.value = true;
  try {
    const payload: ImageEditPayload = {
      imageId: props.image.id,
    };
    if (hasCropSelection.value) {
      payload.crop = {
        x: form.crop.x,
        y: form.crop.y,
        width: form.crop.width,
        height: form.crop.height,
      };
    }
    const tonePayload = buildTonePayload();
    if (tonePayload) {
      payload.toneAdjustment = tonePayload;
    }
    const updated = await editImage(payload);
    ElMessage.success("编辑已应用");
    emit("edited", updated);
    closeDialog();
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "编辑失败，请稍后再试"
    );
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.empty-state {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.5);
}

.info-alert {
  margin-bottom: 16px;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-header h4 {
  margin: 0;
}

.section-header small {
  display: block;
  color: rgba(0, 0, 0, 0.45);
}

.crop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.tone-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tone-controls.disabled {
  opacity: 0.6;
}

.image-edit-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}

@media (max-width: 640px) {
  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .crop-grid {
    grid-template-columns: 1fr;
  }

  .tone-controls {
    gap: 4px;
  }
}
</style>
