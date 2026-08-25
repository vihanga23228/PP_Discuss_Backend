package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {
    List<Paper> findByExamId(Long examId);
    Optional<Paper> findByIdAndExamId(Long id, Long examId);
    Boolean existsByTitleAndExamId(String title, Long examId);
}
