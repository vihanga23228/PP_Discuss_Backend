package com.local.pp_backen.service;

import com.local.pp_backen.dto.exam.ExamRequest;
import com.local.pp_backen.dto.exam.ExamResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.Subject;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExamService {

    private final ExamRepository examRepository;
    private final PaperRepository paperRepository;
    private final SubjectRepository subjectRepository;
    private final PaperService paperService;

    public ExamService(ExamRepository examRepository,
                       PaperRepository paperRepository,
                       SubjectRepository subjectRepository,
                       PaperService paperService) {
        this.examRepository = examRepository;
        this.paperRepository = paperRepository;
        this.subjectRepository = subjectRepository;
        this.paperService = paperService;
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getAllExams() {
        return examRepository.findAll().stream()
                .sorted(Comparator.comparing(Exam::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** The exams inside one subject — the second step of the picker. */
    @Transactional(readOnly = true)
    public List<ExamResponse> getExamsBySubject(Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new ResourceNotFoundException("Subject", "id", subjectId);
        }
        return examRepository.findBySubjectId(subjectId).stream()
                .sorted(Comparator.comparing(Exam::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Exams that predate subjects, so the admin can file them somewhere. */
    @Transactional(readOnly = true)
    public List<ExamResponse> getUnassignedExams() {
        return examRepository.findBySubjectIsNull().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getExamById(Long id) {
        return toResponse(require(id));
    }

    public ExamResponse createExam(ExamRequest request) {
        String title = request.getTitle().trim();
        if (Boolean.TRUE.equals(examRepository.existsByTitle(title))) {
            throw new IllegalArgumentException("An exam called \"" + title + "\" already exists");
        }
        if (request.getSubjectId() == null) {
            throw new IllegalArgumentException("Choose a subject for this exam");
        }

        Exam exam = Exam.builder()
                .title(title)
                .description(trimToNull(request.getDescription()))
                .subject(requireSubject(request.getSubjectId()))
                .build();

        return toResponse(examRepository.save(exam));
    }

    /** Renames the exam, and can move it to a different subject. */
    public ExamResponse updateExam(Long id, ExamRequest request) {
        Exam exam = require(id);
        String title = request.getTitle().trim();

        if (Boolean.TRUE.equals(examRepository.existsByTitleIgnoreCaseAndIdNot(title, id))) {
            throw new IllegalArgumentException("Another exam is already called \"" + title + "\"");
        }

        exam.setTitle(title);
        exam.setDescription(trimToNull(request.getDescription()));
        if (request.getSubjectId() != null) {
            exam.setSubject(requireSubject(request.getSubjectId()));
        }

        return toResponse(examRepository.save(exam));
    }

    public void deleteExam(Long id) {
        Exam exam = require(id);

        // Remove each paper (and the attempts recorded on it) first, otherwise the
        // attempt and answer foreign keys block the delete. Papers are read back from
        // the repository rather than the exam's collection, and the collection is then
        // emptied, so the cascade does not try to re-save children we just removed.
        for (Paper paper : paperRepository.findByExamId(id)) {
            paperService.deleteWithAttempts(paper);
        }
        exam.getPapers().clear();

        examRepository.delete(exam);
    }

    private Exam require(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", "id", id));
    }

    private Subject requireSubject(Long subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", subjectId));
    }

    private ExamResponse toResponse(Exam exam) {
        List<Paper> papers = exam.getPapers() != null ? exam.getPapers() : List.of();
        int questions = papers.stream()
                .mapToInt(p -> p.getQuestions() != null ? p.getQuestions().size() : 0)
                .sum();

        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .subjectId(exam.getSubject() != null ? exam.getSubject().getId() : null)
                .subjectName(exam.getSubject() != null ? exam.getSubject().getName() : null)
                .createdAt(exam.getCreatedAt())
                .paperCount(papers.size())
                .questionCount(questions)
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
