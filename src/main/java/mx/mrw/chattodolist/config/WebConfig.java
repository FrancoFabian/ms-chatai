package mx.mrw.chattodolist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import mx.mrw.chattodolist.security.FeedbackAuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppCorsProperties appCorsProperties;
    private final FeedbackAuthInterceptor feedbackAuthInterceptor;

    public WebConfig(AppCorsProperties appCorsProperties, FeedbackAuthInterceptor feedbackAuthInterceptor) {
        this.appCorsProperties = appCorsProperties;
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
}
