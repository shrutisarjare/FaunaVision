package com.endangered.species.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React build out of {@code static/}. A path that is not a real file is a
 * client-side route, so it falls back to index.html and a refresh on /profile reaches the
 * app instead of a 404. Anything with a file extension, and unknown {@code /api} paths,
 * stays a 404 instead of answering with HTML.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallback());
    }

    private static final class SpaFallback extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            // A missing asset must 404 rather than come back as the HTML shell.
            if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                return null;
            }
            // Absent while developing against Vite: the handler then answers 404 as before.
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() ? index : null;
        }
    }
}
