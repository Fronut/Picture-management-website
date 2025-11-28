package com.imagemanagement.service.impl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.imagemanagement.service.ExifMetadataReader;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DefaultExifMetadataReader implements ExifMetadataReader {

    @Override
    public Metadata readMetadata(Path path) throws ImageProcessingException, IOException {
        return ImageMetadataReader.readMetadata(path.toFile());
    }
}
