export type TagType = "AUTO" | "CUSTOM" | "AI";

export interface ImageTag {
  tagId: number;
  tagName: string;
  tagType: TagType;
  usageCount: number;
  confidence: string | number | null;
}

export interface TagSummary {
  tagId: number;
  tagName: string;
  tagType: TagType;
  usageCount: number;
}

export interface AiTagSuggestionInput {
  name: string;
  confidence?: number;
}

export interface AiTagGenerationOptions {
  hints?: string[];
  limit?: number;
}
