package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OptionRepository extends JpaRepository<Option, Long> {
    List<Option> findByQuestionId(Long questionId);
}
