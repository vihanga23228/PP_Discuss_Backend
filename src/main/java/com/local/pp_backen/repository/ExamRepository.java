package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByTitle(String title);
    Boolean existsByTitle(String title);
    Boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    List<Exam> findBySubjectId(Long subjectId);
    List<Exam> findBySubjectIsNull();
    long countBySubjectId(Long subjectId);

    /** One row per exam with its paper/question counts, in a single query. */
    @Query("SELECT e.id AS examId, "
            + "COUNT(DISTINCT p.id) AS paperCount, "
            + "COUNT(DISTINCT q.id) AS questionCount "
            + "FROM Exam e LEFT JOIN e.papers p LEFT JOIN p.questions q "
            + "GROUP BY e.id")
    List<ExamCounts> countsPerExam();

    @Query("SELECT e.id AS examId, "
            + "COUNT(DISTINCT p.id) AS paperCount, "
            + "COUNT(DISTINCT q.id) AS questionCount "
            + "FROM Exam e LEFT JOIN e.papers p LEFT JOIN p.questions q "
            + "WHERE e.id = :examId "
            + "GROUP BY e.id")
    ExamCounts countsForExam(@Param("examId") Long examId);

    interface ExamCounts {
        Long getExamId();
        Long getPaperCount();
        Long getQuestionCount();
    }
}
