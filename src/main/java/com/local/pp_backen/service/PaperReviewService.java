package com.local.pp_backen.service;

import com.local.pp_backen.dto.admin.PaperReviewResponse;
import com.local.pp_backen.entity.Option;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.Question;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Works out which questions in a paper a person still needs to fix by hand. */
@Service
@Transactional(readOnly = true)
public class PaperReviewService {

    /**
     * The fingerprint of UTF-8 text that was decoded as Latin-1 somewhere: a lead
     * byte rendered as a Latin-1 character followed by continuation bytes, or a
     * bare C1 control that no real text contains. Deliberately not "any character
     * above ASCII", because × ² ° µ and Sinhala are all perfectly legitimate.
     */
    private static final Pattern MOJIBAKE = Pattern.compile(
            "[\\u0080-\\u009f]"
                    + "|[\\u00c2-\\u00df][\\u0080-\\u00bf\\u20ac\\u0192\\u2026\\u2020\\u02c6\\u0160\\u0152\\u2018\\u2019\\u201c\\u201d\\u2022\\u2013\\u2014\\u2122\\u0161\\u0153\\u0178]"
                    + "|[\\u00e0-\\u00ef][\\u0080-\\u00bf\\u20ac\\u0192\\u2026\\u2020\\u02c6\\u0160\\u0152\\u2018\\u2019\\u201c\\u201d\\u2022\\u2013\\u2014\\u2122\\u0161\\u0153\\u0178]{2}");

    /** Figures cropped by the old importer live on a disk that redeploys wipe. */
    private static final String EPHEMERAL_MEDIA = "/api/media/job-";

    private final PaperRepository paperRepository;
    private final QuestionRepository questionRepository;

    public PaperReviewService(PaperRepository paperRepository, QuestionRepository questionRepository) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
    }

    public PaperReviewResponse review(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", paperId));

        List<Question> questions = questionRepository.findByPaperId(paperId);

        // Whether to expect Sinhala at all is a property of the paper, not of one
        // question. Several papers were deliberately transcribed in English only,
        // and flagging all fifty for missing Sinhala buries the real problems.
        // Only call it missing when most of the paper does have it.
        long withSinhala = questions.stream()
                .filter(q -> StringUtils.hasText(q.getStemSi()))
                .count();
        boolean bilingual = !questions.isEmpty() && withSinhala * 2 > questions.size();

        List<PaperReviewResponse.QuestionFlags> flagged = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            List<String> reasons = reasonsFor(q, bilingual);
            if (reasons.isEmpty()) continue;

            flagged.add(PaperReviewResponse.QuestionFlags.builder()
                    .questionId(q.getId())
                    .number(i + 1)
                    .type(q.getType())
                    .preview(preview(q.getStem()))
                    .reasons(reasons)
                    .build());
        }

        return PaperReviewResponse.builder()
                .paperId(paper.getId())
                .paperTitle(paper.getTitle())
                .questionCount(questions.size())
                .needsAttention(flagged.size())
                .questions(flagged)
                .build();
    }

    private List<String> reasonsFor(Question q, boolean bilingual) {
        List<String> reasons = new ArrayList<>();
        List<Option> options = q.getOptions();
        boolean trueFalse = "tf".equals(q.getType());

        long correct = options.stream().filter(o -> Boolean.TRUE.equals(o.getCorrect())).count();

        // The one that actually stops a student scoring the question.
        if (!trueFalse && correct == 0) {
            reasons.add("No correct answer marked");
        }
        if (!trueFalse && correct > 1 && "single".equals(q.getType())) {
            reasons.add(correct + " answers marked correct on a single-answer question");
        }
        if (options.isEmpty()) {
            reasons.add("No options");
        }

        long blank = options.stream()
                .filter(o -> !StringUtils.hasText(o.getText()) && !StringUtils.hasText(o.getImageUrl()))
                .count();
        if (blank > 0) {
            reasons.add(blank + " option" + (blank == 1 ? "" : "s") + " have no text");
        }

        if (!StringUtils.hasText(q.getStem())) {
            reasons.add("No question text");
        }
        if (bilingual && !StringUtils.hasText(q.getStemSi())) {
            reasons.add("No Sinhala text");
        }

        // Points at a figure the host's disk no longer has.
        if (q.getImageUrl() != null && q.getImageUrl().startsWith(EPHEMERAL_MEDIA)) {
            reasons.add("Figure was lost on redeploy — re-attach the screenshot");
        }

        if (damaged(q.getStem()) || damaged(q.getStemSi())
                || options.stream().anyMatch(o -> damaged(o.getText()) || damaged(o.getTextSi()))) {
            reasons.add("Text looks encoding-damaged");
        }

        return reasons;
    }

    private static boolean damaged(String value) {
        return value != null && MOJIBAKE.matcher(value).find();
    }

    private static String preview(String stem) {
        if (stem == null) return "";
        String flat = stem.replaceAll("\\s+", " ").trim();
        return flat.length() <= 90 ? flat : flat.substring(0, 89) + "…";
    }
}
