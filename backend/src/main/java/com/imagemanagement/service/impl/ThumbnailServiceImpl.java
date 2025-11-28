package com.imagemanagement.service.impl;

import com.imagemanagement.config.ThumbnailProperties;
import com.imagemanagement.config.ThumbnailProperties.Preset;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.Thumbnail;
import com.imagemanagement.service.ThumbnailService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class ThumbnailServiceImpl implements ThumbnailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailServiceImpl.class);

    private final ThumbnailProperties properties;
    private final Path rootDirectory;

    public ThumbnailServiceImpl(ThumbnailProperties properties) {
        this.properties = properties;
        this.rootDirectory = Paths.get(properties.getBaseDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize thumbnail directory", ex);
        }
    }

    @Override
    public void generateThumbnails(Image image) {
        if (image == null || image.getUser() == null || !StringUtils.hasText(image.getFilePath())) {
            return;
        }
        if (CollectionUtils.isEmpty(properties.getPresets())) {
            return;
        }

        for (Preset preset : properties.getPresets()) {
            try {
                Path outputPath = resolveOutputPath(image, preset.getType().name());
                Files.createDirectories(outputPath.getParent());

                Thumbnails.of(new File(image.getFilePath()))
                        .size(preset.getWidth(), preset.getHeight())
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .toFile(outputPath.toFile());

                Thumbnail thumbnail = new Thumbnail();
                thumbnail.setSizeType(preset.getType());
                thumbnail.setFilePath(normalizePath(outputPath));
                thumbnail.setFileSize((int) Math.min(Integer.MAX_VALUE, Files.size(outputPath)));

                BufferedImage bufferedImage = ImageIO.read(outputPath.toFile());
                if (bufferedImage != null) {
                    thumbnail.setWidth(bufferedImage.getWidth());
                    thumbnail.setHeight(bufferedImage.getHeight());
                } else {
                    thumbnail.setWidth(preset.getWidth());
                    thumbnail.setHeight(preset.getHeight());
                }

                image.addThumbnail(thumbnail);
            } catch (IOException ex) {
                LOGGER.warn("Thumbnail generation failed for image {} preset {}: {}", image.getId(), preset.getType(), ex.getMessage());
            }
        }
    }

    private Path resolveOutputPath(Image image, String presetName) {
        String baseName = FilenameUtils.getBaseName(image.getStoredFilename());
        String filename = baseName + "_" + presetName.toLowerCase(Locale.ROOT) + ".jpg";
        Path userDirectory = rootDirectory.resolve(String.valueOf(image.getUser().getId()));
        Path presetDirectory = userDirectory.resolve(presetName.toLowerCase(Locale.ROOT));
        return presetDirectory.resolve(filename).normalize();
    }

    private String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
