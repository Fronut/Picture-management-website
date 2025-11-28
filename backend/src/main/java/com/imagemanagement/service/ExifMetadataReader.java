package com.imagemanagement.service;

import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import java.io.IOException;
import java.nio.file.Path;

public interface ExifMetadataReader {

    Metadata readMetadata(Path path) throws ImageProcessingException, IOException;
}
