package mx.mrw.chattodolist.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import mx.mrw.chattodolist.config.AppMediaProperties;
import mx.mrw.chattodolist.exception.ApiException;

@Service
public class MediaStorageService {

    private static final int PNG_SIGNATURE_SIZE = 8;
    private static final int MAX_SIGNATURE_SIZE = 12;

    private final AppMediaProperties mediaProperties;

    public MediaStorageService(AppMediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    public MediaUploadResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_FILE", "File is required");
        }

        long maxBytes = Math.max(1, mediaProperties.getMaxUploadMb()) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "File exceeds upload limit");
        }

        byte[] signature = readSignature(file);
        String mime = detectMime(signature);
        String extension = extensionForMime(mime);

        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String mediaPath = "u/%d/%02d/%s.%s".formatted(
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID().toString(),
                extension);

        Path root = Path.of(mediaProperties.getRoot()).toAbsolutePath().normalize();
        Path destination = root.resolve(mediaPath).normalize();
        if (!destination.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_PATH", "Invalid media path");
        }

        try {
            Files.createDirectories(destination.getParent());
            byte[] bytes = file.getBytes();
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        }
        catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_ERROR", "Unable to persist media file");
        }

        return new MediaUploadResult(
                mediaPath.replace('\\', '/'),
                publicUrl(mediaPath),
                mime,
                file.getSize());
    }

    public MediaCapabilities capabilities() {
        String publicPath = normalizePublicPath(mediaProperties.getPublicPath());
        return new MediaCapabilities(true, Math.max(1, mediaProperties.getMaxUploadMb()), publicPath);
    }

    private byte[] readSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] signature = inputStream.readNBytes(MAX_SIGNATURE_SIZE);
            if (signature.length < 4) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_UNSUPPORTED_TYPE", "Unsupported media type");
            }
            return signature;
        }
        catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_FILE", "Unable to read file");
        }
    }

    private String detectMime(byte[] signature) {
        if (isPng(signature)) {
            return "image/png";
        }
        if (isJpeg(signature)) {
            return "image/jpeg";
        }
        if (isWebp(signature)) {
            return "image/webp";
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_UNSUPPORTED_TYPE", "Unsupported media type");
    }

    private boolean isPng(byte[] signature) {
        if (signature.length < PNG_SIGNATURE_SIZE) {
            return false;
        }
        int[] png = new int[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        for (int index = 0; index < png.length; index++) {
            if ((signature[index] & 0xFF) != png[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isJpeg(byte[] signature) {
        return signature.length >= 3
                && (signature[0] & 0xFF) == 0xFF
                && (signature[1] & 0xFF) == 0xD8
                && (signature[2] & 0xFF) == 0xFF;
    }

    private boolean isWebp(byte[] signature) {
        return signature.length >= 12
                && signature[0] == 'R'
                && signature[1] == 'I'
                && signature[2] == 'F'
                && signature[3] == 'F'
                && signature[8] == 'W'
                && signature[9] == 'E'
                && signature[10] == 'B'
                && signature[11] == 'P';
    }

    private String extensionForMime(String mime) {
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_UNSUPPORTED_TYPE", "Unsupported media type");
        };
    }

    private String publicUrl(String mediaPath) {
        String baseUrl = normalizeBaseUrl(mediaProperties.getPublicBaseUrl());
        String publicPath = normalizePublicPath(mediaProperties.getPublicPath());
        String normalizedMediaPath = mediaPath.replace('\\', '/').replaceAll("^/+", "");
        return "%s%s/%s".formatted(baseUrl, publicPath, normalizedMediaPath);
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://localhost";
        }
        return value.replaceAll("/+$", "");
    }

    private String normalizePublicPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/media";
        }
        String normalized = value.startsWith("/") ? value : "/" + value;
        return normalized.replaceAll("/+$", "");
    }

    public record MediaUploadResult(
            String mediaPath,
            String url,
            String mime,
            long size) {
    }

    public record MediaCapabilities(
            boolean enabled,
            int maxUploadMb,
            String publicPath) {
    }
}
