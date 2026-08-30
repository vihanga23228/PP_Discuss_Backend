package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByName(String name);
    Boolean existsByName(String name);
    Boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** One row per subject with its exam/paper/question counts, in a single query. */
    @Query("SELECT s.id AS subjectId, "
            + "COUNT(DISTINCT e.id) AS examCount, "
            + "COUNT(DISTINCT p.id) AS paperCount, "
            + "COUNT(DISTINCT q.id) AS questionCount "
            + "FROM Subject s LEFT JOIN s.exams e LEFT JOIN e.papers p LEFT JOIN p.questions q "
            + "GROUP BY s.id")
    List<SubjectCounts> countsPerSubject();

    @Query("SELECT s.id AS subjectId, "
            + "COUNT(DISTINCT e.id) AS examCount, "
            + "COUNT(DISTINCT p.id) AS paperCount, "
            + "COUNT(DISTINCT q.id) AS questionCount "
            + "FROM Subject s LEFT JOIN s.exams e LEFT JOIN e.papers p LEFT JOIN p.questions q "
            + "WHERE s.id = :subjectId "
            + "GROUP BY s.id")
    SubjectCounts countsForSubject(@Param("subjectId") Long subjectId);

    interface SubjectCounts {
        Long getSubjectId();
        Long getExamCount();
        Long getPaperCount();
        Long getQuestionCount();
    }
}
