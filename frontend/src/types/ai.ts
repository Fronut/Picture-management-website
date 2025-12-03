export interface AiSearchInterpretation {
  query: string;
  keywords?: string[];
  tags?: string[];
  filters?: Record<string, unknown>;
  explanations?: Array<Record<string, unknown>>;
  confidence?: number;
}
