package com.local.pp_backen.controller;

import com.local.pp_backen.service.MediaStorageService;
import com.local.pp_backen.service.MediaUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Serves figures and page renders. Public, because quiz pages embed them directly. */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaStorageService storage;
    private final MediaUploadService uploads;

    public MediaController(MediaStorageService storage, MediaUploadService uploads) {
        this.storage = storage;
        this.uploads = uploads;
    }

    /**
     * Hand-uploaded images, served from the database. Mapped ahead of the
     * catch-all below because Spring prefers the more specific pattern.
     */
    @GetMapping("/db/{id}")
    public ResponseEntity<byte[]> fromDatabase(@PathVariable String id) {
        return uploads.find(id)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                        .body(file.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) throws IOException {
        String full = request.getRequestURI();
        String relative = full.substring(full.indexOf("/api/media/") + "/api/media/".length());

        Path file;
        try {
            file = storage.resolve(relative);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(contentType != null
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new UrlResource(file.toUri()));
    }
}
