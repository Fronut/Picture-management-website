package com.imagemanagement.service.impl;

import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import com.imagemanagement.service.ExifExtractionService;
import com.imagemanagement.service.ExifMetadataReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExifExtractionServiceImpl implements ExifExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExifExtractionServiceImpl.class);

    private final ExifMetadataReader metadataReader;

    public ExifExtractionServiceImpl(ExifMetadataReader metadataReader) {
        this.metadataReader = metadataReader;
    }

    @Override
    public Optional<ExifData> extract(Path imagePath, Image image) {
        try {
            Metadata metadata = metadataReader.readMetadata(imagePath);
            ExifData exifData = new ExifData();
            exifData.setImage(image);

            boolean hasData = populateCameraInfo(metadata, exifData);
            hasData |= populateExposureInfo(metadata, exifData);
            hasData |= populateGpsInfo(metadata, exifData);

            return hasData ? Optional.of(exifData) : Optional.empty();
        } catch (ImageProcessingException | IOException ex) {
            LOGGER.warn("Failed to extract EXIF metadata from {}: {}", imagePath, ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean populateCameraInfo(Metadata metadata, ExifData exifData) {
        boolean hasData = false;
        ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (directory != null) {
            String make = directory.getString(ExifIFD0Directory.TAG_MAKE);
            if (StringUtils.hasText(make)) {
                exifData.setCameraMake(make);
                hasData = true;
            }
            String model = directory.getString(ExifIFD0Directory.TAG_MODEL);
            if (StringUtils.hasText(model)) {
                exifData.setCameraModel(model);
                hasData = true;
            }
        }
        return hasData;
    }

    private boolean populateExposureInfo(Metadata metadata, ExifData exifData) {
        boolean hasData = false;
        ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (directory == null) {
            return false;
        }
        Date originalDate = directory.getDateOriginal();
        if (originalDate != null) {
            LocalDateTime taken = LocalDateTime.ofInstant(originalDate.toInstant(), ZoneId.systemDefault());
            exifData.setTakenTime(taken);
            hasData = true;
        }
        String exposure = directory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
        if (StringUtils.hasText(exposure)) {
            exifData.setExposureTime(exposure);
            hasData = true;
        }
        String fNumber = directory.getString(ExifSubIFDDirectory.TAG_FNUMBER);
        if (StringUtils.hasText(fNumber)) {
            exifData.setFNumber(fNumber);
            hasData = true;
        }
        Integer iso = directory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
        if (iso != null) {
            exifData.setIsoSpeed(iso);
            hasData = true;
        }
        String focalLength = directory.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
        if (StringUtils.hasText(focalLength)) {
            exifData.setFocalLength(focalLength);
            hasData = true;
        }
        return hasData;
    }

    private boolean populateGpsInfo(Metadata metadata, ExifData exifData) {
        GpsDirectory directory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (directory == null) {
            return false;
        }
        GeoLocation location = directory.getGeoLocation();
        if (location == null || location.isZero()) {
            return false;
        }
        exifData.setLatitude(BigDecimal.valueOf(location.getLatitude()));
        exifData.setLongitude(BigDecimal.valueOf(location.getLongitude()));
        return true;
    }
}
