package com.local.pp_backen.service;

import com.local.pp_backen.dto.subject.SubjectRequest;
import com.local.pp_backen.dto.subject.SubjectResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Subject;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.SubjectRepository;
import com.local.pp_backen.repository.SubjectRepository.SubjectCounts;
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
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ExamRepository examRepository;
    private final ExamService examService;

    public SubjectService(SubjectRepository subjectRepository,
                          ExamRepository examRepository,
                          ExamService examService) {
        this.subjectRepository = subjectRepository;
        this.examRepository = examRepository;
        this.examService = examService;
    }

    /**
     * Listing subjects used to trigger a full exam -> paper -> question hydration per subject
     * just to count sizes (an N+1 that got very expensive once the DB moved off localhost).
     * {@code countsPerSubject()} gets every subject's counts in one aggregate query instead.
     */
    @Transactional(readOnly = true)
    public List<SubjectResponse> getAllSubjects() {
        List<Subject> subjects = subjectRepository.findAll();
        Map<Long, SubjectCounts> counts = subjectRepository.countsPerSubject().stream()
                .collect(Collectors.toMap(SubjectCounts::getSubjectId, Function.identity()));

        return subjects.stream()
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(subject -> toResponse(subject, counts.get(subject.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(Long id) {
        Subject subject = require(id);
        return toResponse(subject, subjectRepository.countsForSubject(id));
    }

    public SubjectResponse createSubject(SubjectRequest request) {
        String name = request.getName().trim();
        if (Boolean.TRUE.equals(subjectRepository.existsByName(name))) {
            throw new IllegalArgumentException("A subject called \"" + name + "\" already exists");
        }

        Subject subject = Subject.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .build();

        // A brand-new subject has nothing under it yet — no need to round-trip for a count of zero.
        return toResponse(subjectRepository.save(subject), null);
    }

    /** Renaming is the common case here, so a clash is reported against the other subject. */
    public SubjectResponse updateSubject(Long id, SubjectRequest request) {
        Subject subject = require(id);
        String name = request.getName().trim();

        if (Boolean.TRUE.equals(subjectRepository.existsByNameIgnoreCaseAndIdNot(name, id))) {
            throw new IllegalArgumentException("Another subject is already called \"" + name + "\"");
        }

        subject.setName(name);
        subject.setDescription(trimToNull(request.getDescription()));
        Subject saved = subjectRepository.save(subject);
        return toResponse(saved, subjectRepository.countsForSubject(id));
    }

    /**
     * Deletes a subject and everything beneath it: its exams, their papers, the questions
     * on those papers, and any attempts recorded against them.
     */
    public void deleteSubject(Long id) {
        Subject subject = require(id);
        for (Exam exam : examRepository.findBySubjectId(id)) {
            examService.deleteExam(exam.getId());
        }
        subject.getExams().clear();
        subjectRepository.delete(subject);
    }

    private Subject require(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
    }

    /** {@code counts} is null for a subject known to have nothing under it yet (just created). */
    SubjectResponse toResponse(Subject subject, SubjectCounts counts) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .description(subject.getDescription())
                .createdAt(subject.getCreatedAt())
                .examCount(counts != null ? counts.getExamCount().intValue() : 0)
                .paperCount(counts != null ? counts.getPaperCount().intValue() : 0)
                .questionCount(counts != null ? counts.getQuestionCount().intValue() : 0)
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
