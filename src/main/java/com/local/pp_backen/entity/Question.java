package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions", indexes = @Index(name = "idx_questions_paper_id", columnList = "paper_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Question type: "tf" (true/false), "single" (single best answer), "multi" (multiple select) */
    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    /** The same question in Sinhala. Null when only an English version exists. */
    @Column(name = "stem_si", columnDefinition = "TEXT")
    private String stemSi;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "note_si", columnDefinition = "TEXT")
    private String noteSi;

    /** Diagram that belongs to the question, served from /api/media/… */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Overall explanation for the question (optional, used for single/multi) */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Sinhala overall explanation */
    @Column(name = "explanation_si", columnDefinition = "TEXT")
    private String explanationSi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;


    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("label ASC")
    @Builder.Default
    private List<Option> options = new ArrayList<>();
}
