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
        const f = file as File;
        // avoid exact duplicate entries (same name + size) in candidates
        const exists = this.candidates.some(
          (c) => c.file.name === f.name && c.file.size === f.size
        );
        if (exists) {
          ElMessage.info(`${f.name} 已在待上传列表，已跳过重复添加`);
          return;
        }
        const candidate: UploadCandidate = {
          id: uuid(),
          file: f,
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

      // ids of candidates that will be uploaded
      const uploadIds = readyCandidates.map((c) => c.id);

      this.isUploading = true;
      this.summary = null;
      this.results = [];

      // mark only the ready candidates as uploading
      this.candidates = this.candidates.map((candidate) =>
        uploadIds.includes(candidate.id)
          ? { ...candidate, status: "uploading", errorMessage: undefined }
          : candidate
      );

      try {
        const response = await uploadImages({
          files: filesToUpload,
          privacyLevel: this.privacyLevel,
          description: this.description,
        });

        this.results = response;
        // build summary based on the uploaded batch
        this.summary = buildSummary(readyCandidates, response);

        // 所有成功上传的候选直接从列表中移除，防止重复上传
        this.candidates = this.candidates.filter(
          (candidate) => !uploadIds.includes(candidate.id)
        );

        ElMessage.success(`上传成功 ${response.length} 张图片`);
      } catch (error) {
        // mark only the attempted candidates as error
        this.candidates = this.candidates.map((candidate) =>
          uploadIds.includes(candidate.id)
            ? {
                ...candidate,
                status: "error",
                errorMessage:
                  error instanceof Error ? error.message : "上传失败",
              }
            : candidate
        );
        this.summary = buildSummary(readyCandidates, []);
        ElMessage.error(
          error instanceof Error ? error.message : "文件上传失败，请稍后再试"
        );
      } finally {
        this.isUploading = false;
      }
    },
    removeResultById(id: number) {
      this.results = this.results.filter((r) => r.id !== id);
    },
  },
});
