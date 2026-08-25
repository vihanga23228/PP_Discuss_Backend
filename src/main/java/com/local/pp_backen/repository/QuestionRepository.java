package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByPaperId(Long paperId);

    /** Questions inherit their subject through paper -> exam -> subject. */
    @Query("SELECT q FROM Question q WHERE q.paper.exam.subject.id = :subjectId")
    List<Question> findBySubjectId(@Param("subjectId") Long subjectId);

    @Query("SELECT q FROM Question q WHERE q.paper.id = :paperId AND q.paper.exam.subject.id = :subjectId")
    List<Question> findByPaperIdAndSubjectId(@Param("paperId") Long paperId,
                                             @Param("subjectId") Long subjectId);

    @Query(value = "SELECT * FROM questions WHERE paper_id = :paperId ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomByPaperId(@Param("paperId") Long paperId, @Param("limit") int limit);

    long countByPaperId(Long paperId);
}
