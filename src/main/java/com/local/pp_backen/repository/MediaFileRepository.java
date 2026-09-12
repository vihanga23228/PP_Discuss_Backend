package com.local.pp_backen.repository;

import com.local.pp_backen.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, String> {
}
