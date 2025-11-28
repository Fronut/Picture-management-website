package com.imagemanagement.service.impl;

import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.imagemanagement.entity.ExifData;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.User;
import com.imagemanagement.service.ExifMetadataReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExifExtractionServiceImplTest {

    @Test
    void extract_shouldPopulateExifFieldsWhenMetadataExists() throws Exception {
        Metadata metadata = new Metadata();
        ExifIFD0Directory ifd0Directory = new ExifIFD0Directory();
        ifd0Directory.setString(ExifIFD0Directory.TAG_MAKE, "Canon");
        ifd0Directory.setString(ExifIFD0Directory.TAG_MODEL, "EOS R6");
        metadata.addDirectory(ifd0Directory);

        ExifSubIFDDirectory subIFDDirectory = new ExifSubIFDDirectory();
        Date captureDate = Date.from(Instant.parse("2023-08-10T12:34:56Z"));
        subIFDDirectory.setDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, captureDate);
        subIFDDirectory.setString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME, "1/125");
        subIFDDirectory.setString(ExifSubIFDDirectory.TAG_FNUMBER, "f/2.8");
        subIFDDirectory.setInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT, 400);
        subIFDDirectory.setString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH, "50mm");
        metadata.addDirectory(subIFDDirectory);

        ExifMetadataReader metadataReader = path -> metadata;
        ExifExtractionServiceImpl service = new ExifExtractionServiceImpl(metadataReader);

        Image image = new Image();
        image.setUser(new User());

        Optional<ExifData> result = service.extract(Path.of("ignored"), image);

        assertThat(result).isPresent();
        ExifData exifData = result.get();
        assertThat(exifData.getCameraMake()).isEqualTo("Canon");
        assertThat(exifData.getCameraModel()).isEqualTo("EOS R6");
        assertThat(exifData.getExposureTime()).isEqualTo("1/125");
        assertThat(exifData.getIsoSpeed()).isEqualTo(400);
        assertThat(exifData.getFocalLength()).isEqualTo("50mm");
        assertThat(exifData.getTakenTime()).isNotNull();
    }

    @Test
    void extract_shouldReturnEmptyWhenReaderFails() throws Exception {
        ExifMetadataReader metadataReader = path -> { throw new ImageProcessingException("boom"); };
        ExifExtractionServiceImpl service = new ExifExtractionServiceImpl(metadataReader);

        Image image = new Image();
        image.setUser(new User());

        Optional<ExifData> result = service.extract(Path.of("ignored"), image);

        assertThat(result).isNotPresent();
    }
}
