package mx.mrw.chattodolist.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import mx.mrw.chattodolist.api.dto.MediaCapabilitiesResponse;
import mx.mrw.chattodolist.api.dto.MediaUploadResponse;
import mx.mrw.chattodolist.service.MediaStorageService;

@RestController
@RequestMapping("/api/chat/media")
public class ChatMediaController {

    private final MediaStorageService mediaStorageService;

    public ChatMediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public MediaUploadResponse upload(@NotNull @RequestPart("file") MultipartFile file) {
        MediaStorageService.MediaUploadResult result = mediaStorageService.store(file);
        return new MediaUploadResponse(result.mediaPath(), result.url(), result.mime(), result.size());
    }

    @GetMapping("/capabilities")
    public MediaCapabilitiesResponse capabilities() {
        MediaStorageService.MediaCapabilities capabilities = mediaStorageService.capabilities();
        return new MediaCapabilitiesResponse(
                capabilities.enabled(),
                capabilities.maxUploadMb(),
                capabilities.publicPath());
    }
}
