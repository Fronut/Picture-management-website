import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useImageUploadStore } from "@/stores/imageUpload";
import type { UploadedImage } from "@/types/image";

const mockUploadImages = vi.hoisted(() => vi.fn());

const messageSpies = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
}));

const uuidStub = vi.hoisted(() => {
  let counter = 0;
  const generator = vi.fn(() => `candidate-${++counter}`);
  return {
    generator,
    reset: () => {
      counter = 0;
      generator.mockClear();
    },
  };
});

vi.mock("@/services/imageService", () => ({
  uploadImages: mockUploadImages,
}));

vi.mock("uuid", () => ({
  v4: uuidStub.generator,
}));

vi.mock("element-plus", () => ({
  ElMessage: messageSpies,
}));

vi.mock("axios", () => ({
  isAxiosError: (value: unknown): value is { isAxiosError: boolean } =>
    Boolean(
      value &&
        typeof value === "object" &&
        (value as { isAxiosError?: boolean }).isAxiosError
    ),
}));

const createFile = (name: string, size = 1024) =>
  new File([new Uint8Array(size)], name, { type: "image/jpeg" });

const sampleUploadResponse = (name: string, id: number): UploadedImage => ({
  id,
  originalFilename: name,
  storedFilename: `${id}-${name}`,
  filePath: `/uploads/${id}`,
  fileSize: 1024,
  mimeType: "image/jpeg",
  width: 100,
  height: 100,
  uploadTime: new Date().toISOString(),
});

describe("image upload store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    Object.values(messageSpies).forEach((spy) => spy.mockClear());
    uuidStub.reset();
  });

  it("skips duplicate files and notifies user", () => {
    const store = useImageUploadStore();
    const file = createFile("dup.jpg");

    store.addFiles([file]);
    store.addFiles([file]);

    expect(store.candidates).toHaveLength(1);
    expect(messageSpies.info).toHaveBeenCalledWith(
      "dup.jpg 已在待上传列表，已跳过重复添加"
    );
  });

  it("warns when uploading without ready files", async () => {
    const store = useImageUploadStore();

    await store.upload();

    expect(messageSpies.warning).toHaveBeenCalledWith("请先选择要上传的图片");
    expect(mockUploadImages).not.toHaveBeenCalled();
  });

  it("uploads ready files and clears candidates on success", async () => {
    const store = useImageUploadStore();
    const fileA = createFile("a.jpg");
    const fileB = createFile("b.jpg");
    store.addFiles([fileA, fileB]);

    const response = [
      sampleUploadResponse("a.jpg", 1),
      sampleUploadResponse("b.jpg", 2),
    ];
    mockUploadImages.mockResolvedValueOnce(response);

    await store.upload();

    expect(mockUploadImages).toHaveBeenCalledWith({
      files: [fileA, fileB],
      privacyLevel: "PRIVATE",
      description: "",
    });
    expect(store.results).toEqual(response);
    expect(store.summary).toEqual({ total: 2, success: 2, failed: 0 });
    expect(store.candidates).toHaveLength(0);
    expect(messageSpies.success).toHaveBeenCalledWith("上传成功 2 张图片");
  });

  it("marks failed uploads and surfaces duplicate details", async () => {
    const store = useImageUploadStore();
    const file = createFile("dup.jpg");
    store.addFiles([file]);

    const axiosLikeError = {
      isAxiosError: true,
      response: {
        data: {
          message: "服务器拒绝",
          data: {
            duplicates: ["dup.jpg"],
          },
        },
      },
    };
    mockUploadImages.mockRejectedValueOnce(axiosLikeError);

    await store.upload();

    expect(store.summary).toEqual({ total: 1, success: 0, failed: 1 });
    expect(store.candidates[0]).toMatchObject({
      status: "error",
      errorMessage: "服务器拒绝：dup.jpg",
    });
    expect(messageSpies.error).toHaveBeenCalledWith("服务器拒绝：dup.jpg");
    expect(store.isUploading).toBe(false);
  });
});
