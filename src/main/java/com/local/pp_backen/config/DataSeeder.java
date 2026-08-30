package com.local.pp_backen.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.local.pp_backen.entity.*;
import com.local.pp_backen.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the exam catalogue and an administrator account on first start.
 *
 * <p>Every step is idempotent, so restarting the app never duplicates data.
 * To add another past paper, drop its JSON into {@code resources/data/} and add
 * one more {@link SeedPaper} entry below.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String SUBJECT_NAME = "Pharmacy";
    private static final String SUBJECT_DESCRIPTION =
            "Pharmacy practice, pharmacology, pharmaceutics and the basic sciences behind them.";

    private static final String EXAM_TITLE = "External Pharmacy Exam";
    private static final String EXAM_DESCRIPTION =
            "Ceylon Medical College Council External Pharmacists' Examination \u2014 "
            + "past papers with bilingual (English & Sinhala) answer discussions.";

    /** A past paper to seed: which JSON file, and how it should appear in the catalogue. */
    private record SeedPaper(String title, int year, String description, String resource) {
    }

    private static final List<SeedPaper> PAPERS = List.of(
            new SeedPaper(
                    "2020 Paper",
                    2020,
                    "December 2020 MCQ paper \u2014 50 questions with full English and Sinhala explanations.",
                    "data/questions-2020.json")
    );

    private final ExamRepository examRepository;
    private final PaperRepository paperRepository;
    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(ExamRepository examRepository,
                      PaperRepository paperRepository,
                      QuestionRepository questionRepository,
                      SubjectRepository subjectRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.admin.username:admin}") String adminUsername,
                      @Value("${app.admin.email:admin@example.com}") String adminEmail,
                      @Value("${app.admin.password:admin123}") String adminPassword) {
        this.examRepository = examRepository;
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedCatalogue();
    }

    private void seedAdmin() {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.info("Admin account '{}' already exists.", adminUsername);
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role("ADMIN")
                .provider("LOCAL")
                .displayName("Administrator")
                .build();
        userRepository.save(admin);

        log.info("Created admin account '{}'. Change the password via app.admin.* config.", adminUsername);
    }

    private void seedCatalogue() {
        Subject subject = subjectRepository.findByName(SUBJECT_NAME)
                .orElseGet(() -> subjectRepository.save(Subject.builder()
                        .name(SUBJECT_NAME)
                        .description(SUBJECT_DESCRIPTION)
                        .build()));

        Exam exam = examRepository.findByTitle(EXAM_TITLE)
                .orElseGet(() -> examRepository.save(Exam.builder()
                        .title(EXAM_TITLE)
                        .description(EXAM_DESCRIPTION)
                        .subject(subject)
                        .build()));

        // An exam seeded before subjects existed still needs filing
        if (exam.getSubject() == null) {
            exam.setSubject(subject);
            examRepository.save(exam);
        }

        backfillUnassignedExams();

        for (SeedPaper seed : PAPERS) {
            seedPaper(exam, seed);
        }
    }

    /** Files any pre-existing exam that has no subject, so the picker never shows a gap. */
    private void backfillUnassignedExams() {
        List<Exam> orphans = examRepository.findBySubjectIsNull();
        if (orphans.isEmpty()) return;

        Subject holding = subjectRepository.findByName("Uncategorised")
                .orElseGet(() -> subjectRepository.save(Subject.builder()
                        .name("Uncategorised")
                        .description("Exams that have not been filed under a subject yet. "
                                + "Rename this subject, or move these exams, from the admin catalogue.")
                        .build()));

        for (Exam orphan : orphans) {
            orphan.setSubject(holding);
            examRepository.save(orphan);
        }
        log.info("Filed {} exam(s) without a subject under 'Uncategorised'.", orphans.size());
    }

    private void seedPaper(Exam exam, SeedPaper seed) {
        boolean alreadyPresent = paperRepository.findByExamId(exam.getId()).stream()
                .anyMatch(p -> p.getTitle().equals(seed.title()));

        if (alreadyPresent) {
            log.info("Paper '{}' already seeded. Skipping.", seed.title());
            return;
        }

        Paper paper = paperRepository.save(Paper.builder()
                .title(seed.title())
                .description(seed.description())
                .year(seed.year())
                .durationMinutes(180)
                .exam(exam)
                .build());

        try (InputStream is = new ClassPathResource(seed.resource()).getInputStream()) {
            List<Map<String, Object>> questionsData =
                    objectMapper.readValue(is, new TypeReference<>() {});

            for (Map<String, Object> qData : questionsData) {
                questionRepository.save(toQuestion(qData, paper));
            }

            log.info("Seeded paper '{}' with {} questions.", seed.title(), questionsData.size());
        } catch (Exception e) {
            log.error("Failed to seed paper '{}' from {}: {}", seed.title(), seed.resource(), e.getMessage(), e);
            throw new IllegalStateException("Data seeding failed for " + seed.resource(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Question toQuestion(Map<String, Object> qData, Paper paper) {
        Question question = new Question();
        question.setType((String) qData.get("type"));
        question.setStem((String) qData.get("stem"));
        question.setStemSi((String) qData.get("stem_si"));
        question.setNote((String) qData.get("note"));
        question.setNoteSi((String) qData.get("note_si"));
        question.setImageUrl((String) qData.get("image"));
        question.setExplanation((String) qData.get("exp"));
        question.setExplanationSi((String) qData.get("exp_si"));
        question.setPaper(paper);
        question.setOptions(new ArrayList<>());

        List<Map<String, Object>> optionsData = (List<Map<String, Object>>) qData.get("options");
        if (optionsData != null) {
            for (Map<String, Object> optData : optionsData) {
                question.getOptions().add(Option.builder()
                        .label((String) optData.get("L"))
                        .text((String) optData.get("text"))
                        .textSi((String) optData.get("text_si"))
                        .correct(Boolean.TRUE.equals(optData.get("correct")))
                        .explanationEn((String) optData.get("exp"))
                        .explanationSi((String) optData.get("exp_si"))
                        .question(question)
                        .build());
            }
        }
        return question;
    }
}
