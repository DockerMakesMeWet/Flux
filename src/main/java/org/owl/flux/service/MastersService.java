package org.owl.flux.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class MastersService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    public static final String MASTERS_ENDPOINT = "https://flux.wildowl.tech/masters.txt";

    private final Logger logger;
    private final Executor executor;
    private final HttpClient httpClient;
    private final AtomicReference<Set<String>> cachedMasters = new AtomicReference<>(Set.of());

    public MastersService(Logger logger, Executor executor) {
        this.logger = logger;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    public CompletableFuture<Boolean> refreshAsync() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MASTERS_ENDPOINT))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("masters.txt endpoint returned status " + response.statusCode());
                    }
                    Set<String> parsed = parseContent(response.body());
                    cachedMasters.set(parsed);
                    logger.info("Flux masters list refreshed with {} entries.", parsed.size());
                    return true;
                }, executor)
                .exceptionally(error -> {
                    logger.error("Flux failed to refresh masters list; keeping existing cache.", error);
                    return false;
                });
    }

    public boolean isMaster(String username) {
        if (username == null) {
            return false;
        }
        return cachedMasters.get().contains(normalize(username));
    }

    public Set<String> snapshot() {
        return cachedMasters.get();
    }

    static Set<String> parseContent(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .map(MastersService::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
