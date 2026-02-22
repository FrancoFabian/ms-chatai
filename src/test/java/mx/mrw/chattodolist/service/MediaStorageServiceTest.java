package mx.mrw.chattodolist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import mx.mrw.chattodolist.config.AppMediaProperties;
import mx.mrw.chattodolist.exception.ApiException;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldPersistPng() throws Exception {
        MediaStorageService service = new MediaStorageService(mediaProperties(10));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                pngBytes());

        MediaStorageService.MediaUploadResult result = service.store(file);

        assertEquals("image/png", result.mime());
        assertTrue(result.mediaPath().startsWith("u/"));
        assertTrue(result.url().contains("/media/"));
        assertTrue(Files.exists(tempDir.resolve(result.mediaPath())));
    }

    @Test
    void storeShouldFailWhenTooLarge() {
        MediaStorageService service = new MediaStorageService(mediaProperties(1));
        byte[] oversized = new byte[(1024 * 1024) + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        ApiException exception = assertThrows(ApiException.class, () -> service.store(file));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
        assertEquals("MEDIA_TOO_LARGE", exception.getErrorCode());
    }

    @Test
    void storeShouldFailWhenUnsupportedType() {
        MediaStorageService service = new MediaStorageService(mediaProperties(10));
        MockMultipartFile file = new MockMultipartFile("file", "bad.svg", "image/svg+xml", "<svg/>".getBytes());

        ApiException exception = assertThrows(ApiException.class, () -> service.store(file));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
        assertEquals("MEDIA_UNSUPPORTED_TYPE", exception.getErrorCode());
    }

    private AppMediaProperties mediaProperties(int maxMb) {
        AppMediaProperties properties = new AppMediaProperties();
        properties.setRoot(tempDir.toString());
        properties.setPublicBaseUrl("http://localhost");
        properties.setPublicPath("/media");
        properties.setMaxUploadMb(maxMb);
        return properties;
    }

    private byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
        };
    }
}
