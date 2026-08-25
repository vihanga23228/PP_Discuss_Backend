package com.local.pp_backen.repository;

import com.local.pp_backen.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {
    List<QuizAnswer> findByAttemptId(Long attemptId);
}
