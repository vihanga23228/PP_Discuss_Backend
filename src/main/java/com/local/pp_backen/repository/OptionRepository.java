package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OptionRepository extends JpaRepository<Option, Long> {
    List<Option> findByQuestionId(Long questionId);

    /**
     * Whether any recorded answer selected this option. Deleting one that is
     * referenced would fail on the foreign key, and rewriting somebody's
     * finished attempt to make room for an edit is not ours to do silently.
     */
    @Query("SELECT COUNT(a) > 0 FROM QuizAnswer a JOIN a.selectedOptions o WHERE o.id = :optionId")
    boolean isAnswered(@Param("optionId") Long optionId);
}
