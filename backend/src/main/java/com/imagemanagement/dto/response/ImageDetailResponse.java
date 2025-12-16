package com.imagemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imagemanagement.entity.enums.TagType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Detailed payload for a single image including owner, EXIF and tag metadata.
 */
public class ImageDetailResponse {

    private ImageSummaryResponse summary;
    private OwnerSummary owner;
    private ExifDetails exif;
    private List<TagDetail> tagDetails = new ArrayList<>();
    private AccessInfo access;

    public ImageSummaryResponse getSummary() {
        return summary;
    }

    public void setSummary(ImageSummaryResponse summary) {
        this.summary = summary;
    }

    public OwnerSummary getOwner() {
        return owner;
    }

    public void setOwner(OwnerSummary owner) {
        this.owner = owner;
    }

    public ExifDetails getExif() {
        return exif;
    }

    public void setExif(ExifDetails exif) {
        this.exif = exif;
    }

    public List<TagDetail> getTagDetails() {
        return tagDetails;
    }

    public void setTagDetails(List<TagDetail> tagDetails) {
        this.tagDetails = tagDetails != null ? tagDetails : new ArrayList<>();
    }

    public AccessInfo getAccess() {
        return access;
    }

    public void setAccess(AccessInfo access) {
        this.access = access;
    }

    public static class OwnerSummary {
        private Long id;
        private String username;
        private String email;
        private String avatarUrl;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    public static class ExifDetails {
        private String cameraMake;
        private String cameraModel;
        private String exposureTime;
        @JsonProperty("fNumber")
        private String fNumber;
        private Integer isoSpeed;
        private String focalLength;
        private java.math.BigDecimal latitude;
        private java.math.BigDecimal longitude;
        private String locationName;
        private java.time.LocalDateTime takenTime;

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

        public String getExposureTime() {
            return exposureTime;
        }

        public void setExposureTime(String exposureTime) {
            this.exposureTime = exposureTime;
        }

        public String getFNumber() {
            return fNumber;
        }

        public void setFNumber(String fNumber) {
            this.fNumber = fNumber;
        }

        public Integer getIsoSpeed() {
            return isoSpeed;
        }

        public void setIsoSpeed(Integer isoSpeed) {
            this.isoSpeed = isoSpeed;
        }

        public String getFocalLength() {
            return focalLength;
        }

        public void setFocalLength(String focalLength) {
            this.focalLength = focalLength;
        }

        public java.math.BigDecimal getLatitude() {
            return latitude;
        }

        public void setLatitude(java.math.BigDecimal latitude) {
            this.latitude = latitude;
        }

        public java.math.BigDecimal getLongitude() {
            return longitude;
        }

        public void setLongitude(java.math.BigDecimal longitude) {
            this.longitude = longitude;
        }

        public String getLocationName() {
            return locationName;
        }

        public void setLocationName(String locationName) {
            this.locationName = locationName;
        }

        public java.time.LocalDateTime getTakenTime() {
            return takenTime;
        }

        public void setTakenTime(java.time.LocalDateTime takenTime) {
            this.takenTime = takenTime;
        }
    }

    public static class TagDetail {
        private Long tagId;
        private String tagName;
        private TagType tagType;
        private Integer usageCount;
        private BigDecimal confidence;

        public Long getTagId() {
            return tagId;
        }

        public void setTagId(Long tagId) {
            this.tagId = tagId;
        }

        public String getTagName() {
            return tagName;
        }

        public void setTagName(String tagName) {
            this.tagName = tagName;
        }

        public TagType getTagType() {
            return tagType;
        }

        public void setTagType(TagType tagType) {
            this.tagType = tagType;
        }

        public Integer getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(Integer usageCount) {
            this.usageCount = usageCount;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(BigDecimal confidence) {
            this.confidence = confidence == null ? null : confidence.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static class AccessInfo {
        private boolean canEdit;
        private boolean canDelete;
        private boolean canDownloadOriginal;

        public boolean isCanEdit() {
            return canEdit;
        }

        public void setCanEdit(boolean canEdit) {
            this.canEdit = canEdit;
        }

        public boolean isCanDelete() {
            return canDelete;
        }

        public void setCanDelete(boolean canDelete) {
            this.canDelete = canDelete;
        }

        public boolean isCanDownloadOriginal() {
            return canDownloadOriginal;
        }

        public void setCanDownloadOriginal(boolean canDownloadOriginal) {
            this.canDownloadOriginal = canDownloadOriginal;
        }
    }
}
