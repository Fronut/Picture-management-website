import apiClient from "./apiClient";
import type { ApiResponse } from "@/types/api";
import type { ImageUploadPayload, UploadedImage } from "@/types/image";

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
