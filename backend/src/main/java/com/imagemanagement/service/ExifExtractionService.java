package com.imagemanagement.service;

import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import java.nio.file.Path;
import java.util.Optional;

public interface ExifExtractionService {

    Optional<ExifData> extract(Path imagePath, Image image);
}
