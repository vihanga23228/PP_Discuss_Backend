package com.local.pp_backen.service;

import com.local.pp_backen.dto.paper.PaperRequest;
import com.local.pp_backen.dto.paper.PaperResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.QuizAttempt;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaperService {

    private final PaperRepository paperRepository;
    private final ExamRepository examRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public PaperService(PaperRepository paperRepository,
                        ExamRepository examRepository,
                        QuizAttemptRepository quizAttemptRepository) {
        this.paperRepository = paperRepository;
        this.examRepository = examRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    public List<PaperResponse> getPapersByExam(Long examId) {
        if (!examRepository.existsById(examId)) {
            throw new ResourceNotFoundException("Exam", "id", examId);
        }
        return paperRepository.findByExamId(examId).stream()
                .map(this::toResponse)
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

    private PaperResponse toResponse(Paper paper) {
        return PaperResponse.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .description(paper.getDescription())
                .year(paper.getYear())
                .examId(paper.getExam().getId())
                .examTitle(paper.getExam().getTitle())
                .subjectId(paper.getExam().getSubject() != null
                        ? paper.getExam().getSubject().getId() : null)
                .subjectName(paper.getExam().getSubject() != null
                        ? paper.getExam().getSubject().getName() : null)
                .createdAt(paper.getCreatedAt())
                .questionCount(paper.getQuestions() != null ? paper.getQuestions().size() : 0)
                .build();
    }
}
