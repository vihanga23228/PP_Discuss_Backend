package com.local.pp_backen.service;

import com.local.pp_backen.dto.subject.SubjectRequest;
import com.local.pp_backen.dto.subject.SubjectResponse;
import com.local.pp_backen.entity.Exam;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.Subject;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.ExamRepository;
import com.local.pp_backen.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAll().stream()
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(Long id) {
        return toResponse(require(id));
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

        return toResponse(subjectRepository.save(subject));
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
        return toResponse(subjectRepository.save(subject));
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

    SubjectResponse toResponse(Subject subject) {
        List<Exam> exams = examRepository.findBySubjectId(subject.getId());

        int papers = 0;
        int questions = 0;
        for (Exam exam : exams) {
            if (exam.getPapers() == null) continue;
            papers += exam.getPapers().size();
            for (Paper paper : exam.getPapers()) {
                questions += paper.getQuestions() != null ? paper.getQuestions().size() : 0;
            }
        }

        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .description(subject.getDescription())
                .createdAt(subject.getCreatedAt())
                .examCount(exams.size())
                .paperCount(papers)
                .questionCount(questions)
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
