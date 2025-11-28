export type ImagePrivacyLevel = "PUBLIC" | "PRIVATE";

export interface ImageUploadPayload {
  files: File[];
  privacyLevel?: ImagePrivacyLevel;
  description?: string;
}

export interface UploadedImage {
  id: number;
  originalFilename: string;
  storedFilename: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  width: number | null;
  height: number | null;
  uploadTime: string;
}

export interface UploadCandidate {
  id: string;
  file: File;
  status: "ready" | "uploading" | "success" | "error";
  errorMessage?: string;
}

export interface UploadResultSummary {
  total: number;
  success: number;
  failed: number;
}
