package com.local.pp_backen.repository;

import com.local.pp_backen.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByName(String name);
    Boolean existsByName(String name);
    Boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
