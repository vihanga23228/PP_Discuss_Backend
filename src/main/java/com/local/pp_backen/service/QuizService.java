package com.local.pp_backen.service;

import com.local.pp_backen.dto.question.OptionResponse;
import com.local.pp_backen.dto.quiz.*;
import com.local.pp_backen.entity.*;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuizService {

    private final QuestionRepository questionRepository;
    private final PaperRepository paperRepository;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public QuizService(QuestionRepository questionRepository,
                       PaperRepository paperRepository,
                       UserRepository userRepository,
                       QuizAttemptRepository quizAttemptRepository) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.userRepository = userRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    public QuizResponse startQuiz(String username, StartQuizRequest request) {
        User user = requireUser(username);

        Paper paper = paperRepository.findById(request.getPaperId())
                .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", request.getPaperId()));

        List<Question> questions;
        if (request.getNumberOfQuestions() != null && request.getNumberOfQuestions() > 0) {
            questions = questionRepository.findRandomByPaperId(paper.getId(), request.getNumberOfQuestions());
        } else {
            questions = questionRepository.findByPaperId(paper.getId());
        }

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No questions found for this paper");
        }

        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.builder()
                .user(user)
                .paper(paper)
                .totalQuestions(questions.size())
                .totalMarks(questions.stream().mapToInt(QuizService::marksFor).sum())
                .build());

        // Questions go out without correct flags or explanations
        List<QuizQuestionResponse> questionResponses = questions.stream()
                .map(q -> QuizQuestionResponse.builder()
                        .questionId(q.getId())
                        .type(q.getType())
                        .stem(q.getStem())
                        .stemSi(q.getStemSi())
                        .note(q.getNote())
                        .noteSi(q.getNoteSi())
                        .imageUrl(q.getImageUrl())
                        .options(q.getOptions().stream()
                                .map(opt -> OptionResponse.builder()
                                        .id(opt.getId())
                                        .label(opt.getLabel())
                                        .text(opt.getText())
                                        .textSi(opt.getTextSi())
                                        .imageUrl(opt.getImageUrl())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return QuizResponse.builder()
                .attemptId(attempt.getId())
                .paperId(paper.getId())
                .paperTitle(paper.getTitle())
                .totalQuestions(questions.size())
                .questions(questionResponses)
                .build();
    }

    public QuizResultResponse submitQuiz(String username, Long attemptId, SubmitQuizRequest request) {
        User user = requireUser(username);
        QuizAttempt attempt = requireOwnedAttempt(user, attemptId);

        if (attempt.getCompletedAt() != null) {
            throw new IllegalArgumentException("This quiz has already been submitted");
        }

        int fullyCorrect = 0;
        int earnedMarks = 0;
        int submittedMarks = 0;
        List<QuizResultResponse.QuestionResult> questionResults = new ArrayList<>();

        for (AnswerSubmission submission : request.getAnswers()) {
            Question question = questionRepository.findById(submission.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Question", "id", submission.getQuestionId()));

            Scored scored = score(question, submission);

            earnedMarks += scored.earned();
            submittedMarks += scored.available();
            if (scored.fullyCorrect()) {
                fullyCorrect++;
            }

            attempt.getAnswers().add(QuizAnswer.builder()
                    .attempt(attempt)
                    .question(question)
                    .isCorrect(scored.fullyCorrect())
                    .marksEarned(scored.earned())
                    .marksAvailable(scored.available())
                    .selectedOptions(question.getOptions().stream()
                            .filter(o -> scored.affirmative().contains(o.getId()))
                            .collect(Collectors.toCollection(HashSet::new)))
                    .build());

            questionResults.add(QuizResultResponse.QuestionResult.builder()
                    .questionId(question.getId())
                    .stem(question.getStem())
                    .stemSi(question.getStemSi())
                    .imageUrl(question.getImageUrl())
                    .type(question.getType())
                    .correct(scored.fullyCorrect())
                    .marksEarned(scored.earned())
                    .marksAvailable(scored.available())
                    .explanation(question.getExplanation())
                    .explanationSi(question.getExplanationSi())
                    .options(optionResults(question, scored.affirmative()))
                    .selectedOptionIds(new ArrayList<>(scored.affirmative()))
                    .build());
        }

        // Questions the user never submitted still count against the paper total
        int paperMarks = attempt.getTotalMarks() != null && attempt.getTotalMarks() > 0
                ? attempt.getTotalMarks()
                : submittedMarks;
        double scorePct = paperMarks > 0 ? (double) earnedMarks / paperMarks * 100.0 : 0.0;

        attempt.setCorrectCount(fullyCorrect);
        attempt.setEarnedMarks(earnedMarks);
        attempt.setTotalMarks(paperMarks);
        attempt.setScorePct(scorePct);
        attempt.setCompletedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);

        return QuizResultResponse.builder()
                .attemptId(attempt.getId())
                .paperId(attempt.getPaper().getId())
                .paperTitle(attempt.getPaper().getTitle())
                .totalQuestions(orZero(attempt.getTotalQuestions()))
                .correctCount(fullyCorrect)
                .totalMarks(paperMarks)
                .earnedMarks(earnedMarks)
                .scorePct(scorePct)
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .questionResults(questionResults)
                .build();
    }

    @Transactional(readOnly = true)
    public List<QuizResultResponse> getUserAttempts(String username) {
        User user = requireUser(username);
        return quizAttemptRepository.findByUserIdOrderByStartedAtDesc(user.getId()).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getAttemptDetail(String username, Long attemptId) {
        User user = requireUser(username);
        QuizAttempt attempt = requireOwnedAttempt(user, attemptId);

        List<QuizResultResponse.QuestionResult> questionResults = attempt.getAnswers().stream()
                .map(answer -> {
                    Question question = answer.getQuestion();
                    Set<Long> affirmative = answer.getSelectedOptions().stream()
                            .map(Option::getId)
                            .collect(Collectors.toSet());

                    return QuizResultResponse.QuestionResult.builder()
                            .questionId(question.getId())
                            .stem(question.getStem())
                            .stemSi(question.getStemSi())
                            .imageUrl(question.getImageUrl())
                    .imageUrl(question.getImageUrl())
                    .stemSi(question.getStemSi())
                    .imageUrl(question.getImageUrl())
                            .type(question.getType())
                            .correct(Boolean.TRUE.equals(answer.getIsCorrect()))
                            .marksEarned(orZero(answer.getMarksEarned()))
                            .marksAvailable(answer.getMarksAvailable() != null
                                    ? answer.getMarksAvailable() : marksFor(question))
                            .explanation(question.getExplanation())
                            .explanationSi(question.getExplanationSi())
                            .options(optionResults(question, affirmative))
                            .selectedOptionIds(new ArrayList<>(affirmative))
                            .build();
                })
                .collect(Collectors.toList());

        QuizResultResponse summary = toSummary(attempt);
        summary.setQuestionResults(questionResults);
        return summary;
    }

    // --- scoring -----------------------------------------------------------

    /** The outcome of marking one submitted answer. */
    private record Scored(int earned, int available, boolean fullyCorrect, Set<Long> affirmative) {
    }

    /**
     * True/false questions are marked statement by statement, exactly as the printed paper is:
     * five statements, five marks. Single and multi answer questions are all-or-nothing.
     */
    private Scored score(Question question, AnswerSubmission submission) {
        if ("tf".equalsIgnoreCase(question.getType())) {
            Map<Long, Boolean> answers = submission.optionAnswersOrEmpty();
            Set<Long> markedTrue = new LinkedHashSet<>();
            int earned = 0;

            for (Option option : question.getOptions()) {
                Boolean answer = answers.get(option.getId());
                if (Boolean.TRUE.equals(answer)) {
                    markedTrue.add(option.getId());
                }
                // A missing entry means the statement was left blank, and scores nothing
                if (answer != null && answer.equals(Boolean.TRUE.equals(option.getCorrect()))) {
                    earned++;
                }
            }

            int available = Math.max(question.getOptions().size(), 1);
            return new Scored(earned, available, earned == available, markedTrue);
        }

        Set<Long> selected = new LinkedHashSet<>(submission.selectedOptionIdsOrEmpty());
        Set<Long> correctIds = question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getCorrect()))
                .map(Option::getId)
                .collect(Collectors.toSet());

        boolean right = !selected.isEmpty() && correctIds.equals(selected);
        return new Scored(right ? 1 : 0, 1, right, selected);
    }

    /** Marks a question is worth: one per statement for true/false, otherwise one. */
    private static int marksFor(Question question) {
        return "tf".equalsIgnoreCase(question.getType())
                ? Math.max(question.getOptions().size(), 1)
                : 1;
    }

    // --- helpers -----------------------------------------------------------

    private List<QuizResultResponse.OptionResult> optionResults(Question question, Set<Long> affirmative) {
        return question.getOptions().stream()
                .map(opt -> QuizResultResponse.OptionResult.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .text(opt.getText())
                        .textSi(opt.getTextSi())
                        .imageUrl(opt.getImageUrl())
                        .correct(Boolean.TRUE.equals(opt.getCorrect()))
                        .selected(affirmative.contains(opt.getId()))
                        .explanationEn(opt.getExplanationEn())
                        .explanationSi(opt.getExplanationSi())
                        .build())
                .collect(Collectors.toList());
    }

    private QuizResultResponse toSummary(QuizAttempt attempt) {
        return QuizResultResponse.builder()
                .attemptId(attempt.getId())
                .paperId(attempt.getPaper().getId())
                .paperTitle(attempt.getPaper().getTitle())
                .totalQuestions(orZero(attempt.getTotalQuestions()))
                .correctCount(orZero(attempt.getCorrectCount()))
                .totalMarks(orZero(attempt.getTotalMarks()))
                .earnedMarks(orZero(attempt.getEarnedMarks()))
                .scorePct(attempt.getScorePct() != null ? attempt.getScorePct() : 0.0)
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .build();
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private QuizAttempt requireOwnedAttempt(User user, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", "id", attemptId));
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("This quiz attempt does not belong to you");
        }
        return attempt;
    }
}
