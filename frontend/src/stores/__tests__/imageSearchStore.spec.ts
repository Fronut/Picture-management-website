import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useImageSearchStore } from "@/stores/imageSearch";
import type { ImageSearchResult } from "@/types/image";

const mockSearchImages = vi.hoisted(() => vi.fn());

const messageSpies = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
}));

vi.mock("@/services/imageService", () => ({
  searchImages: mockSearchImages,
}));

vi.mock("element-plus", () => ({
  ElMessage: messageSpies,
}));

describe("image search store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    Object.values(messageSpies).forEach((spy) => spy.mockClear());
  });

  const sampleResult = (): ImageSearchResult => ({
    id: 1,
    originalFilename: "sunset.jpg",
    storedFilename: "sunset-123.jpg",
    filePath: "/images/1",
    fileSize: 2048,
    mimeType: "image/jpeg",
    width: 1920,
    height: 1080,
    description: "海边落日",
    privacyLevel: "PRIVATE",
    uploadTime: "2025-01-01T10:00:00Z",
    cameraMake: "Canon",
    cameraModel: "EOS",
    takenTime: "2024-12-31T11:00:00Z",
    tags: ["travel"],
    thumbnails: [],
  });

  it("updates and resets filters", () => {
    const store = useImageSearchStore();
    store.updateFilters({
      keyword: "sunset",
      privacyLevel: "PUBLIC",
      onlyOwn: true,
    });

    expect(store.filters).toMatchObject({
      keyword: "sunset",
      privacyLevel: "PUBLIC",
      onlyOwn: true,
    });

    store.resetFilters();

    expect(store.filters).toEqual({
      keyword: "",
      tags: [],
      privacyLevel: "ALL",
      onlyOwn: false,
      size: 20,
      page: 0,
      sortBy: "uploadTime",
      sortDirection: "DESC",
    });
  });

  it("fetches search results and updates pagination", async () => {
    const store = useImageSearchStore();
    store.updateFilters({ keyword: "mountain", page: 1, size: 40 });

    const responsePayload = {
      content: [sampleResult()],
      pageNumber: 2,
      pageSize: 40,
      totalElements: 80,
      totalPages: 2,
      first: false,
      last: true,
    };
    mockSearchImages.mockResolvedValueOnce(responsePayload);
    const expectedPayload = {
      ...store.filters,
      page: 1,
    };

    await store.fetch();

    expect(mockSearchImages).toHaveBeenCalledWith(expectedPayload);
    expect(store.results).toEqual(responsePayload.content);
    expect(store.pagination).toEqual({
      pageNumber: 2,
      pageSize: 40,
      totalElements: 80,
      totalPages: 2,
      first: false,
      last: true,
    });
    expect(store.filters.page).toBe(2);
    expect(store.filters.size).toBe(40);
    expect(store.loading).toBe(false);
  });

  it("shows an error message when search fails", async () => {
    const store = useImageSearchStore();
    mockSearchImages.mockRejectedValueOnce(new Error("Server unavailable"));

    await store.fetch();

    expect(messageSpies.error).toHaveBeenCalledWith("Server unavailable");
    expect(store.loading).toBe(false);
  });
});
