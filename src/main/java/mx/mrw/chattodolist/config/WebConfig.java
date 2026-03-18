package mx.mrw.chattodolist.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import mx.mrw.chattodolist.security.FeedbackAuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppCorsProperties appCorsProperties;
    private final AppMediaProperties appMediaProperties;
    private final FeedbackAuthInterceptor feedbackAuthInterceptor;

    public WebConfig(
            AppCorsProperties appCorsProperties,
            AppMediaProperties appMediaProperties,
            FeedbackAuthInterceptor feedbackAuthInterceptor) {
        this.appCorsProperties = appCorsProperties;
        this.appMediaProperties = appMediaProperties;
        this.feedbackAuthInterceptor = feedbackAuthInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(appCorsProperties.getAllowedOrigins().toArray(String[]::new))
            .allowedMethods(appCorsProperties.getAllowedMethods().toArray(String[]::new))
            .allowedHeaders(appCorsProperties.getAllowedHeaders().toArray(String[]::new))
            .exposedHeaders(appCorsProperties.getExposedHeaders().toArray(String[]::new))
            .allowCredentials(appCorsProperties.isAllowCredentials());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(feedbackAuthInterceptor)
            .addPathPatterns("/api/feedback-chat", "/api/feedback-chat/**", "/api/chat/media/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String publicPath = normalizePublicPath(appMediaProperties.getPublicPath());
        String resourceLocation = normalizeResourceLocation(appMediaProperties.getRoot());
        registry.addResourceHandler(publicPath + "/**")
            .addResourceLocations(resourceLocation);
    }

    private String normalizePublicPath(String value) {
        if (value == null || value.isBlank()) {
            return "/media";
        }
        String normalized = value.startsWith("/") ? value : "/" + value;
        return normalized.replaceAll("/+$", "");
    }

    private String normalizeResourceLocation(String root) {
        String location = Path.of(root == null || root.isBlank() ? "/data/uploads" : root)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
