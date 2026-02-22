package mx.mrw.chattodolist.api.dto;

public record MediaCapabilitiesResponse(
        boolean enabled,
        int maxUploadMb,
        String publicPath) {
}
