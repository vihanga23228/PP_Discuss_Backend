package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    /**
     * {@code toResponse} always reads paper -> exam -> subject; fetch-joining them here means
     * that chain is never lazily loaded one round trip at a time while building a paper list.
     */
    @Query("SELECT p FROM Paper p LEFT JOIN FETCH p.exam e LEFT JOIN FETCH e.subject "
            + "WHERE p.exam.id = :examId")
    List<Paper> findByExamId(@Param("examId") Long examId);

    @Query("SELECT p FROM Paper p LEFT JOIN FETCH p.exam e LEFT JOIN FETCH e.subject "
            + "WHERE p.id = :id AND p.exam.id = :examId")
    Optional<Paper> findByIdAndExamId(@Param("id") Long id, @Param("examId") Long examId);

    Boolean existsByTitleAndExamId(String title, Long examId);

    /** Cheap aggregate counts for catalogue listings — no entity hydration, just COUNT(*). */
    long countByExamId(Long examId);

    @Query("SELECT COUNT(p) FROM Paper p WHERE p.exam.subject.id = :subjectId")
    long countBySubjectId(@Param("subjectId") Long subjectId);
}
