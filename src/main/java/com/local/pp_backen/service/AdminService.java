package com.local.pp_backen.service;

import com.local.pp_backen.dto.admin.*;
import com.local.pp_backen.entity.*;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    /** A member counts as active if they have signed in within this many days. */
    private static final int ACTIVE_WINDOW_DAYS = 30;

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final PaperRepository paperRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SubjectRepository subjectRepository;
    private final PaperService paperService;

    public AdminService(UserRepository userRepository,
                        ExamRepository examRepository,
                        PaperRepository paperRepository,
                        QuestionRepository questionRepository,
                        QuizAttemptRepository quizAttemptRepository,
                        SubjectRepository subjectRepository,
                        PaperService paperService) {
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.subjectRepository = subjectRepository;
        this.paperService = paperService;
    }

    // --- dashboard ---------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusDays(ACTIVE_WINDOW_DAYS);
        LocalDateTime weekAgo = now.minusDays(7);

        long total = userRepository.count();
        long enabled = userRepository.countByEnabledTrue();

        AdminStatsResponse.Members members = AdminStatsResponse.Members.builder()
                .total(total)
                .active(userRepository.countByLastLoginAtAfter(monthAgo))
                .activeThisWeek(userRepository.countByLastLoginAtAfter(weekAgo))
                .newThisWeek(userRepository.countByCreatedAtAfter(weekAgo))
                .admins(userRepository.countByRole("ADMIN"))
                .suspended(total - enabled)
                .neverSignedIn(userRepository.countByLastLoginAtIsNull())
                .build();

        AdminStatsResponse.Content content = AdminStatsResponse.Content.builder()
                .subjects(subjectRepository.count())
                .exams(examRepository.count())
                .papers(paperRepository.count())
                .questions(questionRepository.count())
                .build();

        AdminStatsResponse.Activity activity = AdminStatsResponse.Activity.builder()
                .totalAttempts(quizAttemptRepository.count())
                .completedAttempts(quizAttemptRepository.countByCompletedAtNotNull())
                .averageScorePct(round1(quizAttemptRepository.averageScorePct()))
                .build();

        List<User> recent = userRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        return AdminStatsResponse.builder()
                .members(members)
                .content(content)
                .activity(activity)
                .recentSignups(toUserResponses(recent))
                .recentAttempts(recentAttempts())
                .build();
    }

    private List<AdminStatsResponse.RecentAttempt> recentAttempts() {
        return quizAttemptRepository.findRecentCompleted(PageRequest.of(0, 8)).stream()
                .map(a -> AdminStatsResponse.RecentAttempt.builder()
                        .attemptId(a.getId())
                        .username(a.getUser().getUsername())
                        .paperTitle(a.getPaper().getTitle())
                        .earnedMarks(orZero(a.getEarnedMarks()))
                        .totalMarks(orZero(a.getTotalMarks()))
                        .scorePct(round1(a.getScorePct() != null ? a.getScorePct() : 0))
                        .completedAt(a.getCompletedAt() != null
                                ? a.getCompletedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                : null)
                        .build())
                .collect(Collectors.toList());
    }

    // --- members -----------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> users(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = userRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, pageable);

        return PagedResponse.<AdminUserResponse>builder()
                .content(toUserResponses(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * @param actingUsername the signed-in admin, so they cannot lock themselves out
     */
    public AdminUserResponse updateUser(String actingUsername, Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        boolean isSelf = user.getUsername().equals(actingUsername);

        if (request.getRole() != null) {
            String role = request.getRole().trim().toUpperCase();
            if (!role.equals("USER") && !role.equals("ADMIN")) {
                throw new IllegalArgumentException("Role must be USER or ADMIN");
            }
            if (isSelf && !role.equals("ADMIN")) {
                throw new IllegalArgumentException("You cannot remove your own administrator access");
            }
            if (!role.equals("ADMIN") && "ADMIN".equals(user.getRole()) && isLastAdmin(user)) {
                throw new IllegalArgumentException("This is the last administrator account");
            }
            user.setRole(role);
        }

        if (request.getEnabled() != null) {
            if (isSelf && !request.getEnabled()) {
                throw new IllegalArgumentException("You cannot suspend your own account");
            }
            if (!request.getEnabled() && "ADMIN".equals(user.getRole()) && isLastAdmin(user)) {
                throw new IllegalArgumentException("This is the last administrator account");
            }
            user.setEnabled(request.getEnabled());
        }

        return toUserResponse(userRepository.save(user), 0, null);
    }

    public void deleteUser(String actingUsername, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getUsername().equals(actingUsername)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        if ("ADMIN".equals(user.getRole()) && isLastAdmin(user)) {
            throw new IllegalArgumentException("This is the last administrator account");
        }

        // Attempts cascade from the user, taking their answers with them
        userRepository.delete(user);
    }

    private boolean isLastAdmin(User user) {
        return "ADMIN".equals(user.getRole()) && userRepository.countByRole("ADMIN") <= 1;
    }

    // --- paper upload ------------------------------------------------------

    /**
     * Imports a whole paper: creates (or reuses) the exam, creates the paper, then
     * writes every question and option. Runs in one transaction, so a malformed file
     * leaves nothing behind.
     */
    public ImportPaperResponse importPaper(ImportPaperRequest request) {
        Exam exam = resolveExam(request);

        List<String> warnings = new ArrayList<>();
        boolean replaced = false;

        Optional<Paper> existing = paperRepository.findByExamId(exam.getId()).stream()
                .filter(p -> p.getTitle().equalsIgnoreCase(request.getTitle().trim()))
                .findFirst();

        if (existing.isPresent()) {
            if (!request.isReplaceExisting()) {
                throw new IllegalArgumentException(
                        "A paper called \"" + existing.get().getTitle() + "\" already exists in this exam. "
                        + "Tick \"replace existing\" to overwrite its questions.");
            }
            paperService.deleteWithAttempts(existing.get());
            replaced = true;
            warnings.add("Replaced the previous version of this paper, including its questions and any recorded attempts.");
        }

        Paper paper = paperRepository.save(Paper.builder()
                .title(request.getTitle().trim())
                .description(StringUtils.hasText(request.getDescription())
                        ? request.getDescription().trim() : null)
                .year(request.getYear())
                .exam(exam)
                .build());

        int questionCount = 0;
        int optionCount = 0;
        int totalMarks = 0;

        for (int i = 0; i < request.getQuestions().size(); i++) {
            Map<String, Object> data = request.getQuestions().get(i);
            int humanIndex = i + 1;

            String stem = text(data.get("stem"));
            if (!StringUtils.hasText(stem)) {
                warnings.add("Question " + humanIndex + " has no stem and was skipped.");
                continue;
            }

            String type = normaliseType(text(data.get("type")));
            List<Map<String, Object>> optionData = options(data.get("options"));

            if (optionData.isEmpty()) {
                warnings.add("Question " + humanIndex + " has no options and was skipped.");
                continue;
            }

            Question question = new Question();
            question.setType(type);
            question.setStem(stem);
            question.setStemSi(text(data.get("stem_si")));
            question.setNote(text(data.get("note")));
            question.setNoteSi(text(data.get("note_si")));
            question.setImageUrl(text(data.get("image")));
            question.setExplanation(text(data.get("exp")));
            question.setExplanationSi(text(data.get("exp_si")));
            question.setPaper(paper);
            question.setOptions(new ArrayList<>());

            for (int j = 0; j < optionData.size(); j++) {
                Map<String, Object> opt = optionData.get(j);
                String label = text(opt.get("L"));
                if (!StringUtils.hasText(label)) {
                    label = String.valueOf((char) ('A' + j));
                }

                question.getOptions().add(Option.builder()
                        .label(label)
                        .text(Objects.requireNonNullElse(text(opt.get("text")), ""))
                        .textSi(text(opt.get("text_si")))
                        .imageUrl(text(opt.get("image")))
                        .correct(Boolean.TRUE.equals(opt.get("correct")))
                        .explanationEn(text(opt.get("exp")))
                        .explanationSi(text(opt.get("exp_si")))
                        .question(question)
                        .build());
            }

            if (!"tf".equals(type) && question.getOptions().stream().noneMatch(Option::getCorrect)) {
                warnings.add("Question " + humanIndex + " has no correct answer marked.");
            }

            questionRepository.save(question);
            questionCount++;
            optionCount += question.getOptions().size();
            totalMarks += "tf".equals(type) ? question.getOptions().size() : 1;
        }

        if (questionCount == 0) {
            throw new IllegalArgumentException("None of the questions in that file could be read.");
        }

        return ImportPaperResponse.builder()
                .subjectId(exam.getSubject() != null ? exam.getSubject().getId() : null)
                .subjectName(exam.getSubject() != null ? exam.getSubject().getName() : null)
                .examId(exam.getId())
                .examTitle(exam.getTitle())
                .paperId(paper.getId())
                .paperTitle(paper.getTitle())
                .questionsImported(questionCount)
                .optionsImported(optionCount)
                .totalMarks(totalMarks)
                .replacedExisting(replaced)
                .warnings(warnings)
                .build();
    }

    private Exam resolveExam(ImportPaperRequest request) {
        if (request.getExamId() != null) {
            Exam exam = examRepository.findById(request.getExamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam", "id", request.getExamId()));

            // An exam picked from the list may predate subjects; file it if one was chosen
            if (exam.getSubject() == null) {
                Subject subject = resolveSubject(request, false);
                if (subject != null) {
                    exam.setSubject(subject);
                    examRepository.save(exam);
                }
            }
            return exam;
        }

        if (!StringUtils.hasText(request.getNewExamTitle())) {
            throw new IllegalArgumentException("Choose an existing exam or give a name for a new one");
        }

        String title = request.getNewExamTitle().trim();
        Optional<Exam> existing = examRepository.findByTitle(title);
        if (existing.isPresent()) {
            return existing.get();
        }

        return examRepository.save(Exam.builder()
                .title(title)
                .description(StringUtils.hasText(request.getNewExamDescription())
                        ? request.getNewExamDescription().trim() : null)
                .subject(resolveSubject(request, true))
                .build());
    }

    /**
     * @param required when true, a new exam is being created and must land under a subject
     */
    private Subject resolveSubject(ImportPaperRequest request, boolean required) {
        if (request.getSubjectId() != null) {
            return subjectRepository.findById(request.getSubjectId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Subject", "id", request.getSubjectId()));
        }

        if (StringUtils.hasText(request.getNewSubjectName())) {
            String name = request.getNewSubjectName().trim();
            return subjectRepository.findByName(name)
                    .orElseGet(() -> subjectRepository.save(Subject.builder()
                            .name(name)
                            .description(StringUtils.hasText(request.getNewSubjectDescription())
                                    ? request.getNewSubjectDescription().trim() : null)
                            .build()));
        }

        if (required) {
            throw new IllegalArgumentException("Choose a subject for the new exam, or name a new one");
        }
        return null;
    }

    /** Anything we do not recognise is treated as a single-best-answer question. */
    private String normaliseType(String raw) {
        if (raw == null) return "single";
        String type = raw.trim().toLowerCase();
        return switch (type) {
            case "tf", "true_false", "truefalse" -> "tf";
            case "multi", "multiple" -> "multi";
            default -> "single";
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> options(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(o -> (Map<String, Object>) o)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    // --- mapping -----------------------------------------------------------

    private List<AdminUserResponse> toUserResponses(List<User> users) {
        if (users.isEmpty()) return List.of();

        Set<Long> ids = users.stream().map(User::getId).collect(Collectors.toSet());

        // One aggregate query for the whole page rather than a count per row
        Map<Long, long[]> counts = new HashMap<>();
        Map<Long, Double> best = new HashMap<>();
        for (Object[] row : quizAttemptRepository.statsForUsers(ids)) {
            Long userId = (Long) row[0];
            counts.put(userId, new long[] { (Long) row[1] });
            best.put(userId, row[2] == null ? null : round1((Double) row[2]));
        }

        return users.stream()
                .map(u -> toUserResponse(
                        u,
                        counts.containsKey(u.getId()) ? counts.get(u.getId())[0] : 0,
                        best.get(u.getId())))
                .collect(Collectors.toList());
    }

    private AdminUserResponse toUserResponse(User user, long attemptCount, Double bestScore) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .provider(user.getProvider())
                .enabled(!Boolean.FALSE.equals(user.getEnabled()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .attemptCount(attemptCount)
                .bestScorePct(bestScore)
                .build();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
