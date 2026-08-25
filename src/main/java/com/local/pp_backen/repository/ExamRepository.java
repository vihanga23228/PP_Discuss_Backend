package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByTitle(String title);
    Boolean existsByTitle(String title);
    Boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    List<Exam> findBySubjectId(Long subjectId);
    List<Exam> findBySubjectIsNull();
    long countBySubjectId(Long subjectId);
}
