import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useImageTagStore } from "@/stores/imageTags";

const mockFetchImageTags = vi.hoisted(() => vi.fn());
const mockFetchPopularTags = vi.hoisted(() => vi.fn());
const mockAddCustomTags = vi.hoisted(() => vi.fn());
const mockAddAiTags = vi.hoisted(() => vi.fn());
const mockRemoveImageTag = vi.hoisted(() => vi.fn());

const messageSpies = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}));

vi.mock("@/services/tagService", () => ({
  fetchImageTags: mockFetchImageTags,
  fetchPopularTags: mockFetchPopularTags,
  addCustomTags: mockAddCustomTags,
  addAiTags: mockAddAiTags,
  removeImageTag: mockRemoveImageTag,
}));

vi.mock("element-plus", () => ({
  ElMessage: messageSpies,
}));

describe("image tag store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    Object.values(messageSpies).forEach((spy) => spy.mockClear());
  });

  it("initializes by loading tags and popular tags", async () => {
    const store = useImageTagStore();
    const tags = [
      {
        tagId: 1,
        tagName: "macro",
        tagType: "CUSTOM",
        usageCount: 2,
        confidence: 1,
      },
    ];
    const popular = [
      { tagId: 10, tagName: "travel", tagType: "CUSTOM", usageCount: 50 },
    ];
    mockFetchImageTags.mockResolvedValueOnce(tags);
    mockFetchPopularTags.mockResolvedValueOnce(popular);

    await store.initialize(5);

    expect(store.currentImageId).toBe(5);
    expect(store.tags).toEqual(tags);
    expect(store.popularTags).toEqual(popular);
    expect(mockFetchImageTags).toHaveBeenCalledWith(5);
    expect(mockFetchPopularTags).toHaveBeenCalledWith(12);
  });

  it("warns when adding custom tags without image", async () => {
    const store = useImageTagStore();

    await store.addCustom(["macro"]);

    expect(messageSpies.warning).toHaveBeenCalledWith("请先选择图片");
    expect(mockAddCustomTags).not.toHaveBeenCalled();
  });

  it("adds custom tags and updates state", async () => {
    const store = useImageTagStore();
    store.currentImageId = 9;
    const nextTags = [
      {
        tagId: 2,
        tagName: "macro",
        tagType: "CUSTOM",
        usageCount: 1,
        confidence: 1,
      },
    ];
    mockAddCustomTags.mockResolvedValueOnce(nextTags);

    await store.addCustom(["macro"]);

    expect(mockAddCustomTags).toHaveBeenCalledWith(9, ["macro"]);
    expect(store.tags).toEqual(nextTags);
    expect(store.isMutating).toBe(false);
    expect(messageSpies.success).toHaveBeenCalledWith("自定义标签已添加");
  });

  it("removes tags and reloads list", async () => {
    const store = useImageTagStore();
    store.currentImageId = 7;
    const refreshed = [
      {
        tagId: 3,
        tagName: "nature",
        tagType: "AUTO",
        usageCount: 5,
        confidence: 0.9,
      },
    ];
    mockRemoveImageTag.mockResolvedValueOnce(undefined);
    mockFetchImageTags.mockResolvedValueOnce(refreshed);

    await store.remove(99);

    expect(mockRemoveImageTag).toHaveBeenCalledWith(7, 99);
    expect(mockFetchImageTags).toHaveBeenCalledWith(7);
    expect(store.tags).toEqual(refreshed);
    expect(messageSpies.success).toHaveBeenCalledWith("标签已移除");
    expect(store.isMutating).toBe(false);
  });
});
