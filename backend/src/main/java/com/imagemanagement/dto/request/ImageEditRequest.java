package com.imagemanagement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ImageEditRequest {

    @Valid
    private CropOperation crop;

    @Valid
    private ToneAdjustment toneAdjustment;

    @AssertTrue(message = "至少需要提供一种编辑操作")
    public boolean isValidOperationSet() {
        return hasAnyOperation();
    }

    public boolean hasAnyOperation() {
        return crop != null || (toneAdjustment != null && toneAdjustment.hasAdjustments());
    }

    public CropOperation getCrop() {
        return crop;
    }

    public void setCrop(CropOperation crop) {
        this.crop = crop;
    }

    public ToneAdjustment getToneAdjustment() {
        return toneAdjustment;
    }

    public void setToneAdjustment(ToneAdjustment toneAdjustment) {
        this.toneAdjustment = toneAdjustment;
    }

    public static class CropOperation {

        @NotNull
        @Min(0)
        private Integer x;

        @NotNull
        @Min(0)
        private Integer y;

        @NotNull
        @Positive
        private Integer width;

        @NotNull
        @Positive
        private Integer height;

        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Integer getY() {
            return y;
        }

        public void setY(Integer y) {
            this.y = y;
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
    }

    public static class ToneAdjustment {

        @DecimalMin(value = "-1.0")
        @DecimalMax(value = "1.0")
        private Double brightness;

        @DecimalMin(value = "-0.9")
        @DecimalMax(value = "1.0")
        private Double contrast;

        @DecimalMin(value = "-1.0")
        @DecimalMax(value = "1.0")
        private Double warmth;

        public Double getBrightness() {
            return brightness;
        }

        public void setBrightness(Double brightness) {
            this.brightness = brightness;
        }

        public Double getContrast() {
            return contrast;
        }

        public void setContrast(Double contrast) {
            this.contrast = contrast;
        }

        public Double getWarmth() {
            return warmth;
        }

        public void setWarmth(Double warmth) {
            this.warmth = warmth;
        }

        public boolean hasAdjustments() {
            return (brightness != null && brightness != 0.0d)
                    || (contrast != null && contrast != 0.0d)
                    || (warmth != null && warmth != 0.0d);
        }
    }
}
