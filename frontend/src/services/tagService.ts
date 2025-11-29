import apiClient from "./apiClient";
import type { ApiResponse } from "@/types/api";
import type { AiTagSuggestionInput, ImageTag, TagSummary } from "@/types/tag";

const IMAGE_BASE = "/images";
const TAG_BASE = "/tags";

export const fetchImageTags = async (imageId: number): Promise<ImageTag[]> => {
  const { data } = await apiClient.get<ApiResponse<ImageTag[]>>(
    `${IMAGE_BASE}/${imageId}/tags`
  );

  return data.data ?? [];
};

export const addCustomTags = async (
  imageId: number,
  tagNames: string[]
): Promise<ImageTag[]> => {
  const { data } = await apiClient.post<ApiResponse<ImageTag[]>>(
    `${IMAGE_BASE}/${imageId}/tags/custom`,
    {
      tagNames,
    }
  );

  return data.data ?? [];
};

export const addAiTags = async (
  imageId: number,
  suggestions: AiTagSuggestionInput[]
): Promise<ImageTag[]> => {
  const payload = {
    tags: suggestions.map((suggestion) => ({
      name: suggestion.name,
      confidence: suggestion.confidence,
    })),
  };

  const { data } = await apiClient.post<ApiResponse<ImageTag[]>>(
    `${IMAGE_BASE}/${imageId}/tags/ai`,
    payload
  );

  return data.data ?? [];
};

export const removeImageTag = async (
  imageId: number,
  tagId: number
): Promise<void> => {
  await apiClient.delete<ApiResponse<void>>(
    `${IMAGE_BASE}/${imageId}/tags/${tagId}`
  );
};

export const fetchPopularTags = async (limit = 12): Promise<TagSummary[]> => {
  const { data } = await apiClient.get<ApiResponse<TagSummary[]>>(
    `${TAG_BASE}/popular`,
    {
      params: { limit },
    }
  );

  return data.data ?? [];
};
