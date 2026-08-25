package com.local.pp_backen.repository;

import com.local.pp_backen.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId);
    List<QuizAttempt> findByUserIdAndPaperId(Long userId, Long paperId);

    List<QuizAttempt> findByPaperId(Long paperId);

    long countByCompletedAtNotNull();

    @Query("SELECT COALESCE(AVG(a.scorePct), 0) FROM QuizAttempt a WHERE a.completedAt IS NOT NULL")
    double averageScorePct();

    /** One row per user: [userId, completedAttempts, bestScorePct] */
    @Query("""
            SELECT a.user.id, COUNT(a), MAX(a.scorePct)
            FROM QuizAttempt a
            WHERE a.completedAt IS NOT NULL AND a.user.id IN :userIds
            GROUP BY a.user.id
            """)
    List<Object[]> statsForUsers(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT a FROM QuizAttempt a WHERE a.completedAt IS NOT NULL ORDER BY a.completedAt DESC")
    List<QuizAttempt> findRecentCompleted(org.springframework.data.domain.Pageable pageable);
}
