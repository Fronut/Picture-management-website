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

export type SortDirection = "ASC" | "DESC";

export interface ImageSearchPayload {
  keyword?: string;
  privacyLevel?: ImagePrivacyLevel;
  tags?: string[];
  uploadedFrom?: string;
  uploadedTo?: string;
  cameraMake?: string;
  cameraModel?: string;
  minWidth?: number;
  maxWidth?: number;
  minHeight?: number;
  maxHeight?: number;
  onlyOwn?: boolean;
  page?: number;
  size?: number;
  sortBy?: "uploadTime" | "originalFilename" | "fileSize" | "width" | "height";
  sortDirection?: SortDirection;
}

export interface ImageSummaryThumbnail {
  id: number;
  sizeType: "SMALL" | "MEDIUM" | "LARGE";
  width: number;
  height: number;
  filePath: string;
  fileSize: number;
}

export interface ImageSearchResult {
  id: number;
  originalFilename: string;
  storedFilename: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  width: number | null;
  height: number | null;
  description: string | null;
  privacyLevel: ImagePrivacyLevel;
  uploadTime: string;
  cameraMake?: string | null;
  cameraModel?: string | null;
  takenTime?: string | null;
  tags: string[];
  thumbnails: ImageSummaryThumbnail[];
}

export interface ImageDeleteResult {
  deletedImageId: number;
  deleteTime: string;
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
