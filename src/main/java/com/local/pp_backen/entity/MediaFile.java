package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An image uploaded by hand in the admin console, stored in the database rather
 * than on disk.
 *
 * <p>The host's filesystem does not survive a redeploy: every figure the PDF
 * importer cropped for the 2025 Physics paper was silently lost that way, and
 * those questions still point at URLs that 404. Anything a person takes the
 * trouble to upload has to outlive the container, and the database is the only
 * durable store this deployment has.
 */
@Entity
@Table(name = "media_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {

    /** A random id, so the served URL gives away nothing about other uploads. */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /**
     * Deliberately not @Lob: Hibernate 6 maps a @Lob byte[] to a PostgreSQL large
     * object (oid), which needs the large-object API and a transaction just to
     * read. A plain byte[] maps to bytea and behaves like any other column.
     */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
