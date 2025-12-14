import apiClient from "./apiClient";
import type { ApiResponse } from "@/types/api";
import type { AiChatMessage, AiChatSearchResult } from "@/types/ai";

export const chatSearchImages = async (
  query: string,
  limit = 6,
  onlyOwn?: boolean,
  messages?: AiChatMessage[]
): Promise<AiChatSearchResult> => {
  const { data } = await apiClient.post<ApiResponse<AiChatSearchResult>>(
    "/ai/chat",
    {
      query: query.trim(),
      limit,
      onlyOwn,
      messages: messages?.length ? messages : undefined,
    }
  );
  if (!data.data) {
    throw new Error(data.message || "AI 对话搜索失败");
  }
  return data.data;
};
