package mx.mrw.chattodolist.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import mx.mrw.chattodolist.exception.ApiException;
import mx.mrw.chattodolist.exception.GlobalExceptionHandler;
import mx.mrw.chattodolist.service.MediaStorageService;

class ChatMediaControllerTest {

    private MockMvc mockMvc;
    private MediaStorageService mediaStorageService;

    @BeforeEach
    void setUp() {
        mediaStorageService = Mockito.mock(MediaStorageService.class);
        ChatMediaController controller = new ChatMediaController(mediaStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void uploadShouldReturnMediaPayload() throws Exception {
        when(mediaStorageService.store(any()))
            .thenReturn(new MediaStorageService.MediaUploadResult(
                    "u/2026/02/abc.webp",
                    "http://localhost/media/u/2026/02/abc.webp",
                    "image/webp",
                    1234L));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.webp",
                "image/webp",
                "RIFFxxxxWEBP".getBytes());

        mockMvc.perform(multipart("/api/chat/media/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaPath").value("u/2026/02/abc.webp"))
            .andExpect(jsonPath("$.url").value("http://localhost/media/u/2026/02/abc.webp"))
            .andExpect(jsonPath("$.mime").value("image/webp"))
            .andExpect(jsonPath("$.size").value(1234));
    }

    @Test
    void uploadShouldReturn413WhenTooLarge() throws Exception {
        when(mediaStorageService.store(any()))
            .thenThrow(new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "File exceeds upload limit"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 });

        mockMvc.perform(multipart("/api/chat/media/upload").file(file))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error").value("MEDIA_TOO_LARGE"));
    }

    @Test
    void uploadShouldReturn415WhenMimeUnsupported() throws Exception {
        when(mediaStorageService.store(any()))
            .thenThrow(new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_UNSUPPORTED_TYPE", "Unsupported media type"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.svg",
                "image/svg+xml",
                "<svg/>".getBytes());

        mockMvc.perform(multipart("/api/chat/media/upload").file(file))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error").value("MEDIA_UNSUPPORTED_TYPE"));
    }

    @Test
    void capabilitiesShouldExposeMediaConfig() throws Exception {
        when(mediaStorageService.capabilities())
            .thenReturn(new MediaStorageService.MediaCapabilities(true, 10, "/media"));

        mockMvc.perform(get("/api/chat/media/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.maxUploadMb").value(10))
            .andExpect(jsonPath("$.publicPath").value("/media"));
    }
}
