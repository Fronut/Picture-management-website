package com.imagemanagement.support;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Utility helper that exposes real test images stored under the top-level test/Pictures directory.
 */
public final class TestImageResource {

    private final String filename;
    private final String mimeType;
    private final byte[] bytes;
    private final long size;
    private final int width;
    private final int height;
    private final Path absolutePath;

    private TestImageResource(String filename,
                              String mimeType,
                              byte[] bytes,
                              long size,
                              int width,
                              int height,
                              Path absolutePath) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.size = size;
        this.width = width;
        this.height = height;
        this.absolutePath = absolutePath;
    }

    public static TestImageResource load(String filename) throws IOException {
        Path picturesDir = locatePicturesDirectory();
        Path imagePath = picturesDir.resolve(filename).normalize();
        if (!Files.exists(imagePath)) {
            throw new NoSuchFileException("Test image not found: " + imagePath);
        }
        byte[] data = Files.readAllBytes(imagePath);
        BufferedImage buffered = ImageIO.read(imagePath.toFile());
        if (buffered == null) {
            throw new IOException("Unable to decode image: " + imagePath);
        }
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType == null) {
            mimeType = guessMimeType(filename);
        }
        return new TestImageResource(
                filename,
                mimeType,
                data,
                data.length,
                buffered.getWidth(),
                buffered.getHeight(),
                imagePath);
    }

    public MockMultipartFile asMultipart(String fieldName) {
        String partName = Objects.requireNonNull(fieldName, "fieldName must not be null");
        String originalFilename = Objects.requireNonNull(filename, "filename must not be null");
        String contentType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        return new MockMultipartFile(partName, originalFilename, contentType, bytes.clone());
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public long getSize() {
        return size;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    private static Path locatePicturesDirectory() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve("test").resolve("Pictures");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate test/Pictures directory from " + Path.of("").toAbsolutePath());
    }

    private static String guessMimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ENGLISH);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }
}
