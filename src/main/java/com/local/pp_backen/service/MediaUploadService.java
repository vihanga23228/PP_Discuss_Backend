package com.local.pp_backen.service;

import com.local.pp_backen.entity.MediaFile;
import com.local.pp_backen.repository.MediaFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.security.SecureRandom;

/** Stores hand-uploaded question screenshots in the database. */
@Service
public class MediaUploadService {

    /** Served under this prefix, which SecurityConfig leaves public. */
    public static final String URL_PREFIX = "/api/media/db/";

    /** Comfortably fits a full-page screenshot without letting a stray PDF through. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final List<String> ALLOWED =
            List.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final MediaFileRepository repository;
    private final SecureRandom random = new SecureRandom();

    public MediaUploadService(MediaFileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("That image is larger than 5 MB. Crop it and try again.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only PNG, JPEG, WebP or GIF images can be uploaded.");
        }

        byte[] id = new byte[16];
        random.nextBytes(id);

        MediaFile stored = MediaFile.builder()
                .id(HexFormat.of().formatHex(id))
                .contentType(contentType.toLowerCase())
                .originalName(file.getOriginalFilename())
                .sizeBytes(file.getSize())
                .data(file.getBytes())
                .build();

        repository.save(stored);
        return URL_PREFIX + stored.getId();
    }

    @Transactional(readOnly = true)
    public Optional<MediaFile> find(String id) {
        return repository.findById(id);
    }
}
