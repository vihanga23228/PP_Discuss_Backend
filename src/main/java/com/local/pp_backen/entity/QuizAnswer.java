package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "quiz_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** True only when every mark on this question was earned */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /** Marks scored on this question. True/false questions carry one mark per statement. */
    @Column(name = "marks_earned")
    private Integer marksEarned;

    /** Marks that were available on this question */
    @Column(name = "marks_available")
    private Integer marksAvailable;

    /**
     * Options the user answered affirmatively: picked (single/multi) or marked True (tf).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "quiz_answer_selections",
        joinColumns = @JoinColumn(name = "answer_id"),
        inverseJoinColumns = @JoinColumn(name = "option_id")
    )
    @Builder.Default
    private Set<Option> selectedOptions = new HashSet<>();
}
