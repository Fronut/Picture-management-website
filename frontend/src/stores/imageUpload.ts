import { defineStore } from "pinia";
import { v4 as uuid } from "uuid";
import { ElMessage } from "element-plus";

import { uploadImages } from "@/services/imageService";
import type {
  ImagePrivacyLevel,
  UploadCandidate,
  UploadResultSummary,
  UploadedImage,
} from "@/types/image";
import type { UploadRawFile } from "element-plus";

interface UploadState {
  candidates: UploadCandidate[];
  isUploading: boolean;
  results: UploadedImage[];
  summary: UploadResultSummary | null;
  privacyLevel: ImagePrivacyLevel;
  description: string;
}

const defaultState = (): UploadState => ({
  candidates: [],
  isUploading: false,
  results: [],
  summary: null,
  privacyLevel: "PRIVATE",
  description: "",
});

const buildSummary = (
  candidates: UploadCandidate[],
  responses: UploadedImage[]
): UploadResultSummary => ({
  total: candidates.length,
  success: responses.length,
  failed: candidates.length - responses.length,
});

export const useImageUploadStore = defineStore("imageUpload", {
  state: defaultState,
  getters: {
    readyFiles: (state) => state.candidates.filter((c) => c.status === "ready"),
    hasFiles: (state) => state.candidates.length > 0,
  },
  actions: {
    addFiles(files: Array<File | UploadRawFile>) {
      files.forEach((file) => {
        const candidate: UploadCandidate = {
          id: uuid(),
          file: file as File,
          status: "ready",
        };
        this.candidates.push(candidate);
      });
    },
    removeCandidate(id: string) {
      this.candidates = this.candidates.filter(
        (candidate) => candidate.id !== id
      );
    },
    clearAll() {
      this.candidates = [];
      this.results = [];
      this.summary = null;
    },
    updateDescription(value: string) {
      this.description = value;
    },
    updatePrivacy(level: ImagePrivacyLevel) {
      this.privacyLevel = level;
    },
    async upload() {
      const readyCandidates = this.readyFiles;
      if (!readyCandidates.length) {
        ElMessage.warning("请先选择要上传的图片");
        return;
      }

      const filesToUpload = readyCandidates.map((candidate) => candidate.file);

      this.isUploading = true;
      this.summary = null;
      this.results = [];

      this.candidates = this.candidates.map((candidate) => ({
        ...candidate,
        status: "uploading",
        errorMessage: undefined,
      }));

      try {
        const response = await uploadImages({
          files: filesToUpload,
          privacyLevel: this.privacyLevel,
          description: this.description,
        });

        this.results = response;
        this.summary = buildSummary(this.candidates, response);

        this.candidates = this.candidates.map((candidate) => ({
          ...candidate,
          status: "success",
          errorMessage: undefined,
        }));

        ElMessage.success(`上传成功 ${response.length} 张图片`);
      } catch (error) {
        this.candidates = this.candidates.map((candidate) => ({
          ...candidate,
          status: "error",
          errorMessage: error instanceof Error ? error.message : "上传失败",
        }));
        this.summary = buildSummary(this.candidates, []);
        ElMessage.error(
          error instanceof Error ? error.message : "文件上传失败，请稍后再试"
        );
      } finally {
        this.isUploading = false;
      }
    },
  },
});
