import apiClient from "./apiClient";
import type { ApiResponse } from "@/types/api";
import type { AiChatSearchResult, AiSearchInterpretation } from "@/types/ai";

interface InterpretSearchPayload {
  query: string;
  limit?: number;
}

export const interpretSearchQuery = async (
  query: string,
  limit?: number
): Promise<AiSearchInterpretation> => {
  const body: InterpretSearchPayload = { query: query.trim() };
  if (limit) {
    body.limit = limit;
  }
  const { data } = await apiClient.post<ApiResponse<AiSearchInterpretation>>(
    "/ai/search/interpret",
    body
  );
  return (
    data.data ?? {
      query: body.query,
      keywords: [],
      tags: [],
      filters: {},
    }
  );
};

export const chatSearchImages = async (
  query: string,
  limit = 6,
  onlyOwn?: boolean
): Promise<AiChatSearchResult> => {
  const { data } = await apiClient.post<ApiResponse<AiChatSearchResult>>(
    "/ai/search/chat",
    { query: query.trim(), limit, onlyOwn }
  );
  if (!data.data) {
    throw new Error(data.message || "AI 对话搜索失败");
  }
  return data.data;
};
