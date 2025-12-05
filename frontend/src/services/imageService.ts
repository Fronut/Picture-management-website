import apiClient from "./apiClient";
import type { ApiResponse, PageResponse } from "@/types/api";
import type {
  ImageDeleteResult,
  ImageEditPayload,
  ImageEditResult,
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

export const fetchHighlightImages = async (
  size = 6
): Promise<ImageSearchResult[]> => {
  const { data } = await apiClient.get<ApiResponse<ImageSearchResult[]>>(
    `${IMAGE_BASE}/highlights`,
    {
      params: { size },
    }
  );

  if (!data.data) {
    throw new Error(data.message || "获取精选图片失败");
  }

  return data.data;
};

export const downloadThumbnail = async (
  imageId: number,
  thumbnailId: number
): Promise<Blob> => {
  const { data } = await apiClient.get<Blob>(
    `${IMAGE_BASE}/${imageId}/thumbnails/${thumbnailId}`,
    {
      responseType: "blob",
    }
  );
  return data;
};

export const downloadOriginalImage = async (imageId: number): Promise<Blob> => {
  const { data } = await apiClient.get<Blob>(
    `${IMAGE_BASE}/${imageId}/content`,
    {
      responseType: "blob",
    }
  );
  return data;
};

export const deleteImage = async (
  imageId: number
): Promise<ImageDeleteResult> => {
  const { data } = await apiClient.delete<ApiResponse<ImageDeleteResult>>(
    `${IMAGE_BASE}/${imageId}`
  );

  if (!data.data) {
    throw new Error(data.message || "删除失败");
  }

  return data.data;
};

export const editImage = async (
  payload: ImageEditPayload
): Promise<ImageEditResult> => {
  const requestBody: Record<string, unknown> = {};

  if (payload.crop) {
    requestBody.crop = {
      x: payload.crop.x,
      y: payload.crop.y,
      width: payload.crop.width,
      height: payload.crop.height,
    };
  }

  const tone = normalizeTonePayload(payload.toneAdjustment);
  if (tone) {
    requestBody.toneAdjustment = tone;
  }

  if (!requestBody.crop && !requestBody.toneAdjustment) {
    throw new Error("请至少选择一个编辑操作");
  }

  const { data } = await apiClient.post<ApiResponse<ImageEditResult>>(
    `${IMAGE_BASE}/${payload.imageId}/edit`,
    requestBody
  );

  if (!data.data) {
    throw new Error(data.message || "编辑失败");
  }

  return data.data;
};

const normalizeSearchPayload = (payload: ImageSearchPayload) => ({
  keyword: payload.keyword?.trim() || undefined,
  // privacy: treat explicit 'ALL' as undefined to avoid sending an invalid enum
  privacyLevel:
    payload.privacyLevel === "ALL" ? undefined : payload.privacyLevel,
  tags: payload.tags?.filter((tag) => tag.trim()) ?? [],
  uploadedFrom: payload.uploadedFrom ?? undefined,
  uploadedTo: payload.uploadedTo ?? undefined,
  cameraMake: payload.cameraMake?.trim() || undefined,
  cameraModel: payload.cameraModel?.trim() || undefined,
  // Normalise numeric ranges: disallow non-positive, and ensure min <= max by swapping
  minWidth: (() => {
    let min = payload.minWidth ?? undefined;
    let max = payload.maxWidth ?? undefined;
    if (typeof min === "number" && min <= 0) min = undefined;
    if (typeof max === "number" && max <= 0) max = undefined;
    if (typeof min === "number" && typeof max === "number" && min > max) {
      // swap to be friendly
      const t = min;
      min = max;
      max = t;
    }
    return min ?? undefined;
  })(),
  maxWidth: (() => {
    let min = payload.minWidth ?? undefined;
    let max = payload.maxWidth ?? undefined;
    if (typeof min === "number" && min <= 0) min = undefined;
    if (typeof max === "number" && max <= 0) max = undefined;
    if (typeof min === "number" && typeof max === "number" && min > max) {
      const t = min;
      min = max;
      max = t;
    }
    return max ?? undefined;
  })(),
  minHeight: (() => {
    let min = payload.minHeight ?? undefined;
    let max = payload.maxHeight ?? undefined;
    if (typeof min === "number" && min <= 0) min = undefined;
    if (typeof max === "number" && max <= 0) max = undefined;
    if (typeof min === "number" && typeof max === "number" && min > max) {
      const t = min;
      min = max;
      max = t;
    }
    return min ?? undefined;
  })(),
  maxHeight: (() => {
    let min = payload.minHeight ?? undefined;
    let max = payload.maxHeight ?? undefined;
    if (typeof min === "number" && min <= 0) min = undefined;
    if (typeof max === "number" && max <= 0) max = undefined;
    if (typeof min === "number" && typeof max === "number" && min > max) {
      const t = min;
      min = max;
      max = t;
    }
    return max ?? undefined;
  })(),
  onlyOwn: payload.onlyOwn ?? false,
  page: payload.page ?? 0,
  size: payload.size ?? 20,
  sortBy: payload.sortBy ?? "uploadTime",
  sortDirection: payload.sortDirection ?? "DESC",
});

const normalizeTonePayload = (tone?: ImageEditPayload["toneAdjustment"]) => {
  if (!tone) {
    return undefined;
  }
  const normalized: Record<string, number> = {};
  const maybeAssign = (key: keyof typeof tone) => {
    const value = tone[key];
    if (typeof value === "number" && value !== 0) {
      normalized[key] = Number(value.toFixed(2));
    }
  };

  maybeAssign("brightness");
  maybeAssign("contrast");
  maybeAssign("warmth");

  return Object.keys(normalized).length ? normalized : undefined;
};
