package com.imagemanagement.dto.response;

import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import com.imagemanagement.entity.enums.ThumbnailSizeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImageSummaryResponse {

    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private String description;
    private ImagePrivacyLevel privacyLevel;
    private LocalDateTime uploadTime;
    private String cameraMake;
    private String cameraModel;
    private LocalDateTime takenTime;
    private List<String> tags = new ArrayList<>();
    private List<ThumbnailSummary> thumbnails = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ImagePrivacyLevel getPrivacyLevel() {
        return privacyLevel;
    }

    public void setPrivacyLevel(ImagePrivacyLevel privacyLevel) {
        this.privacyLevel = privacyLevel;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
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

    public LocalDateTime getTakenTime() {
        return takenTime;
    }

    public void setTakenTime(LocalDateTime takenTime) {
        this.takenTime = takenTime;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public List<ThumbnailSummary> getThumbnails() {
        return thumbnails;
    }

    public void setThumbnails(List<ThumbnailSummary> thumbnails) {
        this.thumbnails = thumbnails != null ? thumbnails : new ArrayList<>();
    }

    public static class ThumbnailSummary {
        private Long id;
        private ThumbnailSizeType sizeType;
        private Integer width;
        private Integer height;
        private String filePath;
        private Integer fileSize;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public ThumbnailSizeType getSizeType() {
            return sizeType;
        }

        public void setSizeType(ThumbnailSizeType sizeType) {
            this.sizeType = sizeType;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getFileSize() {
            return fileSize;
        }

        public void setFileSize(Integer fileSize) {
            this.fileSize = fileSize;
        }
    }
}
