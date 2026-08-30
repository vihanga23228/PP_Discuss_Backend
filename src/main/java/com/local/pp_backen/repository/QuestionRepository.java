package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Fetches every question of a paper together with its options and its
     * paper -> exam -> subject chain in a single round trip. Plain {@code findByPaperId}
     * used to leave {@code options} (and the paper/exam/subject chain) LAZY, so
     * {@code QuestionService.toResponse} triggered one extra SELECT per question — a 50-question
     * paper cost ~51+ round trips. Each fetch join folds that into this one query.
     */
    @Query("SELECT DISTINCT q FROM Question q "
            + "LEFT JOIN FETCH q.options "
            + "LEFT JOIN FETCH q.paper p "
            + "LEFT JOIN FETCH p.exam e "
            + "LEFT JOIN FETCH e.subject "
            + "WHERE q.paper.id = :paperId "
            + "ORDER BY q.id")
    List<Question> findByPaperId(@Param("paperId") Long paperId);

    /** Questions inherit their subject through paper -> exam -> subject. */
    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options "
            + "WHERE q.paper.exam.subject.id = :subjectId ORDER BY q.id")
    List<Question> findBySubjectId(@Param("subjectId") Long subjectId);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options "
            + "WHERE q.paper.id = :paperId AND q.paper.exam.subject.id = :subjectId ORDER BY q.id")
    List<Question> findByPaperIdAndSubjectId(@Param("paperId") Long paperId,
                                             @Param("subjectId") Long subjectId);

    @Query(value = "SELECT * FROM questions WHERE paper_id = :paperId ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomByPaperId(@Param("paperId") Long paperId, @Param("limit") int limit);

    long countByPaperId(Long paperId);

    /** Cheap aggregate counts for catalogue listings — no entity hydration, just COUNT(*). */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.paper.exam.id = :examId")
    long countByExamId(@Param("examId") Long examId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.paper.exam.subject.id = :subjectId")
    long countBySubjectId(@Param("subjectId") Long subjectId);

    /** One row per paper with its question count, for every paper in one exam — a single query. */
    @Query("SELECT q.paper.id AS paperId, COUNT(q) AS questionCount "
            + "FROM Question q WHERE q.paper.exam.id = :examId GROUP BY q.paper.id")
    List<PaperQuestionCount> countsPerPaperForExam(@Param("examId") Long examId);

    interface PaperQuestionCount {
        Long getPaperId();
        Long getQuestionCount();
    }
}
