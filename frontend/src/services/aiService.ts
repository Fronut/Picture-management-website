import apiClient from "./apiClient";
import type { ApiResponse } from "@/types/api";
import type { AiSearchInterpretation } from "@/types/ai";

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
