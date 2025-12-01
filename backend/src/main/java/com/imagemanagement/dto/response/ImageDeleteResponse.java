package com.imagemanagement.dto.response;

import java.time.Instant;

public class ImageDeleteResponse {

    private Long deletedImageId;
    private Instant deleteTime;

    public ImageDeleteResponse() {
    }

    public ImageDeleteResponse(Long deletedImageId, Instant deleteTime) {
        this.deletedImageId = deletedImageId;
        this.deleteTime = deleteTime;
    }

    public Long getDeletedImageId() {
        return deletedImageId;
    }

    public void setDeletedImageId(Long deletedImageId) {
        this.deletedImageId = deletedImageId;
    }

    public Instant getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(Instant deleteTime) {
        this.deleteTime = deleteTime;
    }
}