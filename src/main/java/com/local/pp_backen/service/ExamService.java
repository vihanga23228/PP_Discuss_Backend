package com.local.pp_backen.service;

import com.local.pp_backen.dto.exam.ExamRequest;
import com.local.pp_backen.dto.exam.ExamResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.Subject;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.ExamRepository.ExamCounts;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    /**
     * Listing exams used to hydrate every paper (and every question on it) per exam just to
     * count sizes — an N+1 that got very expensive once the DB moved off localhost.
     * {@code countsPerExam()} gets every exam's counts in one aggregate query instead.
     */
    @Transactional(readOnly = true)
    public List<ExamResponse> getAllExams() {
        List<Exam> exams = examRepository.findAll();
        Map<Long, ExamCounts> counts = examRepository.countsPerExam().stream()
                .collect(Collectors.toMap(ExamCounts::getExamId, Function.identity()));

        return exams.stream()
                .sorted(Comparator.comparing(Exam::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(exam -> toResponse(exam, counts.get(exam.getId())))
                .collect(Collectors.toList());
    }

    /** The exams inside one subject — the second step of the picker. */
    @Transactional(readOnly = true)
    public List<ExamResponse> getExamsBySubject(Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new ResourceNotFoundException("Subject", "id", subjectId);
        }
        List<Exam> exams = examRepository.findBySubjectId(subjectId);
        Map<Long, ExamCounts> counts = examRepository.countsPerExam().stream()
                .collect(Collectors.toMap(ExamCounts::getExamId, Function.identity()));

        return exams.stream()
                .sorted(Comparator.comparing(Exam::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(exam -> toResponse(exam, counts.get(exam.getId())))
                .collect(Collectors.toList());
    }

    /** Exams that predate subjects, so the admin can file them somewhere. */
    @Transactional(readOnly = true)
    public List<ExamResponse> getUnassignedExams() {
        List<Exam> exams = examRepository.findBySubjectIsNull();
        Map<Long, ExamCounts> counts = examRepository.countsPerExam().stream()
                .collect(Collectors.toMap(ExamCounts::getExamId, Function.identity()));

        return exams.stream()
                .map(exam -> toResponse(exam, counts.get(exam.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getExamById(Long id) {
        Exam exam = require(id);
        return toResponse(exam, examRepository.countsForExam(id));
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

        // A brand-new exam has nothing under it yet — no need to round-trip for a count of zero.
        return toResponse(examRepository.save(exam), null);
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

        Exam saved = examRepository.save(exam);
        return toResponse(saved, examRepository.countsForExam(id));
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

    /** {@code counts} is null for an exam known to have nothing under it yet (just created). */
    private ExamResponse toResponse(Exam exam, ExamCounts counts) {
        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .subjectId(exam.getSubject() != null ? exam.getSubject().getId() : null)
                .subjectName(exam.getSubject() != null ? exam.getSubject().getName() : null)
                .createdAt(exam.getCreatedAt())
                .paperCount(counts != null ? counts.getPaperCount().intValue() : 0)
                .questionCount(counts != null ? counts.getQuestionCount().intValue() : 0)
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
