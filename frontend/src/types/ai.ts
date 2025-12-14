import type { PageResponse } from "./api";
import type { ImageSearchPayload, ImageSearchResult } from "./image";

export interface AiChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

export interface AiSearchInterpretation {
  query: string;
  keywords?: string[];
  tags?: string[];
  filters?: Record<string, unknown>;
  explanations?: Array<Record<string, unknown>>;
  confidence?: number;
}

export interface AiToolFunction {
  name?: string;
  arguments?: string;
}

export interface AiToolCall {
  id?: string;
  type?: string;
  function?: AiToolFunction;
}

export interface AiChatPrimaryResult {
  summary?: string;
  query?: string;
  interpretation?: AiSearchInterpretation;
  searchPayload?: Partial<ImageSearchPayload>;
  page?: PageResponse<ImageSearchResult>;
  matches?: ImageSearchResult[];
  requestedLimit?: number;
  onlyOwn?: boolean;
}

export interface AiChatSearchResult {
  message: string;
  primaryResult: AiChatPrimaryResult | null;
  results: AiChatPrimaryResult[];
  toolCalls?: AiToolCall[];
}
