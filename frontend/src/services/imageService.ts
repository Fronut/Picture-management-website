import apiClient from "./apiClient";
import type { ApiResponse, PageResponse } from "@/types/api";
import type {
  ImageSearchPayload,
  ImageSearchResult,
  ImageUploadPayload,
  UploadedImage,
} from "@/types/image";

const IMAGE_BASE = "/images";

export const uploadImages = async (
  payload: ImageUploadPayload
): Promise<UploadedImage[]> => {
  if (!payload.files.length) {
    throw new Error("Please select at least one file to upload");
  }

  const formData = new FormData();
  payload.files.forEach((file) => {
    formData.append("files", file);
  });

  if (payload.privacyLevel) {
    formData.append("privacyLevel", payload.privacyLevel);
  }
  if (payload.description?.trim()) {
    formData.append("description", payload.description.trim());
  }

  const { data } = await apiClient.post<ApiResponse<UploadedImage[]>>(
    `${IMAGE_BASE}/upload`,
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }
  );

  if (!data.data) {
    throw new Error(data.message || "Upload failed");
  }

  return data.data;
};

export const searchImages = async (
  payload: ImageSearchPayload
): Promise<PageResponse<ImageSearchResult>> => {
  const requestBody = normalizeSearchPayload(payload);
  const { data } = await apiClient.post<
    ApiResponse<PageResponse<ImageSearchResult>>
  >(`${IMAGE_BASE}/search`, requestBody);
  if (!data.data) {
    throw new Error(data.message || "搜索失败");
  }
  return data.data;
};

const normalizeSearchPayload = (payload: ImageSearchPayload) => ({
  keyword: payload.keyword?.trim() || undefined,
  privacyLevel: payload.privacyLevel,
  tags: payload.tags?.filter((tag) => tag.trim()) ?? [],
  uploadedFrom: payload.uploadedFrom ?? undefined,
  uploadedTo: payload.uploadedTo ?? undefined,
  cameraMake: payload.cameraMake?.trim() || undefined,
  cameraModel: payload.cameraModel?.trim() || undefined,
  minWidth: payload.minWidth ?? undefined,
  maxWidth: payload.maxWidth ?? undefined,
  minHeight: payload.minHeight ?? undefined,
  maxHeight: payload.maxHeight ?? undefined,
  onlyOwn: payload.onlyOwn ?? false,
  page: payload.page ?? 0,
  size: payload.size ?? 20,
  sortBy: payload.sortBy ?? "uploadTime",
  sortDirection: payload.sortDirection ?? "DESC",
});
