package com.imagemanagement.dto.request;

import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

public class ImageSearchRequest {

    private String keyword;

    private ImagePrivacyLevel privacyLevel;

    private List<String> tags = new ArrayList<>();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedTo;

    private String cameraMake;

    private String cameraModel;

    @Min(1)
    private Integer minWidth;

    @Min(1)
    private Integer minHeight;

    @Min(1)
    private Integer maxWidth;

    @Min(1)
    private Integer maxHeight;

    private Boolean onlyOwn = Boolean.FALSE;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    private String sortBy = "uploadTime";

    private Sort.Direction sortDirection = Sort.Direction.DESC;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public ImagePrivacyLevel getPrivacyLevel() {
        return privacyLevel;
    }

    public void setPrivacyLevel(ImagePrivacyLevel privacyLevel) {
        this.privacyLevel = privacyLevel;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public LocalDateTime getUploadedFrom() {
        return uploadedFrom;
    }

    public void setUploadedFrom(LocalDateTime uploadedFrom) {
        this.uploadedFrom = uploadedFrom;
    }

    public LocalDateTime getUploadedTo() {
        return uploadedTo;
    }

    public void setUploadedTo(LocalDateTime uploadedTo) {
        this.uploadedTo = uploadedTo;
    }

    public String getCameraMake() {
        return cameraMake;
    }

    public void setCameraMake(String cameraMake) {
        this.cameraMake = cameraMake;
    }

    public String getCameraModel() {
        return cameraModel;
    }

    public void setCameraModel(String cameraModel) {
        this.cameraModel = cameraModel;
    }

    public Integer getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(Integer minWidth) {
        this.minWidth = minWidth;
    }

    public Integer getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(Integer minHeight) {
        this.minHeight = minHeight;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Integer maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Integer getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(Integer maxHeight) {
        this.maxHeight = maxHeight;
    }

    public Boolean getOnlyOwn() {
        return onlyOwn;
    }

    public void setOnlyOwn(Boolean onlyOwn) {
        this.onlyOwn = onlyOwn;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.max(1, Math.min(size, 100));
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public Sort.Direction getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(Sort.Direction sortDirection) {
        this.sortDirection = sortDirection != null ? sortDirection : Sort.Direction.DESC;
    }
}
