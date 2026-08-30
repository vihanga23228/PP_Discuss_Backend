package com.local.pp_backen.service;

import com.local.pp_backen.dto.paper.PaperRequest;
import com.local.pp_backen.dto.paper.PaperResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.QuizAttempt;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.QuestionRepository;
import com.local.pp_backen.repository.QuestionRepository.PaperQuestionCount;
import com.local.pp_backen.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaperService {

    private final PaperRepository paperRepository;
    private final ExamRepository examRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuestionRepository questionRepository;

    public PaperService(PaperRepository paperRepository,
                        ExamRepository examRepository,
                        QuizAttemptRepository quizAttemptRepository,
                        QuestionRepository questionRepository) {
        this.paperRepository = paperRepository;
        this.examRepository = examRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Listing a paper used to run one {@code countByPaperId} round trip per paper — an N+1
     * that got expensive once the DB moved off localhost. {@code countsPerPaperForExam} gets
     * every paper's question count for the whole exam in one aggregate query instead.
     */
    public List<PaperResponse> getPapersByExam(Long examId) {
        if (!examRepository.existsById(examId)) {
            throw new ResourceNotFoundException("Exam", "id", examId);
        }
        List<Paper> papers = paperRepository.findByExamId(examId);
        Map<Long, Long> counts = questionRepository.countsPerPaperForExam(examId).stream()
                .collect(Collectors.toMap(PaperQuestionCount::getPaperId, PaperQuestionCount::getQuestionCount));

        return papers.stream()
                .map(paper -> toResponse(paper, counts.getOrDefault(paper.getId(), 0L)))
                .collect(Collectors.toList());
    }

    public PaperResponse getPaperById(Long examId, Long paperId) {
        Paper paper = paperRepository.findByIdAndExamId(paperId, examId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", paperId));
        return toResponse(paper);
    }

    public PaperResponse createPaper(Long examId, PaperRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", "id", examId));

        Paper paper = Paper.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .year(request.getYear())
                .durationMinutes(request.getDurationMinutes())
                .exam(exam)
                .build();
        paper = paperRepository.save(paper);
        return toResponse(paper);
    }

    public PaperResponse updatePaper(Long examId, Long paperId, PaperRequest request) {
        Paper paper = paperRepository.findByIdAndExamId(paperId, examId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", paperId));
        paper.setTitle(request.getTitle());
        paper.setDescription(request.getDescription());
        paper.setYear(request.getYear());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper = paperRepository.save(paper);
        return toResponse(paper);
    }

    public void deletePaper(Long examId, Long paperId) {
        Paper paper = paperRepository.findByIdAndExamId(paperId, examId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", paperId));
        deleteWithAttempts(paper);
    }

    /**
     * Removes a paper together with every attempt recorded against it.
     *
     * <p>Attempts have to go first: {@code quiz_attempts.paper_id} and
     * {@code quiz_answers.question_id} both point at rows the paper owns, so deleting the
     * paper on its own trips a foreign key constraint once anyone has sat it.
     */
    public void deleteWithAttempts(Paper paper) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByPaperId(paper.getId());
        if (!attempts.isEmpty()) {
            quizAttemptRepository.deleteAll(attempts);
            quizAttemptRepository.flush();
        }
        paperRepository.delete(paper);
        paperRepository.flush();
    }

    /** Single-paper lookups: one extra round trip for this paper's own count is cheap enough. */
    private PaperResponse toResponse(Paper paper) {
        return toResponse(paper, questionRepository.countByPaperId(paper.getId()));
    }

    private PaperResponse toResponse(Paper paper, long questionCount) {
        return PaperResponse.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .description(paper.getDescription())
                .year(paper.getYear())
                .durationMinutes(paper.getDurationMinutes())
                .examId(paper.getExam().getId())
                .examTitle(paper.getExam().getTitle())
                .subjectId(paper.getExam().getSubject() != null
                        ? paper.getExam().getSubject().getId() : null)
                .subjectName(paper.getExam().getSubject() != null
                        ? paper.getExam().getSubject().getName() : null)
                .createdAt(paper.getCreatedAt())
                .questionCount((int) questionCount)
                .build();
    }
}
