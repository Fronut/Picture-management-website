import { defineStore } from "pinia";
import { ElMessage } from "element-plus";

import { searchImages } from "@/services/imageService";
import type {
  ImageSearchPayload,
  ImageSearchResult,
  SortDirection,
} from "@/types/image";
import type { PageResponse } from "@/types/api";

export interface ImageSearchState {
  results: ImageSearchResult[];
  pagination: Pick<
    PageResponse<ImageSearchResult>,
    | "pageNumber"
    | "pageSize"
    | "totalElements"
    | "totalPages"
    | "first"
    | "last"
  >;
  filters: ImageSearchPayload;
  loading: boolean;
}

const defaultFilters: ImageSearchPayload = {
  keyword: "",
  tags: [],
  onlyOwn: false,
  size: 20,
  page: 0,
  sortBy: "uploadTime",
  sortDirection: "DESC",
};

const defaultState = (): ImageSearchState => ({
  results: [],
  pagination: {
    pageNumber: 0,
    pageSize: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  },
  filters: { ...defaultFilters },
  loading: false,
});

export const useImageSearchStore = defineStore("imageSearch", {
  state: defaultState,
  getters: {
    hasResults: (state) => state.results.length > 0,
  },
  actions: {
    updateFilters(partial: Partial<ImageSearchPayload>) {
      this.filters = {
        ...this.filters,
        ...partial,
      };
    },

    resetFilters() {
      this.filters = { ...defaultFilters };
    },

    async fetch(page?: number) {
      try {
        this.loading = true;
        const payload: ImageSearchPayload = {
          ...this.filters,
          page: page ?? this.filters.page ?? 0,
        };
        const response = await searchImages(payload);
        this.results = response.content;
        this.pagination = {
          pageNumber: response.pageNumber,
          pageSize: response.pageSize,
          totalElements: response.totalElements,
          totalPages: response.totalPages,
          first: response.first,
          last: response.last,
        };
        this.filters.page = response.pageNumber;
        this.filters.size = response.pageSize;
      } catch (error) {
        ElMessage.error(
          error instanceof Error ? error.message : "图片搜索失败，请稍后再试"
        );
      } finally {
        this.loading = false;
      }
    },

    async searchWithFilters(partial?: Partial<ImageSearchPayload>) {
      if (partial) {
        this.updateFilters({ ...partial, page: 0 });
      } else {
        this.filters.page = 0;
      }
      await this.fetch(0);
    },

    async changePage(page: number) {
      if (page < 0 || page === this.pagination.pageNumber) {
        return;
      }
      await this.fetch(page);
    },

    async changePageSize(size: number) {
      if (size <= 0) {
        return;
      }
      this.filters.size = size;
      await this.fetch(0);
    },

    async changeSort(
      sortBy: NonNullable<ImageSearchPayload["sortBy"]>,
      direction: SortDirection
    ) {
      this.filters.sortBy = sortBy;
      this.filters.sortDirection = direction;
      await this.fetch(0);
    },
  },
});
