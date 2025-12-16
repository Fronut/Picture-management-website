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

      <section class="preview-section">
        <div class="section-header">
          <div>
            <h4>实时预览</h4>
            <small v-if="previewSize.width && previewSize.height">
              输出尺寸：{{ previewSize.width }} × {{ previewSize.height }}
            </small>
            <small v-else>加载原图后可查看输出尺寸</small>
          </div>
        </div>
        <div class="preview-stage">
          <canvas
            v-show="previewReady"
            ref="previewCanvas"
            class="preview-canvas"
          ></canvas>
          <div v-if="previewLoading" class="preview-placeholder">
            原图加载中...
          </div>
          <div v-else-if="previewError" class="preview-placeholder error">
            {{ previewError }}
          </div>
          <div v-else-if="!previewReady" class="preview-placeholder">
            选择图片后自动加载原图
          </div>
        </div>
      </section>

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
              <h4>旋转</h4>
              <small>范围 -180° ~ 180°</small>
            </div>
            <el-switch v-model="form.rotationEnabled" />
          </div>
          <div
            class="rotation-controls"
            :class="{ disabled: !form.rotationEnabled }"
          >
            <el-form-item label="角度">
              <el-slider
                v-model="form.rotation.degrees"
                :min="-180"
                :max="180"
                :step="1"
                :disabled="!form.rotationEnabled"
                show-input
              />
            </el-form-item>
            <div class="rotation-shortcuts">
              <el-button-group>
                <el-button
                  size="small"
                  :disabled="!form.rotationEnabled"
                  @click="applyRotationDelta(-90)"
                >
                  -90°
                </el-button>
                <el-button
                  size="small"
                  :disabled="!form.rotationEnabled"
                  @click="applyRotationDelta(-45)"
                >
                  -45°
                </el-button>
                <el-button
                  size="small"
                  :disabled="!form.rotationEnabled"
                  @click="applyRotationDelta(45)"
                >
                  +45°
                </el-button>
                <el-button
                  size="small"
                  :disabled="!form.rotationEnabled"
                  @click="applyRotationDelta(90)"
                >
                  +90°
                </el-button>
                <el-button
                  size="small"
                  :disabled="!form.rotationEnabled"
                  @click="resetRotation"
                >
                  重置
                </el-button>
              </el-button-group>
            </div>
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
import { computed, reactive, ref, watch, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";

import { downloadOriginalImage, editImage } from "@/services/imageService";
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
const previewCanvas = ref<HTMLCanvasElement | null>(null);
const previewLoading = ref(false);
const previewError = ref<string | null>(null);
const previewSize = reactive({ width: 0, height: 0 });
const originalImageElement = ref<HTMLImageElement | null>(null);
const loadedImageId = ref<number | null>(null);
let objectUrl: string | null = null;
let currentLoadToken = 0;
let rafHandle = 0;

const form = reactive({
  cropEnabled: false,
  toneEnabled: false,
  rotationEnabled: false,
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
  rotation: {
    degrees: 0,
  },
});

const canCrop = computed(() => !!props.image?.width && !!props.image?.height);
const previewReady = computed(
  () =>
    !!originalImageElement.value && !previewLoading.value && !previewError.value
);

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

const hasRotationSelection = computed(
  () => form.rotationEnabled && Math.abs(form.rotation.degrees) > 0.01
);

const canSubmit = computed(
  () =>
    hasCropSelection.value ||
    hasToneSelection.value ||
    hasRotationSelection.value
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
  form.rotationEnabled = false;
  form.tone.brightness = 0;
  form.tone.contrast = 0;
  form.tone.warmth = 0;
  form.rotation.degrees = 0;
  form.crop.x = 0;
  form.crop.y = 0;
  const width = props.image?.width ?? 0;
  const height = props.image?.height ?? 0;
  form.crop.width = width > 0 ? width : 0;
  form.crop.height = height > 0 ? height : 0;
};

const clampValue = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

const clampRotation = (value: number) => clampValue(value, -180, 180);

const applyRotationDelta = (delta: number) => {
  if (!form.rotationEnabled) {
    return;
  }
  form.rotation.degrees = clampRotation(form.rotation.degrees + delta);
};

const resetRotation = () => {
  form.rotation.degrees = 0;
};

const revokeObjectUrl = () => {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
};

const clearPreviewCanvas = () => {
  if (!previewCanvas.value) {
    return;
  }
  const ctx = previewCanvas.value.getContext("2d");
  if (ctx) {
    ctx.clearRect(0, 0, previewCanvas.value.width, previewCanvas.value.height);
  }
};

const cleanupPreviewResources = () => {
  currentLoadToken += 1;
  if (rafHandle) {
    cancelAnimationFrame(rafHandle);
    rafHandle = 0;
  }
  revokeObjectUrl();
  originalImageElement.value = null;
  loadedImageId.value = null;
  previewSize.width = 0;
  previewSize.height = 0;
  previewError.value = null;
  previewLoading.value = false;
  clearPreviewCanvas();
};

const rotateCanvas = (canvas: HTMLCanvasElement, degrees: number) => {
  if (Math.abs(degrees) < 0.01) {
    return canvas;
  }
  const radians = (degrees * Math.PI) / 180;
  const sin = Math.abs(Math.sin(radians));
  const cos = Math.abs(Math.cos(radians));
  const width = canvas.width;
  const height = canvas.height;
  const newWidth = Math.max(1, Math.floor(width * cos + height * sin));
  const newHeight = Math.max(1, Math.floor(height * cos + width * sin));
  const rotated = document.createElement("canvas");
  rotated.width = newWidth;
  rotated.height = newHeight;
  const context = rotated.getContext("2d");
  if (!context) {
    return canvas;
  }
  context.translate(newWidth / 2, newHeight / 2);
  context.rotate(radians);
  context.drawImage(canvas, -width / 2, -height / 2);
  return rotated;
};

const clampChannel = (value: number) => clampValue(Math.round(value), 0, 255);

const applyToneAdjustmentsOnCanvas = (
  canvas: HTMLCanvasElement,
  brightness: number,
  contrast: number,
  warmth: number
) => {
  if (!canvas.width || !canvas.height) {
    return;
  }
  const context = canvas.getContext("2d");
  if (!context) {
    return;
  }
  const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
  const data = imageData.data;
  const scale = Math.max(0.1, 1 + contrast);
  const offset = brightness * 255;
  const redDelta = warmth * 25;
  const blueDelta = -warmth * 25;

  for (let i = 0; i < data.length; i += 4) {
    let r = data[i];
    let g = data[i + 1];
    let b = data[i + 2];

    if (brightness !== 0 || contrast !== 0) {
      r = clampChannel(r * scale + offset);
      g = clampChannel(g * scale + offset);
      b = clampChannel(b * scale + offset);
    }

    if (warmth !== 0) {
      r = clampChannel(r + redDelta);
      b = clampChannel(b + blueDelta);
    }

    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
  }

  context.putImageData(imageData, 0, 0);
};

const renderPreview = () => {
  if (!previewCanvas.value || !originalImageElement.value) {
    return;
  }
  const source = originalImageElement.value;
  const sourceWidth = source.naturalWidth || source.width;
  const sourceHeight = source.naturalHeight || source.height;
  if (!sourceWidth || !sourceHeight) {
    previewError.value = "原图尺寸不可用";
    return;
  }

  const safeX = clampValue(form.crop.x, 0, Math.max(0, sourceWidth - 1));
  const safeY = clampValue(form.crop.y, 0, Math.max(0, sourceHeight - 1));
  const maxWidth = Math.max(1, sourceWidth - safeX);
  const maxHeight = Math.max(1, sourceHeight - safeY);
  const safeWidth = hasCropSelection.value
    ? clampValue(form.crop.width, 1, maxWidth)
    : sourceWidth;
  const safeHeight = hasCropSelection.value
    ? clampValue(form.crop.height, 1, maxHeight)
    : sourceHeight;

  const baseCanvas = document.createElement("canvas");
  baseCanvas.width = safeWidth;
  baseCanvas.height = safeHeight;
  const ctx = baseCanvas.getContext("2d");
  if (!ctx) {
    return;
  }
  ctx.drawImage(
    source,
    hasCropSelection.value ? safeX : 0,
    hasCropSelection.value ? safeY : 0,
    safeWidth,
    safeHeight,
    0,
    0,
    safeWidth,
    safeHeight
  );

  let workingCanvas = baseCanvas;

  if (hasRotationSelection.value) {
    workingCanvas = rotateCanvas(workingCanvas, form.rotation.degrees);
  }

  if (hasToneSelection.value) {
    applyToneAdjustmentsOnCanvas(
      workingCanvas,
      form.tone.brightness,
      form.tone.contrast,
      form.tone.warmth
    );
  }

  const target = previewCanvas.value;
  const targetContext = target.getContext("2d");
  if (!targetContext) {
    return;
  }
  target.width = workingCanvas.width;
  target.height = workingCanvas.height;
  targetContext.clearRect(0, 0, target.width, target.height);
  targetContext.drawImage(workingCanvas, 0, 0);
  previewSize.width = workingCanvas.width;
  previewSize.height = workingCanvas.height;
  previewError.value = null;
};

const queueRenderPreview = () => {
  if (!props.modelValue || previewLoading.value) {
    return;
  }
  if (!originalImageElement.value || !previewCanvas.value) {
    return;
  }
  if (rafHandle) {
    cancelAnimationFrame(rafHandle);
  }
  rafHandle = window.requestAnimationFrame(() => {
    rafHandle = 0;
    renderPreview();
  });
};

const loadPreviewSource = async () => {
  if (!props.image || !props.modelValue) {
    return;
  }
  if (
    loadedImageId.value === props.image.id &&
    originalImageElement.value &&
    previewReady.value
  ) {
    queueRenderPreview();
    return;
  }
  const loadToken = ++currentLoadToken;
  previewLoading.value = true;
  previewError.value = null;
  try {
    const blob = await downloadOriginalImage(props.image.id);
    if (loadToken !== currentLoadToken) {
      return;
    }
    revokeObjectUrl();
    const nextUrl = URL.createObjectURL(blob);
    objectUrl = nextUrl;
    const image = new Image();
    image.onload = () => {
      if (loadToken !== currentLoadToken) {
        return;
      }
      originalImageElement.value = image;
      loadedImageId.value = props.image?.id ?? null;
      previewLoading.value = false;
      queueRenderPreview();
    };
    image.onerror = () => {
      if (loadToken !== currentLoadToken) {
        return;
      }
      previewLoading.value = false;
      previewError.value = "原图加载失败，请稍后重试";
    };
    image.src = nextUrl;
  } catch (error) {
    if (loadToken !== currentLoadToken) {
      return;
    }
    previewLoading.value = false;
    previewError.value =
      error instanceof Error ? error.message : "原图加载失败，请稍后重试";
  }
};

const handleReset = () => {
  resetForm();
  queueRenderPreview();
};

const closeDialog = () => {
  emit("update:modelValue", false);
};

const handleDialogClose = () => {
  if (submitting.value) {
    return;
  }
  resetForm();
  cleanupPreviewResources();
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
    ElMessage.warning("请至少启用裁剪、旋转或色调调整");
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
    if (hasRotationSelection.value) {
      payload.rotation = {
        degrees: Number(form.rotation.degrees.toFixed(1)),
      };
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

watch(
  () => props.modelValue,
  (visible) => {
    if (visible && props.image) {
      void loadPreviewSource();
      queueRenderPreview();
    }
    if (!visible) {
      cleanupPreviewResources();
    }
  }
);

watch(
  () => props.image,
  () => {
    resetForm();
    cleanupPreviewResources();
    if (props.image && props.modelValue) {
      void loadPreviewSource();
    }
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

watch(
  () => [
    form.cropEnabled,
    form.crop.x,
    form.crop.y,
    form.crop.width,
    form.crop.height,
    form.toneEnabled,
    form.tone.brightness,
    form.tone.contrast,
    form.tone.warmth,
    form.rotationEnabled,
    form.rotation.degrees,
  ],
  () => {
    queueRenderPreview();
  }
);

onBeforeUnmount(() => {
  cleanupPreviewResources();
});
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

.preview-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.preview-stage {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 8px;
  min-height: 200px;
  background-image: linear-gradient(
      45deg,
      rgba(0, 0, 0, 0.02) 25%,
      transparent 25%,
      transparent 75%,
      rgba(0, 0, 0, 0.02) 75%,
      rgba(0, 0, 0, 0.02)
    ),
    linear-gradient(
      45deg,
      rgba(0, 0, 0, 0.02) 25%,
      transparent 25%,
      transparent 75%,
      rgba(0, 0, 0, 0.02) 75%,
      rgba(0, 0, 0, 0.02)
    );
  background-size: 24px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-canvas {
  width: 100%;
  height: auto;
  max-height: 360px;
  border-radius: 6px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  background: #000;
}

.preview-placeholder {
  width: 100%;
  text-align: center;
  color: rgba(0, 0, 0, 0.55);
  padding: 24px 12px;
}

.preview-placeholder.error {
  color: #f56c6c;
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

.rotation-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rotation-controls.disabled {
  opacity: 0.6;
}

.rotation-shortcuts {
  display: flex;
  justify-content: flex-start;
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

  .preview-stage {
    min-height: 160px;
  }
}
</style>
