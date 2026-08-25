package com.local.pp_backen.repository;

import com.local.pp_backen.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    long countByRole(String role);
    long countByEnabledTrue();
    long countByCreatedAtAfter(LocalDateTime since);
    long countByLastLoginAtAfter(LocalDateTime since);
    long countByLastLoginAtIsNull();

    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);
}
