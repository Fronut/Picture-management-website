import { defineStore } from "pinia";
import { ElMessage } from "element-plus";

import {
  addAiTags,
  addCustomTags,
  fetchImageTags,
  fetchPopularTags,
  removeImageTag,
} from "@/services/tagService";
import { useImageUploadStore } from "@/stores/imageUpload";
import type { AiTagSuggestionInput, ImageTag, TagSummary } from "@/types/tag";

interface ImageTagState {
  currentImageId: number | null;
  tags: ImageTag[];
  isLoading: boolean;
  isMutating: boolean;
  popularTags: TagSummary[];
  popularLoading: boolean;
}

const defaultState = (): ImageTagState => ({
  currentImageId: null,
  tags: [],
  isLoading: false,
  isMutating: false,
  popularTags: [],
  popularLoading: false,
});

export const useImageTagStore = defineStore("imageTags", {
  state: defaultState,
  actions: {
    async initialize(imageId: number) {
      if (!imageId || Number.isNaN(imageId)) {
        ElMessage.warning("请提供有效的图片 ID");
        return;
      }
      this.currentImageId = imageId;
      await Promise.all([this.loadTags(), this.loadPopularTags()]);
    },

    async loadTags(imageId?: number) {
      const targetId = imageId ?? this.currentImageId;
      if (!targetId) {
        return;
      }
      this.isLoading = true;
      try {
        this.tags = await fetchImageTags(targetId);
      } catch (error) {
        // 如果后端返回图片不存在（被删除），从上传页中移除残留记录并提示用户
        const anyErr: any = error;
        const status = anyErr?.response?.status;
        const msg = anyErr?.response?.data?.message ?? anyErr?.message;
        if (status === 404 || /Image not found/i.test(msg)) {
          ElMessage.warning("图片不存在或已被删除，已移除本地缓存记录");
          try {
            const uploadStore = useImageUploadStore();
            uploadStore.removeResultById(Number(targetId));
          } catch (e) {
            // ignore
          }
          this.currentImageId = null;
          this.tags = [];
          return;
        }

        ElMessage.error(
          error instanceof Error ? error.message : "获取标签失败"
        );
      } finally {
        this.isLoading = false;
      }
    },

    async loadPopularTags(limit = 12) {
      this.popularLoading = true;
      try {
        this.popularTags = await fetchPopularTags(limit);
      } catch (error) {
        ElMessage.error(
          error instanceof Error ? error.message : "读取热门标签失败"
        );
      } finally {
        this.popularLoading = false;
      }
    },

    async addCustom(tagNames: string[]) {
      if (!this.currentImageId) {
        ElMessage.warning("请先选择图片");
        return;
      }
      if (!tagNames.length) {
        ElMessage.warning("请输入至少一个标签");
        return;
      }
      this.isMutating = true;
      try {
        this.tags = await addCustomTags(this.currentImageId, tagNames);
        ElMessage.success("自定义标签已添加");
      } catch (error) {
        ElMessage.error(
          error instanceof Error ? error.message : "添加标签失败"
        );
      } finally {
        this.isMutating = false;
      }
    },

    async addAi(suggestions: AiTagSuggestionInput[]) {
      if (!this.currentImageId) {
        ElMessage.warning("请先选择图片");
        return;
      }
      const filtered = suggestions.filter((item) => item.name.trim());
      if (!filtered.length) {
        ElMessage.warning("请输入至少一个标签建议");
        return;
      }
      this.isMutating = true;
      try {
        this.tags = await addAiTags(this.currentImageId, filtered);
        ElMessage.success("AI 标签已同步");
      } catch (error) {
        ElMessage.error(
          error instanceof Error ? error.message : "添加 AI 标签失败"
        );
      } finally {
        this.isMutating = false;
      }
    },

    async remove(tagId: number) {
      if (!this.currentImageId) {
        ElMessage.warning("请先选择图片");
        return;
      }
      this.isMutating = true;
      try {
        await removeImageTag(this.currentImageId, tagId);
        await this.loadTags();
        ElMessage.success("标签已移除");
      } catch (error) {
        ElMessage.error(
          error instanceof Error ? error.message : "移除标签失败"
        );
      } finally {
        this.isMutating = false;
      }
    },
  },
});
