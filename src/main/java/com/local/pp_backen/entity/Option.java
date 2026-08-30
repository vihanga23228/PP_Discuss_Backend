package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "options", indexes = @Index(name = "idx_options_question_id", columnList = "question_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Label such as A, B, C, D, E */
    @Column(nullable = false, length = 5)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** The same option in Sinhala. Null for numeric options that need no translation. */
    @Column(name = "text_si", columnDefinition = "TEXT")
    private String textSi;

    /** Used when the option itself is a graph rather than text. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean correct;

    /** English explanation for this option */
    @Column(name = "explanation_en", columnDefinition = "TEXT")
    private String explanationEn;

    /** Sinhala explanation for this option */
    @Column(name = "explanation_si", columnDefinition = "TEXT")
    private String explanationSi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
}
