package com.imagemanagement.config;

import com.imagemanagement.entity.enums.ThumbnailSizeType;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.thumbnail")
public class ThumbnailProperties {

    @NotBlank
    private String baseDir = "./thumbnails";

    @Valid
    private List<Preset> presets = new ArrayList<>();

    @PostConstruct
    void applyDefaults() {
        if (presets.isEmpty()) {
            presets.add(new Preset(ThumbnailSizeType.SMALL, 256, 256));
            presets.add(new Preset(ThumbnailSizeType.MEDIUM, 512, 512));
            presets.add(new Preset(ThumbnailSizeType.LARGE, 1024, 1024));
        } else {
            var seen = EnumSet.noneOf(ThumbnailSizeType.class);
            for (Preset preset : presets) {
                if (!seen.add(preset.getType())) {
                    throw new IllegalStateException("Duplicate thumbnail preset for type " + preset.getType());
                }
            }
        }
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public List<Preset> getPresets() {
        return presets;
    }

    public void setPresets(List<Preset> presets) {
        this.presets = presets;
    }

    public static class Preset {

        @NotNull
        private ThumbnailSizeType type;

        @Min(1)
        private int width;

        @Min(1)
        private int height;

        public Preset() {
        }

        public Preset(ThumbnailSizeType type, int width, int height) {
            this.type = type;
            this.width = width;
            this.height = height;
        }

        public ThumbnailSizeType getType() {
            return type;
        }

        public void setType(ThumbnailSizeType type) {
            this.type = type;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
}
