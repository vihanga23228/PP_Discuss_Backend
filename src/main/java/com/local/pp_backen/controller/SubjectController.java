package com.local.pp_backen.controller;

import com.local.pp_backen.dto.exam.ExamResponse;
import com.local.pp_backen.dto.subject.SubjectRequest;
import com.local.pp_backen.dto.subject.SubjectResponse;
import com.local.pp_backen.service.ExamService;
import com.local.pp_backen.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Step one of the picker: choose a subject. */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final ExamService examService;

    public SubjectController(SubjectService subjectService, ExamService examService) {
        this.subjectService = subjectService;
        this.examService = examService;
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> getAllSubjects() {
        return ResponseEntity.ok(subjectService.getAllSubjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getSubjectById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getSubjectById(id));
    }

    /** Step two: the exams inside this subject. */
    @GetMapping("/{id}/exams")
    public ResponseEntity<List<ExamResponse>> getExams(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExamsBySubject(id));
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubject(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> updateSubject(@PathVariable Long id,
                                                         @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.updateSubject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
