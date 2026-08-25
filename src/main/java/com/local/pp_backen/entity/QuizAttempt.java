package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    /** Number of questions answered completely correctly */
    @Column(name = "correct_count")
    private Integer correctCount;

    /** Marks available across the paper (true/false questions carry one mark per statement) */
    @Column(name = "total_marks")
    private Integer totalMarks;

    /** Marks the user actually earned */
    @Column(name = "earned_marks")
    private Integer earnedMarks;

    @Column(name = "score_pct")
    private Double scorePct;

    @Column(name = "started_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuizAnswer> answers = new ArrayList<>();
}
