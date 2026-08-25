package com.local.pp_backen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Writes page renders and cropped figures to disk and hands back the URL the
 * frontend should use. Files live under a single configurable root so the whole
 * store can be pointed at a volume in production.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    /** Everything served from here is public — see SecurityConfig. */
    public static final String URL_PREFIX = "/api/media/";

    private final Path root;

    public MediaStorageService(@Value("${app.media.dir:./media}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Media store at {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the media directory: " + root, e);
        }
    }

    /**
     * @param folder  a grouping such as the extraction job id
     * @param name    file name inside that folder
     * @return the URL path to store on the question or option
     */
    public String write(String folder, String name, byte[] bytes) {
        String safeFolder = sanitise(folder);
        String safeName = sanitise(name);
        Path target = root.resolve(safeFolder).resolve(safeName).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to write outside the media directory");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write media file " + target, e);
        }

        return URL_PREFIX + safeFolder + "/" + safeName;
    }

    /** Resolves a URL path such as "/api/media/job123/q7.png" back to a file. */
    public Path resolve(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to read outside the media directory");
        }
        return target;
    }

    /** Removes everything written under one folder — used when a draft is discarded. */
    public void deleteFolder(String folder) {
        Path target = root.resolve(sanitise(folder)).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) return;

        try (var walk = Files.walk(target)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort — a leftover temp file is not worth failing a request
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not clear media folder {}: {}", target, e.getMessage());
        }
    }

    /**
     * Drops the full-page renders but keeps the cropped figures.
     *
     * <p>Used once a draft has been published: the page images existed only so the
     * reviewer could check the draft against the original, and they are by far the
     * largest files, but the crops are now referenced by live questions.
     */
    public void deletePageRenders(String folder) {
        Path target = root.resolve(sanitise(folder)).normalize();
        if (!target.startsWith(root) || !Files.isDirectory(target)) return;

        try (var listing = Files.list(target)) {
            listing.filter(path -> path.getFileName().toString().startsWith("page-"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not clear page renders in {}: {}", target, e.getMessage());
        }
    }

    private static String sanitise(String value) {
        String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (cleaned.isBlank() || cleaned.contains("..")) {
            throw new IllegalArgumentException("Invalid media path segment: " + value);
        }
        return cleaned;
    }
}
