package com.endangered.species.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves a species image from the Wikipedia summary API and memoises the
 * result, replacing the old {@code image_cache.json} file cache.
 */
@Service
public class WikiImageService {

    private static final Logger log = LoggerFactory.getLogger(WikiImageService.class);

    private static final Map<String, String> NAME_REPLACEMENTS = Map.of(
            "bengal tiger", "tiger",
            "indian leopard", "leopard",
            "wild dog", "dhole",
            "striped hyena", "hyena",
            "indian wolf", "wolf");

    private final HttpClient httpClient;
    private final Duration timeout;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public WikiImageService(@Value("${app.wiki.timeout-ms:3000}") long timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String imageFor(String animalName) {
        String key = cacheKey(animalName);
        if (key == null) {
            return "";
        }
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://en.wikipedia.org/api/rest_v1/page/summary/" + key))
                    .timeout(timeout)
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String url = extractThumbnail(response.body());
                if (url != null) {
                    cache.put(key, url);
                    return url;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Wikipedia lookup failed for {}: {}", key, e.getMessage());
        }
        return "";
    }

    static String cacheKey(String animalName) {
        if (animalName == null) {
            return null;
        }
        String name = animalName.toLowerCase(Locale.ROOT).trim();
        int parenthesis = name.indexOf('(');
        if (parenthesis >= 0) {
            name = name.substring(0, parenthesis).trim();
        }
        name = NAME_REPLACEMENTS.getOrDefault(name, name);
        return name.replace(' ', '_');
    }

    /** Minimal extraction of {@code thumbnail.source}; avoids pulling in a JSON parser dependency. */
    private static String extractThumbnail(String body) {
        String marker = "\"thumbnail\"";
        int thumbnail = body.indexOf(marker);
        if (thumbnail < 0) {
            return null;
        }
        int sourceKey = body.indexOf("\"source\"", thumbnail);
        if (sourceKey < 0) {
            return null;
        }
        int start = body.indexOf('"', body.indexOf(':', sourceKey) + 1);
        if (start < 0) {
            return null;
        }
        int end = body.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return body.substring(start + 1, end);
    }
}
